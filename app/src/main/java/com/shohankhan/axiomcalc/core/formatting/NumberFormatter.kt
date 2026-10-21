package com.shohankhan.axiomcalc.core.formatting

import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** How a result is written out. */
enum class NumberNotation { DECIMAL, SCIENTIFIC, ENGINEERING }

/** Explicit rounding policies exposed by the Math Lab engineering panel. */
enum class RoundingPolicy(val label: String) {
    HALF_EVEN("Half to even (banker's)"),
    HALF_UP("Half away from zero"),
    DOWN("Toward zero (truncate)"),
    UP("Away from zero"),
    CEILING("Toward +infinity"),
    FLOOR("Toward -infinity"),
    ;

    fun toMode(): RoundingMode = when (this) {
        HALF_EVEN -> RoundingMode.HALF_EVEN
        HALF_UP -> RoundingMode.HALF_UP
        DOWN -> RoundingMode.DOWN
        UP -> RoundingMode.UP
        CEILING -> RoundingMode.CEILING
        FLOOR -> RoundingMode.FLOOR
    }
}

/**
 * The user's presentation policy. Presentation is strictly separated from the
 * normalized value: [NumberFormatter] never feeds its own output back into the
 * engine (MATHEMATICS_SPEC.md §Precision policy).
 */
data class DisplayPolicy(
    val autoPrecision: Boolean = true,
    val decimals: Int = 6,
    val grouping: Boolean = true,
    val notation: NumberNotation = NumberNotation.DECIMAL,
) {
    companion object {
        val DEFAULT = DisplayPolicy()
    }
}

/**
 * Single, authoritative presentation layer for every numeric result in the app.
 *
 * Switching rules (documented, not incidental):
 *  * In [NumberNotation.DECIMAL] with automatic precision, plain notation is
 *    used while the adjusted exponent lies in [-6, 12]; outside that window the
 *    value is shown in scientific notation with 12 significant digits, because
 *    a plain rendering would be either enormous or indistinguishable from zero.
 *  * [NumberNotation.SCIENtific] keeps the mantissa in [1, 10).
 *  * [NumberNotation.ENGINEERING] keeps the exponent a multiple of three and
 *    the mantissa in [1, 1000).
 *  * Grouping separators apply only to the integer part of plain decimal
 *    output, never inside an exponent.
 */
object NumberFormatter {

    /** Significant digits used in automatic mode. */
    const val AUTO_SIGNIFICANT_DIGITS = 12
    private const val PLAIN_MIN_EXPONENT = -6
    private const val PLAIN_MAX_EXPONENT = 12

    fun format(value: CalcNumber, policy: DisplayPolicy): String {
        if (value.isZero) return if (policy.grouping && policy.notation == NumberNotation.DECIMAL) "0" else plainZero(policy)
        return when (policy.notation) {
            NumberNotation.DECIMAL -> decimal(value, policy)
            NumberNotation.SCIENTIFIC -> scientific(value, sigFigs(policy), policy.autoPrecision)
            NumberNotation.ENGINEERING -> engineering(value, sigFigs(policy), policy.autoPrecision)
        }
    }

    private fun plainZero(policy: DisplayPolicy): String = when (policy.notation) {
        NumberNotation.DECIMAL -> "0"
        NumberNotation.SCIENTIFIC -> "0e0"
        NumberNotation.ENGINEERING -> "0e0"
    }

    private fun sigFigs(policy: DisplayPolicy): Int =
        if (policy.autoPrecision) AUTO_SIGNIFICANT_DIGITS else (policy.decimals + 1).coerceIn(1, 34)

    private fun decimal(value: CalcNumber, policy: DisplayPolicy): String {
        val v = if (policy.autoPrecision) {
            value.value.round(MathContext(AUTO_SIGNIFICANT_DIGITS, Precision.ROUNDING)).stripTrailingZeros()
        } else {
            value.value.setScale(policy.decimals, Precision.ROUNDING)
        }
        val exponent = adjustedExponent(v)
        if (exponent < PLAIN_MIN_EXPONENT || exponent > PLAIN_MAX_EXPONENT) {
            return scientific(value, sigFigs(policy))
        }
        val plain = if (v.scale() < 0) v.setScale(0, Precision.ROUNDING).toPlainString() else v.toPlainString()
        return if (policy.grouping) group(plain) else plain
    }

