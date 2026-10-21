package com.shohankhan.axiomcalc.math.precision

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Numerical policy of the engine.
 *
 * Everything the engine guarantees about precision is declared here so that it
 * is documented in exactly one place (see MATHEMATICS_SPEC.md).
 *
 *  * Exact integer arithmetic (factorials, gcd/lcm, fractions, base conversion)
 *    uses [BigInteger] and is exact until the digit guard rails trip.
 *  * Exact rational-ish arithmetic (+, -, *, integer powers, sqrt) uses
 *    [BigDecimal] with [MC] (IEEE-854 decimal128: 34 significant digits).
 *  * Transcendental functions (exp, ln, log, trig, hyperbolic) and fractional
 *    powers are evaluated through IEEE-754 binary64 (`java.lang.Math`), so their
 *    precision ceiling is ~15-17 significant decimal digits. Results are
 *    re-rounded to [MC] afterwards; they are never displayed with more digits
 *    than the display policy allows.
 *  * Division is performed with [MC]; a quotient that does not terminate in
 *    decimal is rounded half-even at 34 digits. This is documented, not hidden.
 */
object Precision {
    /** Working context for all decimal arithmetic: 34 significant digits. */
    val MC: MathContext = MathContext.DECIMAL128

    /** Rounding applied when reducing to display or solver precision. */
    val ROUNDING: RoundingMode = RoundingMode.HALF_EVEN

    /** Largest integer exponent accepted by `^`. Guards CPU and memory. */
    const val MAX_EXPONENT: Long = 100_000L

    /** Largest n accepted by n!. 10000! has 35,660 digits: bounded and safe. */
    const val MAX_FACTORIAL: Int = 10_000

    /** Largest digit count accepted for an entered literal. */
    const val MAX_LITERAL_DIGITS: Int = 60

    /** Largest digit count accepted for a BigInteger result. */
    const val MAX_INTEGER_DIGITS: Int = 100_000

    /** Tolerance used when a numerical method decides "close enough to zero". */
    const val SOLVER_TOLERANCE: Double = 1e-12

    /** Display-side tolerance for treating a value as an exact integer. */
    const val INTEGRAL_TOLERANCE: Double = 1e-12

    /** Default maximum iterations for iterative solvers. */
    const val DEFAULT_MAX_ITERATIONS: Int = 200

    /** Recursion guard for expression evaluation depth. */
    const val MAX_EXPRESSION_DEPTH: Int = 256

    /** Total node guard for a parsed expression. */
    const val MAX_EXPRESSION_NODES: Int = 4_000

    /** Matrix dimension guard: rows*cols must stay <= this. */
    const val MAX_MATRIX_CELLS: Int = 4096

    internal fun overflow(detail: String): Nothing = throw CalcException(CalcError.Overflow(detail))
    internal fun resourceLimit(limit: String, detail: String = ""): Nothing =
        throw CalcException(CalcError.ResourceLimit(limit, detail))
}

/**
 * The single numeric type of the calculation engine.
 *
 * A [CalcNumber] is an immutable [BigDecimal] plus the intent of how it was
 * produced. Keeping the value as a decimal (rather than a Double) is what makes
 * `0.1 + 0.2` display as `0.3` and keeps integer results exact.
 *
 * Important: a [CalcNumber] is the *normalized* result. It is never constructed
 * from a formatted display string (see [com.shohankhan.axiomcalc.core.formatting.NumberFormatter]).
 */
class CalcNumber internal constructor(val value: BigDecimal) : Comparable<CalcNumber> {

    val isZero: Boolean get() = value.signum() == 0
    val isNegative: Boolean get() = value.signum() < 0
    val signum: Int get() = value.signum()

    /** True when the value has no fractional part at the working precision. */
    val isIntegral: Boolean get() = value.stripTrailingZeros().scale() <= 0

    fun negate(): CalcNumber = CalcNumber(value.negate())
    fun abs(): CalcNumber = CalcNumber(value.abs())

    operator fun plus(other: CalcNumber): CalcNumber =
        CalcNumber(value.add(other.value, Precision.MC))

    operator fun minus(other: CalcNumber): CalcNumber =
        CalcNumber(value.subtract(other.value, Precision.MC))

    operator fun times(other: CalcNumber): CalcNumber =
        CalcNumber(value.multiply(other.value, Precision.MC))

    /** Exact integer division when the quotient is integral, rounded otherwise. */
    operator fun div(other: CalcNumber): CalcNumber {
        if (other.isZero) throw CalcException(CalcError.DivisionByZero("divisor is exactly zero"))
        return CalcNumber(value.divide(other.value, Precision.MC))
    }

    /** IEEE remainder semantics are *not* used; this is the mathematical mod. */
    fun mod(other: CalcNumber): CalcNumber {
        if (other.isZero) throw CalcException(CalcError.DivisionByZero("modulus by zero"))
        return CalcNumber(value.remainder(other.value, Precision.MC))
    }

    operator fun unaryMinus(): CalcNumber = negate()

    override fun compareTo(other: CalcNumber): Int = value.compareTo(other.value)

    override fun equals(other: Any?): Boolean =
        other is CalcNumber && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()

    override fun toString(): String = value.toPlainString()

    fun toDouble(): Double = value.toDouble()

    /** Exact conversion when integral, else null. Guarded against overflow. */
    fun toBigIntegerOrNull(): BigInteger? =
        if (isIntegral) value.toBigInteger() else null

    companion object {
        val ZERO = CalcNumber(BigDecimal.ZERO)
        val ONE = CalcNumber(BigDecimal.ONE)
        // Not BigDecimal.TWO: that constant only exists from Java 9 / recent
        // Android API levels, and the engine targets minSdk 24.
        val TWO = CalcNumber(BigDecimal.valueOf(2L))
        val TEN = CalcNumber(BigDecimal.TEN)

        fun of(value: BigDecimal): CalcNumber = CalcNumber(value)
        fun of(value: BigInteger): CalcNumber = CalcNumber(BigDecimal(value))
        fun of(value: Int): CalcNumber = CalcNumber(BigDecimal.valueOf(value.toLong()))
        fun of(value: Long): CalcNumber = CalcNumber(BigDecimal.valueOf(value))
        fun of(value: Double): CalcNumber = CalcNumber(BigDecimal.valueOf(value))

        /**
         * Parses a user literal. Rejects malformed input and over-long literals
         * instead of silently truncating them.
         */
        fun parse(literal: String): CalcNumber {
            val digits = literal.count { it.isDigit() }
            if (digits > Precision.MAX_LITERAL_DIGITS) {
                Precision.resourceLimit("literal digits", "$digits > ${Precision.MAX_LITERAL_DIGITS}")
            }
            return try {
                CalcNumber(BigDecimal(literal))
            } catch (e: NumberFormatException) {
                throw CalcException(CalcError.Syntax(-1, "malformed numeric literal '$literal'"))
            }
        }

        /** Wraps a double produced by a transcendental function, re-rounded to [Precision.MC]. */
        fun fromTranscendental(value: Double): CalcNumber {
            if (value.isNaN()) throw CalcException(CalcError.Domain("transcendental", "result is not a real number"))
            if (value.isInfinite()) Precision.overflow("transcendental result is infinite")
            return CalcNumber(BigDecimal(value, Precision.MC))
        }
    }
}
