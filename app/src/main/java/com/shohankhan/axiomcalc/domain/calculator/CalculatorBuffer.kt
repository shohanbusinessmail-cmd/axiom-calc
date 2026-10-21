package com.shohankhan.axiomcalc.domain.calculator

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * One keypad-inserted unit of the Standard/Scientific expression buffer.
 *
 * Tokens (not raw characters) are the unit of editing, which is what makes the
 * documented backspace policy exact: one backspace removes exactly one token,
 * whether that token is a single digit or the whole `sin(` insertion.
 */
sealed class BufToken {
    abstract fun render(): String

    /** A numeric entry in progress; [text] may be empty or end with '.'. */
    data class Num(val text: String, val negated: Boolean) : BufToken() {
        override fun render(): String = (if (negated) "-" else "") + text
    }

    /** Binary operator, exponent, or a unary minus at entry-leading position. */
    data class Op(val symbol: String) : BufToken() {
        override fun render(): String = symbol
    }

    data object LParen : BufToken() { override fun render(): String = "(" }
    data object RParen : BufToken() { override fun render(): String = ")" }
    data object Percent : BufToken() { override fun render(): String = "%" }
    data object Factorial : BufToken() { override fun render(): String = "!" }

    /** An atomic insertion: function call opening, constant, or previous answer. */
    data class Atom(val text: String) : BufToken() {
        override fun render(): String = text
    }
}

/**
 * The editable expression state of a calculator screen, with the exact editing,
 * undo/redo, memory and percentage semantics that the specification requires to
 * be documented and tested.
 *
 * Editing policy (MATHEMATICS_SPEC.md §Editing):
 *  * BACKSPACE removes the last whole token.
 *  * CE (clear entry) removes the trailing numeric entry only: its digits, its
 *    leading unary minus and any postfix % or ! attached to it. The rest of the
 *    expression survives.
 *  * AC clears the buffer but never touches memory or the previous answer.
 *  * UNDO/REDO are snapshot stacks over the token list; every mutating key press
 *    records one snapshot, so one undo restores exactly one key press.
 *  * A single tap can never apply an operation twice: evaluation happens only
 *    through [evaluate], and the ViewModel guards history writes.
 *  * Operator keys replace a trailing operator instead of stacking two.
 *  * Missing closing parentheses are supplied at evaluation time only; the
 *    buffer itself keeps showing what the user typed.
 */
class CalculatorBuffer(private val maxTokens: Int = 400) {

    private val tokens = ArrayList<BufToken>(32)
    private val undoStack = ArrayDeque<List<BufToken>>()
    private val redoStack = ArrayDeque<List<BufToken>>()

    var memory: CalcNumber? = null
        private set

    val memoryOccupied: Boolean get() = memory != null

    fun expressionText(): String = tokens.joinToString("") { it.render() }

    fun tokensSnapshot(): List<BufToken> = tokens.toList()

    val isEmpty: Boolean get() = tokens.isEmpty()

    // ---- input ---------------------------------------------------------------

    fun pressDigit(digit: Char) {
        mutate {
            val last = tokens.lastOrNull()
            when {
                last is BufToken.Num && !endsWithPostfix() ->
                    tokens[tokens.lastIndex] = last.copy(text = last.text + digit)
                else -> tokens += BufToken.Num(digit.toString(), negated = false)
            }
        }
    }

    fun pressDecimalPoint() {
        mutate {
            val last = tokens.lastOrNull()
            when {
                last is BufToken.Num && !last.text.contains('.') && !endsWithPostfix() ->
                    tokens[tokens.lastIndex] = last.copy(text = if (last.text.isEmpty()) "0." else last.text + ".")
                last is BufToken.Num -> Unit // a second point in one entry is ignored
                else -> tokens += BufToken.Num("0.", negated = false)
            }
        }
    }

    /** symbol is one of + - * / ^ or "mod". */
    fun pressOperator(symbol: String) {
        mutate {
            val last = tokens.lastOrNull()
            when {
                tokens.isEmpty() -> if (symbol == "-") tokens += BufToken.Op("-") else Unit
                last is BufToken.Op ->
                    if (isUnaryPosition(tokens.lastIndex) && symbol != "-") Unit
                    else tokens[tokens.lastIndex] = BufToken.Op(symbol)
                last is BufToken.LParen -> if (symbol == "-") tokens += BufToken.Op("-") else Unit
                last is BufToken.Percent || last is BufToken.Factorial || last is BufToken.RParen ||
                    last is BufToken.Num || last is BufToken.Atom -> tokens += BufToken.Op(symbol)
                else -> Unit
            }
        }
    }