    /** `1.2345e7` style with the mantissa in [1, 10). */
    fun scientific(value: CalcNumber, significantDigits: Int, trimTrailingZeros: Boolean = true): String {
        if (value.isZero) return "0e0"
        val s = significantDigits.coerceIn(1, 34)
        val rounded = value.value.round(MathContext(s, Precision.ROUNDING))
        val exponent = adjustedExponent(rounded)
        val mantissa = rounded.movePointLeft(exponent).setScale(s - 1, Precision.ROUNDING)
        return "${renderMantissa(mantissa, trimTrailingZeros)}e${signed(exponent)}"
    }

    /** `12.345e6` style: exponent a multiple of three, mantissa in [1, 1000). */
    fun engineering(value: CalcNumber, significantDigits: Int, trimTrailingZeros: Boolean = true): String {
        if (value.isZero) return "0e0"
        val s = significantDigits.coerceIn(1, 34)
        val rounded = value.value.round(MathContext(s, Precision.ROUNDING))
        val rawExponent = adjustedExponent(rounded)
        val exponent = Math.floorDiv(rawExponent, 3) * 3
        // The mantissa carries 1..3 integer digits, so the fractional part takes
        // the remaining significant digits.
        val integerDigits = rawExponent - exponent + 1
        val fractionDigits = (s - integerDigits).coerceAtLeast(0)
        val mantissa = rounded.movePointLeft(exponent).setScale(fractionDigits, Precision.ROUNDING)
        return "${renderMantissa(mantissa, trimTrailingZeros)}e${signed(exponent)}"
    }

    /** Rounds to [significantDigits] significant figures, exactly. */
    fun significantFigures(value: CalcNumber, significantDigits: Int): CalcNumber {
        val s = significantDigits.coerceIn(1, 34)
        return CalcNumber.of(value.value.round(MathContext(s, Precision.ROUNDING)))
    }

    /** Rounds to a fixed number of decimal places under an explicit policy. */
    fun roundTo(value: CalcNumber, decimals: Int, policy: RoundingPolicy): CalcNumber =
        CalcNumber.of(value.value.setScale(decimals, policy.toMode()))

    /** Truncates toward zero to [decimals] places. */
    fun truncateTo(value: CalcNumber, decimals: Int): CalcNumber =
        CalcNumber.of(value.value.setScale(decimals, RoundingMode.DOWN))

    /** Diagnostics for the Math Lab "precision inspection" tool. */
    fun inspect(value: CalcNumber): String {
        val bd = value.value
        val asDouble = bd.toDouble()
        return buildString {
            appendLine("decimal (exact, ${bd.stripTrailingZeros().precision()} sig. digits): ${bd.toPlainString()}")
            appendLine("working context: ${Precision.MC.precision} significant digits, rounding ${Precision.ROUNDING}")
            append("as IEEE-754 double: $asDouble")
            if (asDouble.isFinite()) {
                append("  (ulp ${Math.ulp(asDouble)})")
            }
            appendLine()
            appendLine("scientific: ${scientific(value, AUTO_SIGNIFICANT_DIGITS)}")
            append("engineering: ${engineering(value, AUTO_SIGNIFICANT_DIGITS)}")
        }
    }

    // ---- internals ----------------------------------------------------------

    private fun renderMantissa(mantissa: BigDecimal, trim: Boolean): String =
        if (!trim) mantissa.toPlainString()
        else mantissa.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    private fun adjustedExponent(v: BigDecimal): Int = v.precision() - v.scale() - 1

    private fun signed(e: Int): String = if (e >= 0) "+$e" else "$e"

    private fun group(plain: String): String {
        val negative = plain.startsWith("-")
        val body = if (negative) plain.substring(1) else plain
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val rest = if (dot >= 0) body.substring(dot) else ""
        val grouped = StringBuilder()
        intPart.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) grouped.append(',')
            grouped.append(c)
        }
        return (if (negative) "-" else "") + grouped.reverse().toString() + rest
    }
}
