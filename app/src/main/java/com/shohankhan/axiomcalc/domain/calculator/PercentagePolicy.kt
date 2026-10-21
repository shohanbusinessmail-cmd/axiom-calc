package com.shohankhan.axiomcalc.domain.calculator

import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * The documented percentage-key semantics of the keypad calculators.
 *
 * The *expression language* treats postfix `%` as "divide by 100" everywhere.
 * The *percentage key* additionally implements the conventional calculator
 * behaviour for additive contexts, and this class is the single place that
 * defines it (see MATHEMATICS_SPEC.md §Percentage semantics):
 *
 *  | context before the entry | `entry %` means                    | example            |
 *  |--------------------------|----------------------------------|--------------------|
 *  | `+`                      | left + left*entry/100            | 200 + 10% = 220    |
 *  | `-`                      | left - left*entry/100            | 200 - 10% = 180    |
 *  | `*`                      | left * entry/100                 | 200 * 15% = 30     |
 *  | `/`                      | left / (entry/100)               | 30 / 15% = 200     |
 *  | none (start of input)    | entry/100                        | 15% = 0.15         |
 *
 * The rewrite is performed on the token list *before* evaluation, substituting
 * the computed value as an exact literal, so the value the user sees, the value
 * that is evaluated and the value written to history are always identical.
 *
 * If the left-hand prefix cannot itself be evaluated (the user is mid-edit),
 * the policy falls back to the plain "divide by 100" meaning so that live
 * preview stays useful; this fallback is documented and tested.
 */
object PercentagePolicy {

    private val HUNDRED = CalcNumber.of(100)

    fun resolve(
        tokens: List<BufToken>,
        angleMode: AngleMode = AngleMode.DEGREES,
        ans: CalcNumber = CalcNumber.ZERO,
    ): List<BufToken> {
        if (tokens.none { it is BufToken.Percent }) return tokens

        val out = ArrayList<BufToken>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t !is BufToken.Percent) {
                out += t
                i++
                continue
            }
            val entryStart = entryStartBefore(tokens, i)
            if (entryStart == null) {
                out += t
                i++
                continue
            }
            val entryLength = i - entryStart
            val entryText = tokens.subList(entryStart, i).joinToString("") { it.render() }
            val entryValue = ExpressionEngine.evaluate(entryText, angleMode, ans).valueOrNull()
            if (entryValue == null) {
                out += t
                i++
                continue
            }
            // The prefix must be taken from the ALREADY REWRITTEN output, so that
            // chained percentages compound correctly (200 + 10% + 5% = 231).
            val entryStartOut = out.size - entryLength
            val contextOperator = binaryOperatorBefore(out, entryStartOut)
            val replacement: CalcNumber = when (contextOperator?.second) {
                "+", "-" -> {
                    val prefix = out.subList(0, contextOperator.first)
                    val prefixValue = if (prefix.isEmpty()) {
                        CalcNumber.ZERO
                    } else {
                        val prefixText = prefix.joinToString("") { it.render() } + closingFor(prefix)
                        ExpressionEngine.evaluate(prefixText, angleMode, ans).valueOrNull()
                    }
                    if (prefixValue == null) entryValue / HUNDRED
                    else prefixValue * entryValue / HUNDRED
                }
                else -> entryValue / HUNDRED
            }
            repeat(entryLength) { if (out.isNotEmpty()) out.removeAt(out.lastIndex) }
            out += BufToken.Num(
                text = replacement.abs().value.stripTrailingZeros().toPlainString(),
                negated = replacement.isNegative,
            )
            i++
        }
        return out
    }

    /** Start index of the numeric entry (with unary minus and postfixes) ending just before [percentIndex]. */
    private fun entryStartBefore(tokens: List<BufToken>, percentIndex: Int): Int? {
        var i = percentIndex - 1
        if (i < 0) return null
        while (i >= 0 && (tokens[i] is BufToken.Percent || tokens[i] is BufToken.Factorial)) i--
        if (i < 0 || tokens[i] !is BufToken.Num) return null
        val numIndex = i
        if (numIndex - 1 >= 0) {
            val prev = tokens[numIndex - 1]
            if (prev is BufToken.Op && prev.symbol == "-") {
                val before = if (numIndex - 2 < 0) null else tokens[numIndex - 2]
                if (before == null || before is BufToken.Op || before is BufToken.LParen) return numIndex - 1
            }
        }
        return numIndex
    }

    /** Nearest preceding binary operator at the same parenthesis depth. */
    private fun binaryOperatorBefore(tokens: List<BufToken>, entryStart: Int): Pair<Int, String>? {
        val depth = IntArray(tokens.size)
        var d = 0
        tokens.forEachIndexed { idx, tk ->
            if (tk is BufToken.RParen) d--
            depth[idx] = d
            if (tk is BufToken.LParen || (tk is BufToken.Atom && tk.text.endsWith("("))) d++
        }
        val targetDepth = if (entryStart < tokens.size) depth[entryStart] else 0
        for (idx in entryStart - 1 downTo 0) {
            val tk = tokens[idx]
            if (tk is BufToken.RParen) return null // entry belongs to an inner group
            if (depth[idx] != targetDepth) continue
            if (tk is BufToken.Op) {
                val prev = if (idx == 0) null else tokens[idx - 1]
                val unary = tk.symbol == "-" && (prev == null || prev is BufToken.Op || prev is BufToken.LParen)
                if (!unary) return idx to tk.symbol
            }
        }
        return null
    }

    private fun closingFor(prefix: List<BufToken>): String {
        var open = 0; var close = 0
        prefix.forEach { if (it is BufToken.LParen) open++; if (it is BufToken.RParen) close++ }
        return ")".repeat((open - close).coerceAtLeast(0))
    }
}
