package com.shohankhan.axiomcalc.math.evaluator

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.parser.Expr
import com.shohankhan.axiomcalc.math.parser.Parser
import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * Outcome of a calculation. A failure always carries its typed [CalcError];
 * the engine never returns a plausible-looking value after a failure.
 */
sealed interface CalcResult {
    data class Success(val value: CalcNumber) : CalcResult
    data class Failure(val error: CalcError) : CalcResult

    val isSuccess: Boolean get() = this is Success
    fun valueOrNull(): CalcNumber? = (this as? Success)?.value
    fun errorOrNull(): CalcError? = (this as? Failure)?.error
}

/**
 * Public entry point of the shared mathematics engine.
 *
 * This object is pure JVM Kotlin: no Android types, no I/O, no state. It is the
 * single authoritative calculation surface reused by the Standard calculator,
 * the Scientific calculator, Quick Solve and every formula form, and it is the
 * unit that the standalone verification harness compiles and tests without any
 * Android dependency.
 */
object ExpressionEngine {

    /** Parses to a reusable tree. Returns null when the source is not valid. */
    fun parseTree(source: String): Expr? = try {
        Parser.parse(source)
    } catch (e: CalcException) {
        null
    }

    /** Parse error without evaluating, for diagnostics and tests. */
    fun parseError(source: String): CalcError? = try {
        Parser.parse(source)
        null
    } catch (e: CalcException) {
        e.error
    }

    /** Evaluates a source expression end to end. */
    fun evaluate(
        source: String,
        angleMode: AngleMode = AngleMode.DEGREES,
        ans: CalcNumber = CalcNumber.ZERO,
    ): CalcResult = try {
        val tree = Parser.parse(source)
        CalcResult.Success(Evaluator(angleMode, ans).evaluate(tree))
    } catch (e: CalcException) {
        CalcResult.Failure(e.error)
    }

    /**
     * Live-preview evaluation used while the user is still typing.
     *
     * Returns null when the expression is merely *incomplete* (trailing operator,
     * unbalanced open parenthesis, trailing decimal point) so the UI can keep the
     * previous preview instead of flashing an error at every keystroke.
     */
    fun preview(
        source: String,
        angleMode: AngleMode = AngleMode.DEGREES,
        ans: CalcNumber = CalcNumber.ZERO,
    ): CalcResult? {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return null
        val result = evaluate(trimmed, angleMode, ans)
        return when (val error = result.errorOrNull()) {
            null -> result
            is CalcError.Incomplete,
            is CalcError.UnbalancedParentheses,
            -> null
            is CalcError.Syntax -> if (isTruncationSyntax(error, trimmed)) null else result
            else -> result
        }
    }

    /** True when a syntax error is just "the user has not finished typing yet". */
    private fun isTruncationSyntax(error: CalcError.Syntax, source: String): Boolean {
        if (error.position < 0) return false
        return error.position >= source.length - 1
    }

    /**
     * Evaluates a pre-parsed tree; used by solvers that build or substitute trees
     * so that displayed steps and computed values can never diverge.
     */
    fun evaluateTree(
        tree: Expr,
        angleMode: AngleMode = AngleMode.DEGREES,
        ans: CalcNumber = CalcNumber.ZERO,
    ): CalcResult = try {
        CalcResult.Success(Evaluator(angleMode, ans).evaluate(tree))
    } catch (e: CalcException) {
        CalcResult.Failure(e.error)
    }
}


