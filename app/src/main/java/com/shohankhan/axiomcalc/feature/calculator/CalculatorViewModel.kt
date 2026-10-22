package com.shohankhan.axiomcalc.feature.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.formatting.DisplayPolicy
import com.shohankhan.axiomcalc.core.formatting.NumberFormatter
import com.shohankhan.axiomcalc.core.formatting.NumberNotation
import com.shohankhan.axiomcalc.domain.calculator.CalculatorBuffer
import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.SavedCalculation
import com.shohankhan.axiomcalc.domain.repository.HistoryRepository
import com.shohankhan.axiomcalc.domain.repository.SavedCalculationRepository
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable view state of a calculator screen. */
data class CalculatorUiState(
    val expression: String = "",
    val result: String = "0",
    /** Live preview while typing; null when the expression is incomplete. */
    val preview: String? = null,
    /** Typed error, surfaced as an actionable message — never a raw exception. */
    val error: CalcError? = null,
    val memory: String? = null,
    val angleMode: AngleMode = AngleMode.DEGREES,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasExpression: Boolean = false,
    /** True right after `=` so the display can emphasize the committed result. */
    val evaluated: Boolean = false,
    val settings: AppSettings = AppSettings(),
)

/** One-shot UI events that are not part of persistent state. */
sealed interface CalculatorEvent {
    data class Haptic(val strong: Boolean) : CalculatorEvent
    data class Message(val text: String) : CalculatorEvent
    /** Asks the screen to open the "save calculation" sheet. */
    data object RequestSaveName : CalculatorEvent
}

/**
 * Shared ViewModel for the Standard and Scientific calculators.
 *
 * All editing goes through [CalculatorBuffer], so both screens share identical
 * token semantics, undo/redo, memory and percentage behaviour — the difference
 * between them is only which keys are exposed. Results are formatted by
 * [NumberFormatter] from the persisted [DisplayPolicy]; a formatted string is
 * never fed back into the engine.
 */