    fun pressOpenParen() {
        mutate {
            val last = tokens.lastOrNull()
            if (last is BufToken.Num || last is BufToken.RParen || last is BufToken.Atom && !last.text.endsWith("(")) {
                // juxtaposition: "5(" means 5*( - the parser documents this
                tokens += BufToken.LParen
            } else {
                tokens += BufToken.LParen
            }
        }
    }

    fun pressCloseParen() {
        mutate {
            if (openCount() > closeCount()) {
                val last = tokens.lastOrNull()
                if (last is BufToken.Num || last is BufToken.RParen || last is BufToken.Atom ||
                    last is BufToken.Percent || last is BufToken.Factorial
                ) {
                    tokens += BufToken.RParen
                }
            }
        }
    }

    fun pressFunction(text: String) {
        mutate { maybeImplicitBeforeAtom(); tokens += BufToken.Atom(text) }
    }

    fun pressConstant(text: String) {
        mutate { maybeImplicitBeforeAtom(); tokens += BufToken.Atom(text) }
    }

    fun pressAns() {
        mutate { maybeImplicitBeforeAtom(); tokens += BufToken.Atom("ans") }
    }

    fun pressPercent() {
        mutate {
            val last = tokens.lastOrNull()
            if (last is BufToken.Num && last.text.isNotEmpty() || last is BufToken.RParen || last is BufToken.Atom) {
                if (tokens.lastOrNull() !is BufToken.Percent) tokens += BufToken.Percent
            }
        }
    }

    fun pressFactorial() {
        mutate {
            val last = tokens.lastOrNull()
            if (last is BufToken.Num || last is BufToken.RParen || last is BufToken.Atom) {
                tokens += BufToken.Factorial
            }
        }
    }

    /** +/- on the keypad toggles the sign of the entry currently being typed. */
    fun pressSignToggle() {
        mutate {
            val entryStart = currentEntryStart()
            if (entryStart == null) {
                tokens += BufToken.Num("", negated = true)
                return@mutate
            }
            val numIndex = if (tokens[entryStart] is BufToken.Op) entryStart + 1 else entryStart
            val num = tokens[numIndex] as? BufToken.Num ?: return@mutate
            tokens[numIndex] = num.copy(negated = !num.negated)
        }
    }

    fun pressBackspace() {
        mutate { if (tokens.isNotEmpty()) tokens.removeAt(tokens.lastIndex) }
    }

    fun pressClearEntry() {
        mutate {
            val start = currentEntryStart()
            if (start != null) {
                var end = tokens.size
                while (end > start && (tokens[end - 1] is BufToken.Percent || tokens[end - 1] is BufToken.Factorial)) end--
                while (end - 1 >= start) { tokens.removeAt(end - 1); end-- }
            }
        }
    }

    fun pressAllClear() {
        mutate { tokens.clear() }
    }

    // ---- undo / redo -----------------------------------------------------------

    fun undo(): Boolean = mutateReporting {
        if (undoStack.isEmpty()) return@mutateReporting false
        redoStack.addLast(tokens.toList())
        restore(undoStack.removeLast())
        true
    }

