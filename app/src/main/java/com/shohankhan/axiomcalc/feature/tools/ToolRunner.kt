package com.shohankhan.axiomcalc.feature.tools

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.domain.formula.FormInput
import com.shohankhan.axiomcalc.domain.formula.FormResult
import com.shohankhan.axiomcalc.domain.formula.FormulaCatalog
import com.shohankhan.axiomcalc.domain.solver.QuickSolve
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * One computable tool: either a Formula Calculator form or a Quick Solve tool.
 *
 * Both are adapted to this single interface so one ViewModel and one UI panel
 * serve every form in the app. The adapter holds no logic of its own — it calls
 * the same domain function the unit tests call, which is what makes it
 * impossible for the UI to show a different answer from the tested engine.
 */
sealed interface ToolSpec {
    val id: String
    val title: String
    val inputs: List<FormInput>

    /** Human-readable statement of what the tool computes. */
    val summary: String

    /** The formula as written, shown verbatim next to the inputs. */
    val formulaText: String?

    /** Extra explanation, including any restrictions or domain notes. */
    val explanation: String

    /** Runs the tool. Throws [CalcException] with a typed error on bad input. */
    fun compute(values: Map<String, String>): FormResult

    data class Form(val form: com.shohankhan.axiomcalc.domain.formula.FormulaForm) : ToolSpec {
        override val id: String get() = form.id
        override val title: String get() = form.title
        override val inputs: List<FormInput> get() = form.inputs
        override val summary: String get() = form.explanation
        override val formulaText: String get() = form.formulaText
        override val explanation: String get() = form.explanation
        override fun compute(values: Map<String, String>): FormResult = form.compute(values)
    }

    data class Tool(val tool: QuickSolve.QuickTool) : ToolSpec {
        override val id: String get() = tool.id
        override val title: String get() = tool.title
        override val inputs: List<FormInput> get() = tool.inputs
        override val summary: String get() = tool.summary
        override val formulaText: String? get() = null
        override val explanation: String get() = tool.summary
        override fun compute(values: Map<String, String>): FormResult = tool.solve(values)
    }

    companion object {
        /**
         * Resolves an id from either registry. Returns null for an unknown id so
         * the caller can show "not found" instead of a blank or fake screen.
         */
        fun resolve(id: String): ToolSpec? =
            FormulaCatalog.byId(id)?.let { Form(it) } ?: QuickSolve.byId(id)?.let { Tool(it) }
    }
}

/** Immutable state of a running tool. */
data class ToolUiState(
    val values: Map<String, String> = emptyMap(),
    val result: FormResult? = null,
    val error: CalcError? = null,
    /** Field-level messages, keyed by input id. */
    val fieldErrors: Map<String, String> = emptyMap(),
    val showSteps: Boolean = false,
    val hasComputed: Boolean = false,
    /** True once every declared input has a non-blank value. */
    val isComplete: Boolean = false,
)

/**
 * Runs a [ToolSpec] against the values the user typed.
 *
 * Recomputation happens on every keystroke and is *live*, but a failure is
 * reported as a typed error rather than replaced by a plausible-looking number;
 * when the inputs are incomplete the previous result is cleared, never kept, so
 * the screen can never show a stale answer next to new inputs.
 */
class ToolRunnerViewModel(
    val spec: ToolSpec,
    /**
     * Called with a completed result once typing settles, so one logical
     * computation produces exactly one history row rather than one per
     * keystroke. Null disables recording (used by the lab modules, which
     * record through their own explicit actions).
     */
    private val onResult: ((ToolSpec, Map<String, String>, FormResult) -> Unit)? = null,
) : androidx.lifecycle.ViewModel() {

    private var recordJob: kotlinx.coroutines.Job? = null

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ToolUiState())
    val state: kotlinx.coroutines.flow.StateFlow<ToolUiState> = _state

    init {
        recompute()
    }

    fun setValue(inputId: String, raw: String) {
        val next = _state.value.values.toMutableMap()
        next[inputId] = raw
        _state.value = _state.value.copy(values = next)
        recompute()
    }

    fun setAll(values: Map<String, String>) {
        _state.value = _state.value.copy(values = values)
        recompute()
    }

    fun toggleSteps() {
        _state.value = _state.value.copy(showSteps = !_state.value.showSteps)
    }

    fun clear() {
        _state.value = ToolUiState(showSteps = _state.value.showSteps)
    }

    /**
     * Debounced recording. The delay is a named constant so tests can drive it
     * with a virtual-time dispatcher instead of sleeping.
     */
    private fun scheduleRecording(values: Map<String, String>, result: FormResult) {
        val sink = onResult ?: return
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            kotlinx.coroutines.delay(RECORD_DEBOUNCE_MS)
            sink(spec, values, result)
        }
    }

    private fun recompute() {
        val values = _state.value.values
        val complete = spec.inputs.all { !values[it.id].isNullOrBlank() }
        if (!complete) {
            _state.value = _state.value.copy(
                result = null,
                error = null,
                fieldErrors = emptyMap(),
                hasComputed = false,
                isComplete = false,
            )
            return
        }
        try {
            val result = spec.compute(values)
            _state.value = _state.value.copy(
                result = result,
                error = null,
                fieldErrors = emptyMap(),
                hasComputed = true,
                isComplete = true,
            )
            scheduleRecording(values, result)
        } catch (e: CalcException) {
            _state.value = _state.value.copy(
                result = null,
                error = e.error,
                fieldErrors = emptyMap(),
                hasComputed = true,
                isComplete = true,
            )
        } catch (e: IllegalArgumentException) {
            // Domain adapters report unusable input as an argument failure; it
            // is surfaced as a typed invalid-argument error, never swallowed.
            _state.value = _state.value.copy(
                result = null,
                error = CalcError.InvalidArgument(e.message ?: "invalid input"),
                hasComputed = true,
                isComplete = true,
            )
        }
    }

    companion object {
        /** Quiet period after the last keystroke before a result is recorded. */
        const val RECORD_DEBOUNCE_MS = 600L
    }
}