open class CalculatorViewModel(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val savedRepository: SavedCalculationRepository,
    private val origin: CalcOrigin,
) : ViewModel() {

    /**
     * The clipboard service, resolved once at construction.
     *
     * A ViewModel deliberately outlives the Activity that created it, so it must
     * not retain a [Context] field - Android lint reports that as
     * StaticFieldLeak and it is a real leak whenever an Activity context is
     * passed. Only the application context ever reaches this constructor (see
     * `axiomViewModel`), and from it the one system service this class needs is
     * taken immediately and the context itself is dropped.
     */
    private val clipboard: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val buffer = CalculatorBuffer()
    private var ans: CalcNumber = CalcNumber.ZERO

    private val _state = MutableStateFlow(CalculatorUiState())
    val state: StateFlow<CalculatorUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CalculatorEvent>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CalculatorEvent> = _events.asSharedFlow()

    /** Result of the last committed evaluation, reused by "save" and "ans". */
    private var lastResultValue: CalcNumber? = null
    private var lastResultDisplay: String = ""
    private var lastExpression: String = ""

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings, angleMode = parseAngle(settings)) }
                refresh()
            }
        }
    }

    private fun parseAngle(settings: AppSettings): AngleMode =
        runCatching { AngleMode.valueOf(settings.angleModeName) }.getOrDefault(AngleMode.DEGREES)

    private fun displayPolicy(settings: AppSettings): DisplayPolicy = DisplayPolicy(
        autoPrecision = settings.autoPrecision,
        decimals = settings.decimalPlaces,
        grouping = settings.grouping,
        notation = runCatching { NumberNotation.valueOf(settings.notationName) }
            .getOrDefault(NumberNotation.DECIMAL),
    )

    /** Recomputes the display from the buffer without mutating it. */
    private fun refresh() {
        val settings = _state.value.settings
        val policy = displayPolicy(settings)
        val angle = parseAngle(settings)
        val previewResult = buffer.preview(angle, ans)
        val previewText = previewResult?.valueOrNull()?.let { NumberFormatter.format(it, policy) }
        val error = previewResult?.errorOrNull()?.takeIf { it !is CalcError.Incomplete }

        val evaluated = _state.value.evaluated && lastResultValue != null

        _state.update {
            it.copy(
                expression = buffer.expressionText(),
                // While typing the main line shows the live preview; after `=`
                // it shows the committed result and the preview line goes quiet.
                result = if (evaluated) lastResultDisplay else (previewText ?: "0"),
                preview = if (evaluated) null else previewText?.takeIf { !buffer.isEmpty },
                error = if (evaluated) null else error,
                memory = buffer.memoryRecall()?.let { m -> NumberFormatter.format(m, policy) },
                angleMode = angle,
                canUndo = buffer.canUndo(),
                canRedo = buffer.canRedo(),
                hasExpression = !buffer.isEmpty,
                evaluated = evaluated,
            )
        }
    }

    /**
     * Applies [block] to the buffer, then refreshes the display.
     *
     * The lambda is the LAST parameter on purpose: Kotlin binds a trailing
     * lambda to the final parameter only, so `mutate { ... }` would otherwise
     * be matched against `hapticStrong` and fail to compile.
     */
    private fun mutate(hapticStrong: Boolean = false, block: CalculatorBuffer.() -> Unit) {
        buffer.block()
        if (_state.value.evaluated) _state.update { it.copy(evaluated = false) }
        refresh()
        emitHaptic(hapticStrong)
    }

    private fun emitHaptic(strong: Boolean) {
        if (_state.value.settings.hapticsEnabled) _events.tryEmit(CalculatorEvent.Haptic(strong))
    }

    // ---- keys -----------------------------------------------------------------------

    fun onDigit(digit: Char) = mutate { pressDigit(digit) }
    fun onDecimalPoint() = mutate { pressDecimalPoint() }
    fun onOperator(symbol: String) = mutate { pressOperator(symbol) }
    fun onFunction(text: String) = mutate { pressFunction(text) }
    fun onConstant(text: String) = mutate { pressConstant(text) }
    fun onOpenParen() = mutate { pressOpenParen() }
    fun onCloseParen() = mutate { pressCloseParen() }
    fun onPercentKey() = mutate { pressPercent() }
    fun onFactorial() = mutate { pressFactorial() }
    fun onSignToggle() = mutate { pressSignToggle() }
    fun onBackspace() = mutate { pressBackspace() }
    fun onClearEntry() = mutate { pressClearEntry() }
    fun onAns() = mutate { pressAns() }

    fun onAllClear() {
        buffer.pressAllClear()
        lastResultValue = null
        lastResultDisplay = ""
        lastExpression = ""
        _state.update { it.copy(evaluated = false, result = "0", preview = null, error = null) }
        refresh()
        emitHaptic(strong = true)
    }

    fun onUndo() {
        if (buffer.undo()) {
            _state.update { it.copy(evaluated = false) }
            refresh()
            emitHaptic(strong = false)
        }
    }

    fun onRedo() {
        if (buffer.redo()) {
            _state.update { it.copy(evaluated = false) }
            refresh()
            emitHaptic(strong = false)
        }
    }

    /**
     * `=`. On success the result becomes the display, `ans` and the history row;
     * on failure the typed error is shown and nothing is committed — no silent
     * zero, no partial write.
     */
    fun onEquals() {
        val settings = _state.value.settings
        val angle = parseAngle(settings)
        val policy = displayPolicy(settings)
        when (val result = buffer.evaluate(angle, ans)) {
            is CalcResult.Failure -> {
                _state.update {
                    it.copy(
                        error = result.error,
                        preview = null,
                        evaluated = false,
                    )
                }
                emitHaptic(strong = true)
            }

            is CalcResult.Success -> {
                val display = NumberFormatter.format(result.value, policy)
                val expression = buffer.expressionText() + buffer.closingSuffix()
                ans = result.value
                lastResultValue = result.value
                lastResultDisplay = display
                lastExpression = expression
                buffer.pressAllClear()
                _state.update {
                    it.copy(
                        expression = expression,
                        result = display,
                        preview = null,
                        error = null,
                        evaluated = true,
                        hasExpression = false,
                        canUndo = buffer.canUndo(),
                        canRedo = buffer.canRedo(),
                    )
                }
                emitHaptic(strong = true)
                recordHistory(expression, result.value.toCanonicalString(), display)
            }
        }
    }

    private fun recordHistory(expression: String, value: String, display: String) {
        viewModelScope.launch {
            runCatching {
                historyRepository.add(
                    HistoryEntry(
                        id = 0,
                        expression = expression,
                        resultValue = value,
                        resultDisplay = display,
                        origin = origin,
                        timestampMillis = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    // ---- single dispatch point for every key -----------------------------------------

    /**
     * Maps a keypad key to exactly one buffer or app action.
     *
     * Keeping this in the ViewModel (not in the composables) means the mapping is
     * unit-testable without a Compose runtime, and both calculators share it, so
     * a key can never behave differently on two screens.
     */
    fun onKey(key: CalcKey) {
        when (key) {
            CalcKey.D0 -> onDigit('0')
            CalcKey.D1 -> onDigit('1')
            CalcKey.D2 -> onDigit('2')
            CalcKey.D3 -> onDigit('3')
            CalcKey.D4 -> onDigit('4')
            CalcKey.D5 -> onDigit('5')
            CalcKey.D6 -> onDigit('6')
            CalcKey.D7 -> onDigit('7')
            CalcKey.D8 -> onDigit('8')
            CalcKey.D9 -> onDigit('9')
            CalcKey.DECIMAL -> onDecimalPoint()

            CalcKey.PLUS -> onOperator("+")
            CalcKey.MINUS -> onOperator("-")
            CalcKey.TIMES -> onOperator("*")
            CalcKey.DIVIDE -> onOperator("/")
            CalcKey.POWER -> onOperator("^")
            CalcKey.MOD -> onOperator("mod")

            CalcKey.OPEN -> onOpenParen()
            CalcKey.CLOSE -> onCloseParen()
            CalcKey.PERCENT -> onPercentKey()
            CalcKey.FACTORIAL -> onFactorial()
            CalcKey.SIGN -> onSignToggle()
            CalcKey.ANS -> onAns()

            CalcKey.AC -> onAllClear()
            CalcKey.CE -> onClearEntry()
            CalcKey.BACK -> onBackspace()
            CalcKey.UNDO -> onUndo()
            CalcKey.REDO -> onRedo()
            CalcKey.EQUALS -> onEquals()
            CalcKey.COPY -> onCopyExpressionAndResult()
            CalcKey.PASTE -> onPaste(clipboardText().orEmpty())
            CalcKey.SAVE -> requestSave()

            CalcKey.MC -> onMemoryClear()
            CalcKey.MR -> onMemoryRecall()
            CalcKey.M_PLUS -> onMemoryAdd()
            CalcKey.M_MINUS -> onMemorySubtract()
            CalcKey.MS -> onMemoryStore()

            CalcKey.DEG_MODE -> toggleAngleMode()

            CalcKey.SIN -> onFunction("sin(")
            CalcKey.COS -> onFunction("cos(")
            CalcKey.TAN -> onFunction("tan(")
            CalcKey.ASIN -> onFunction("asin(")
            CalcKey.ACOS -> onFunction("acos(")
            CalcKey.ATAN -> onFunction("atan(")
            CalcKey.SINH -> onFunction("sinh(")
            CalcKey.COSH -> onFunction("cosh(")
            CalcKey.TANH -> onFunction("tanh(")

            CalcKey.SQRT -> onFunction("sqrt(")
            CalcKey.CBRT -> onFunction("cbrt(")
            CalcKey.NTH_ROOT -> onFunction("root(")
            CalcKey.LN -> onFunction("ln(")
            CalcKey.LOG -> onFunction("log10(")
            CalcKey.EXP_FN -> onFunction("exp(")
            CalcKey.ABS -> onFunction("abs(")
            CalcKey.GCD -> onFunction("gcd(")
            CalcKey.LCM -> onFunction("lcm(")
            CalcKey.NCR -> onFunction("ncr(")
            CalcKey.NPR -> onFunction("npr(")

            CalcKey.SQUARE -> insertSequence("^2")
            CalcKey.CUBE -> insertSequence("^3")
            // x^-1: unary minus is legal directly after `^`, so this is exact.
            CalcKey.INV -> insertSequence("^-1")
            CalcKey.TEN_X -> insertSequence("10^")
            CalcKey.EE -> insertSequence("*10^")

            CalcKey.PI -> onConstant("π")
            CalcKey.E_CONST -> onConstant("e")
            CalcKey.TAU -> onConstant("τ")
            CalcKey.PHI -> onConstant("φ")
        }
    }

    /**
     * Inserts a short literal sequence key-by-key through the buffer, so the
     * undo stack and token semantics stay identical to real key presses.
     */
    private fun insertSequence(text: String) {
        text.forEach { ch ->
            when {
                ch.isDigit() -> buffer.pressDigit(ch)
                ch in "+-*/^" -> buffer.pressOperator(ch.toString())
                else -> Unit
            }
        }
        _state.update { it.copy(evaluated = false) }
        refresh()
        emitHaptic(strong = false)
    }

    /**
     * Hardware keyboard mapping (spec: "keyboard input"). Returns true when the
     * event was consumed so the screen can report unhandled keys honestly.
     */
    fun onHardwareKey(keyChar: Char?): Boolean {
        val ch = keyChar ?: return false
        return when {
            ch.isDigit() -> { onDigit(ch); true }
            ch == '.' || ch == ',' -> { onDecimalPoint(); true }
            ch == '+' || ch == '-' || ch == '*' || ch == '/' || ch == '^' -> { onOperator(ch.toString()); true }
            ch == 'x' || ch == 'X' -> { onOperator("*"); true }
            ch == '(' -> { onOpenParen(); true }
            ch == ')' -> { onCloseParen(); true }
            ch == '%' -> { onPercentKey(); true }
            ch == '!' -> { onFactorial(); true }
            ch == '=' || ch == '\n' || ch == '\r' -> { onEquals(); true }
            ch == '\b' -> { onBackspace(); true }
            else -> false
        }
    }

    // ---- memory ---------------------------------------------------------------------

    fun onMemoryStore() {
        val value = currentValueForMemory() ?: return reportNoValue()
        buffer.memoryStore(value)
        refresh()
        emitHaptic(strong = false)
        _events.tryEmit(CalculatorEvent.Message("Stored in memory"))
    }

    fun onMemoryRecall() = mutate { pressMemoryRecall() }

    fun onMemoryAdd() {
        val value = currentValueForMemory() ?: return reportNoValue()
        buffer.memoryAdd(value)
        refresh()
        _events.tryEmit(CalculatorEvent.Message("Added to memory"))
    }

    fun onMemorySubtract() {
        val value = currentValueForMemory() ?: return reportNoValue()
        buffer.memorySubtract(value)
        refresh()
        _events.tryEmit(CalculatorEvent.Message("Subtracted from memory"))
    }

    fun onMemoryClear() {
        buffer.memoryClear()
        refresh()
        _events.tryEmit(CalculatorEvent.Message("Memory cleared"))
    }

    /**
     * The value a memory operation acts on: the committed result when the screen
     * is showing one, otherwise the live preview of the current buffer.
     */
    private fun currentValueForMemory(): CalcNumber? {
        lastResultValue?.let { return it }
        val angle = parseAngle(_state.value.settings)
        return buffer.preview(angle, ans)?.valueOrNull()
    }

    private fun reportNoValue() {
        _events.tryEmit(CalculatorEvent.Message("Nothing to store yet"))
    }

    // ---- clipboard --------------------------------------------------------------------

    fun onCopyResult() {
        val text = _state.value.result
        if (text.isBlank() || text == "0" && !hasCommittedResult()) return
        copyToClipboard("Axiom Calc result", text)
    }

    fun onCopyExpression() {
        val text = _state.value.expression
        if (text.isBlank()) return
        copyToClipboard("Axiom Calc expression", text)
    }

    fun onCopyExpressionAndResult() {
        val text = "${_state.value.expression} = ${_state.value.result}"
        if (_state.value.expression.isBlank()) return
        copyToClipboard("Axiom Calc calculation", text)
    }

    private fun hasCommittedResult(): Boolean = lastResultValue != null

    private fun copyToClipboard(label: String, text: String) {
        runCatching {
            val manager = clipboard ?: return@runCatching
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
            // Android 13+ shows its own confirmation; below that we confirm in-app.
            if (android.os.Build.VERSION.SDK_INT < 33) {
                _events.tryEmit(CalculatorEvent.Message("Copied"))
            }
        }
        emitHaptic(strong = false)
    }

    /** Paste is a text insert, sanitized to characters the engine accepts. */
    fun onPaste(clipText: String) {
        val sanitized = clipText.filter { it.isDigit() || it in "+-*/^%.()!," || it.isWhitespace() }
        if (sanitized.isBlank()) {
            _events.tryEmit(CalculatorEvent.Message("Clipboard has no usable numbers"))
            return
        }
        _state.update { it.copy(evaluated = false) }
        sanitized.forEach { ch ->
            when {
                ch.isDigit() -> buffer.pressDigit(ch)
                ch == '.' -> buffer.pressDecimalPoint()
                ch in "+-*/^" -> buffer.pressOperator(ch.toString())
                ch == '(' -> buffer.pressOpenParen()
                ch == ')' -> buffer.pressCloseParen()
                ch == '%' -> buffer.pressPercent()
                ch == '!' -> buffer.pressFactorial()
                else -> Unit // whitespace and commas are ignored
            }
        }
        refresh()
    }

    /** Text currently on the clipboard, for the paste affordance. */
    fun clipboardText(): String? = runCatching {
        val manager = clipboard ?: return@runCatching null
        val item = manager.primaryClip?.getItemAt(0) ?: return@runCatching null
        item.text?.toString()
    }.getOrNull()

    // ---- persistence ------------------------------------------------------------------

    fun requestSave() {
        if (lastResultValue == null) {
            _events.tryEmit(CalculatorEvent.Message("Evaluate a calculation first"))
            return
        }
        _events.tryEmit(CalculatorEvent.RequestSaveName)
    }

    fun saveCalculation(name: String) {
        val value = lastResultValue ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _events.tryEmit(CalculatorEvent.Message("Enter a name to save"))
            return
        }
        viewModelScope.launch {
            runCatching {
                savedRepository.add(
                    SavedCalculation(
                        id = 0,
                        name = trimmed,
                        expression = lastExpression,
                        resultValue = value.toCanonicalString(),
                        resultDisplay = lastResultDisplay,
                        origin = origin,
                        savedAtMillis = System.currentTimeMillis(),
                    ),
                )
                _events.tryEmit(CalculatorEvent.Message("Saved"))
            }.onFailure {
                _events.tryEmit(CalculatorEvent.Message("Could not save this calculation"))
            }
        }
    }

    /** Restores a history or saved row exactly, using the stored normalized value. */
    fun restore(expression: String, resultValue: String) {
        val value = CalcNumber.fromCanonical(resultValue)
        if (value == null) {
            _events.tryEmit(CalculatorEvent.Message("This stored value can no longer be restored"))
            return
        }
        buffer.pressAllClear()
        ans = value
        lastResultValue = value
        lastResultDisplay = NumberFormatter.format(value, displayPolicy(_state.value.settings))
        lastExpression = expression
        _state.update {
            it.copy(
                expression = expression,
                result = lastResultDisplay,
                preview = null,
                error = null,
                evaluated = true,
                hasExpression = false,
            )
        }
        emitHaptic(strong = false)
    }

    /** Switches DEG/RAD and re-evaluates the preview so the change is visible. */
    fun toggleAngleMode() {
        viewModelScope.launch {
            settingsRepository.update { current ->
                current.copy(
                    angleModeName = if (current.angleModeName == "DEGREES") "RADIANS" else "DEGREES",
                )
            }
        }
        emitHaptic(strong = false)
    }
}

/** Standard calculator: fixed keypad, no scientific panel. */
class StandardCalculatorViewModel(
    context: Context,
    settingsRepository: SettingsRepository,
    historyRepository: HistoryRepository,
    savedRepository: SavedCalculationRepository,
) : CalculatorViewModel(context, settingsRepository, historyRepository, savedRepository, CalcOrigin.STANDARD)

/** Scientific calculator: compact + expanded panels, DEG/RAD, constants. */
class ScientificCalculatorViewModel(
    context: Context,
    settingsRepository: SettingsRepository,
    historyRepository: HistoryRepository,
    savedRepository: SavedCalculationRepository,
) : CalculatorViewModel(context, settingsRepository, historyRepository, savedRepository, CalcOrigin.SCIENTIFIC)