    fun redo(): Boolean = mutateReporting {
        if (redoStack.isEmpty()) return@mutateReporting false
        undoStack.addLast(tokens.toList())
        restore(redoStack.removeLast())
        true
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    // ---- memory ------------------------------------------------------------------

    /** MS: store the current entry value (or the whole expression when complete). */
    fun memoryStore(value: CalcNumber) { memory = value }
    fun memoryRecall(): CalcNumber? = memory
    fun memoryAdd(value: CalcNumber) { memory = (memory ?: CalcNumber.ZERO) + value }
    fun memorySubtract(value: CalcNumber) { memory = (memory ?: CalcNumber.ZERO) - value }
    fun memoryClear() { memory = null }

    /** Inserts the memory value as a literal entry. */
    fun pressMemoryRecall() {
        val m = memory ?: return
        mutate {
            maybeImplicitBeforeAtom()
            val negative = m.isNegative
            val text = m.abs().value.stripTrailingZeros().toPlainString()
            tokens += BufToken.Num(text, negated = negative)
        }
    }

    // ---- evaluation ----------------------------------------------------------------

    /**
     * Evaluates the buffer with the documented percentage-key semantics and with
     * missing closing parentheses supplied. The buffer itself is unchanged.
     */
    fun evaluate(angleMode: AngleMode, ans: CalcNumber): CalcResult {
        val rewritten = PercentagePolicy.resolve(tokens, angleMode, ans)
        val text = rewritten.joinToString("") { it.render() } + closingSuffix(rewritten)
        if (text.isBlank()) return CalcResult.Failure(CalcError.Incomplete())
        return ExpressionEngine.evaluate(text, angleMode, ans)
    }

    /** Live preview; null while the expression is still incomplete. */
    fun preview(angleMode: AngleMode, ans: CalcNumber): CalcResult? {
        val rewritten = PercentagePolicy.resolve(tokens, angleMode, ans)
        val text = rewritten.joinToString("") { it.render() } + closingSuffix(rewritten)
        return ExpressionEngine.preview(text, angleMode, ans)
    }

    fun closingSuffix(list: List<BufToken> = tokens): String =
        ")".repeat((openCount(list) - closeCount(list)).coerceAtLeast(0))

    /** Value of the entry currently being typed, for memory operations. */
    fun currentEntryValue(angleMode: AngleMode, ans: CalcNumber): CalcNumber? {
        val start = currentEntryStart() ?: return null
        var end = tokens.size
        val piece = tokens.subList(start, end).joinToString("") { it.render() }
        return ExpressionEngine.evaluate(piece, angleMode, ans).valueOrNull()
    }

    // ---- internals -------------------------------------------------------------------

    private fun endsWithPostfix(): Boolean =
        tokens.size >= 2 && (tokens[tokens.lastIndex - 1] is BufToken.Percent ||
            tokens[tokens.lastIndex - 1] is BufToken.Factorial) && tokens.lastOrNull() is BufToken.Num

    private fun maybeImplicitBeforeAtom() {
        val last = tokens.lastOrNull()
        if (last is BufToken.Num || last is BufToken.RParen) {
            // juxtaposition is parsed as multiplication; nothing to insert
        }
    }

    /** Open parentheses, counting the implicit paren of a function insertion. */
    private fun openCount(list: List<BufToken> = tokens): Int =
        list.count { it is BufToken.LParen } + list.count { it is BufToken.Atom && it.text.endsWith("(") }

    private fun closeCount(list: List<BufToken> = tokens): Int = list.count { it is BufToken.RParen }

    /** True when the Op at [index] is a unary minus (start, after Op, after '('). */
    private fun isUnaryPosition(index: Int): Boolean {
        val t = tokens[index]
        if (t !is BufToken.Op || t.symbol != "-") return false
        val prev = if (index == 0) null else tokens[index - 1]
        return prev == null || prev is BufToken.Op || prev is BufToken.LParen
    }

    /**
     * Index of the first token of the trailing numeric entry, or null when the
     * buffer does not end in an entry. Includes a leading unary minus.
     */
    private fun currentEntryStart(): Int? {
        var i = tokens.lastIndex
        if (i < 0) return null
        while (i >= 0 && (tokens[i] is BufToken.Percent || tokens[i] is BufToken.Factorial)) i--
        if (i < 0 || tokens[i] !is BufToken.Num) return null
        val numIndex = i
        if (numIndex - 1 >= 0 && isUnaryPosition(numIndex - 1)) return numIndex - 1
        return numIndex
    }

    private inline fun mutate(block: () -> Unit) {
        undoStack.addLast(tokens.toList())
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        block()
        if (tokens.size > maxTokens) {
            // Restore and refuse rather than grow without bound.
            restore(undoStack.removeLast())
            throw CalcException(CalcError.ResourceLimit("expression length", "$maxTokens tokens"))
        }
    }

    private inline fun mutateReporting(block: () -> Boolean): Boolean {
        val before = tokens.toList()
        val result = block()
        if (!result) return false
        if (before == tokens) return false
        return true
    }

    private fun restore(state: List<BufToken>) {
        tokens.clear()
        tokens.addAll(state)
    }
}
