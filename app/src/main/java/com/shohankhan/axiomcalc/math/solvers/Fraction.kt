package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Exact rational arithmetic on [BigInteger] numerator/denominator.
 *
 * The denominator is always kept positive and the pair always reduced, so
 * equality and comparison are structural. There is no integer overflow
 * (BigInteger), but a digit guard rail keeps hostile input bounded.
 */
data class Fraction(val numerator: BigInteger, val denominator: BigInteger) : Comparable<Fraction> {

    init {
        if (denominator.signum() == 0) {
            throw CalcException(CalcError.DivisionByZero("fraction denominator is zero"))
        }
    }

    val reduced: Fraction by lazy {
        var n = numerator
        var d = denominator
        val g = n.abs().gcd(d.abs())
        if (g.signum() > 0 && g != BigInteger.ONE) { n = n.divide(g); d = d.divide(g) }
        if (d.signum() < 0) { n = n.negate(); d = d.negate() }
        guard(n); guard(d)
        Fraction(n, d)
    }

    val isInteger: Boolean get() = reduced.denominator == BigInteger.ONE

    operator fun plus(o: Fraction): Fraction =
        Fraction(numerator.multiply(o.denominator).add(o.numerator.multiply(denominator)),
            denominator.multiply(o.denominator)).reduced

    operator fun minus(o: Fraction): Fraction = this + (-o)

    operator fun unaryMinus(): Fraction = Fraction(numerator.negate(), denominator).reduced

    operator fun times(o: Fraction): Fraction =
        Fraction(numerator.multiply(o.numerator), denominator.multiply(o.denominator)).reduced

    operator fun div(o: Fraction): Fraction {
        if (o.numerator.signum() == 0) throw CalcException(CalcError.DivisionByZero("division by a zero fraction"))
        return Fraction(numerator.multiply(o.denominator), denominator.multiply(o.numerator)).reduced
    }

    override fun compareTo(other: Fraction): Int =
        numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator))

    /** Whole part plus proper fraction, e.g. 7/2 -> 3 1/2. */
    fun toMixedString(): String {
        val r = reduced
        if (r.denominator == BigInteger.ONE) return r.numerator.toString()
        val whole = r.numerator.divide(r.denominator)
        val rest = r.numerator.remainder(r.denominator)
        return when {
            whole.signum() == 0 -> "${r.numerator}/${r.denominator}"
            rest.signum() == 0 -> whole.toString()
            else -> "$whole ${rest.abs()}/${r.denominator}"
        }
    }

    fun toDecimal(digits: Int = 12): CalcNumber =
        CalcNumber.of(BigDecimal(numerator).divide(BigDecimal(denominator), digits, RoundingMode.HALF_EVEN))

    override fun toString(): String = "${reduced.numerator}/${reduced.denominator}"

    private fun guard(v: BigInteger) {
        if (v.toString().length > Precision.MAX_INTEGER_DIGITS) {
            throw CalcException(CalcError.Overflow("fraction magnitude exceeded ${Precision.MAX_INTEGER_DIGITS} digits"))
        }
    }

    companion object {
        fun of(n: Long, d: Long = 1L): Fraction =
            Fraction(BigInteger.valueOf(n), BigInteger.valueOf(d)).reduced

        fun of(n: BigInteger, d: BigInteger): Fraction = Fraction(n, d).reduced

        /** Parses "3/4", "-3/4", "1 2/3", "5". */
        fun parse(text: String): Fraction {
            val t = text.trim().replace("−", "-")
            val mixed = Regex("^([+-]?\\d+)\\s+(\\d+)\\s*/\\s*(\\d+)$").find(t)
            if (mixed != null) {
                val (w, n, d) = mixed.destructured
                val whole = BigInteger(w)
                val frac = Fraction(BigInteger(n), BigInteger(d)).reduced
                val sign = if (whole.signum() < 0) BigInteger.ONE.negate() else BigInteger.ONE
                return (Fraction(whole.abs().multiply(frac.denominator).add(frac.numerator), frac.denominator)
                    .let { if (sign.signum() < 0) -it else it }).reduced
            }
            val simple = Regex("^([+-]?\\d+)\\s*/\\s*([+-]?\\d+)$").find(t)
            if (simple != null) {
                val (n, d) = simple.destructured
                return Fraction(BigInteger(n), BigInteger(d)).reduced
            }
            val whole = Regex("^([+-]?\\d+)$").find(t)
            if (whole != null) return Fraction(BigInteger(whole.groupValues[1]), BigInteger.ONE).reduced
            throw CalcException(CalcError.InvalidArgument("fraction", "'$text' is not a fraction literal"))
        }
    }
}
