package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * Arithmetic and geometric sequences.
 *
 * Conventions:
 *  * terms are 1-indexed: term(1) is the first term a1.
 *  * n must be a positive integer; n = 0 sums are 0 by definition.
 *  * geometric sums use the closed form a1 (1 - r^n) / (1 - r); r = 1 is handled
 *    explicitly as a1 * n because the closed form is 0/0 there.
 */
object Sequences {

    private fun positiveIndex(n: CalcNumber, label: String): Int {
        val bi = n.toBigIntegerOrNull()
            ?: throw CalcException(CalcError.InvalidArgument(label, "$n is not an integer"))
        if (bi.signum() < 0) throw CalcException(CalcError.Domain(label, "$label = $n must be >= 0"))
        if (bi.bitLength() > 31) throw CalcException(CalcError.ResourceLimit(label, "$n is too large"))
        return bi.toInt()
    }

    data class ArithmeticResult(
        val firstTerm: CalcNumber,
        val commonDifference: CalcNumber,
        val termIndex: Int,
        val nthTerm: CalcNumber,
        val sumToN: CalcNumber,
    )

    fun arithmetic(a1: CalcNumber, d: CalcNumber, n: CalcNumber): ArithmeticResult {
        val ni = positiveIndex(n, "n")
        val nth = a1 + d * CalcNumber.of(ni - 1)
        val sum = (a1 + nth) * CalcNumber.of(ni) / CalcNumber.TWO
        return ArithmeticResult(a1, d, ni, nth, sum)
    }

    /** Derives (a1, d) from two known terms at 1-indexed positions p and q. */
    fun arithmeticFromTwoTerms(tp: CalcNumber, p: Int, tq: CalcNumber, q: Int): Pair<CalcNumber, CalcNumber> {
        if (p == q) throw CalcException(CalcError.InvalidArgument("positions", "the two positions must differ"))
        if (p < 1 || q < 1) throw CalcException(CalcError.Domain("positions", "term positions are 1-indexed"))
        val d = (tq - tp) / CalcNumber.of(q - p)
        val a1 = tp - d * CalcNumber.of(p - 1)
        return a1 to d
    }

    data class GeometricResult(
        val firstTerm: CalcNumber,
        val commonRatio: CalcNumber,
        val termIndex: Int,
        val nthTerm: CalcNumber,
        val sumToN: CalcNumber,
    )

    fun geometric(a1: CalcNumber, r: CalcNumber, n: CalcNumber): GeometricResult {
        val ni = positiveIndex(n, "n")
        val nth = a1 * powerInt(r, ni - 1)
        val sum = if (r == CalcNumber.ONE) {
            a1 * CalcNumber.of(ni)
        } else {
            a1 * (CalcNumber.ONE - powerInt(r, ni)) / (CalcNumber.ONE - r)
        }
        return GeometricResult(a1, r, ni, nth, sum)
    }

    /** Infinite geometric sum; converges only for |r| < 1. */
    fun geometricInfinite(a1: CalcNumber, r: CalcNumber): CalcNumber {
        val rd = r.toDouble()
        if (rd <= -1.0 || rd >= 1.0) {
            throw CalcException(
                CalcError.NonConvergence(0, rd, "the infinite geometric series diverges for |r| >= 1 (r = $r)"),
            )
        }
        return a1 / (CalcNumber.ONE - r)
    }

    fun powerInt(base: CalcNumber, exponent: Int): CalcNumber =
        if (exponent == 0) CalcNumber.ONE
        else CalcNumber.of(base.value.pow(exponent, com.shohankhan.axiomcalc.math.precision.Precision.MC))
}
