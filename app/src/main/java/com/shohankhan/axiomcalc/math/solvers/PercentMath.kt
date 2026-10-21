package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import java.math.BigInteger

/**
 * Percentage, discount, tax, margin and ratio mathematics.
 *
 * Every function here is exact decimal arithmetic. Denominator guards raise
 * [CalcError.DivisionByZero] or [CalcError.InvalidArgument]; nothing silently
 * returns zero.
 *
 * Margin versus markup is kept explicit and is explained in the UI:
 *  * **markup**  = (selling - cost) / cost   (denominator: cost)
 *  * **margin**  = (selling - cost) / selling (denominator: selling)
 */
object PercentMath {

    private val HUNDRED = CalcNumber.of(100)

    private fun requireNonZero(v: CalcNumber, name: String): CalcNumber {
        if (v.isZero) throw CalcException(CalcError.DivisionByZero("$name is zero"))
        return v
    }

    // ---- percentage core ----------------------------------------------------

    fun percentOf(value: CalcNumber, percent: CalcNumber): CalcNumber = value * percent / HUNDRED

    fun whatPercent(part: CalcNumber, whole: CalcNumber): CalcNumber =
        part / requireNonZero(whole, "whole") * HUNDRED

    fun percentageIncrease(from: CalcNumber, to: CalcNumber): CalcNumber =
        (to - from) / requireNonZero(from, "original value") * HUNDRED

    fun percentageDecrease(from: CalcNumber, to: CalcNumber): CalcNumber =
        (from - to) / requireNonZero(from, "original value") * HUNDRED

    /** Signed change relative to |from|, so a fall is negative. */
    fun percentageChange(from: CalcNumber, to: CalcNumber): CalcNumber =
        (to - from) / requireNonZero(from, "original value").abs() * HUNDRED

    /** Pure difference in percentage points, e.g. 18% to 20% is +2 points. */
    fun percentagePoints(from: CalcNumber, to: CalcNumber): CalcNumber = to - from

    fun applyIncrease(value: CalcNumber, percent: CalcNumber): CalcNumber =
        value * (CalcNumber.ONE + percent / HUNDRED)

    fun applyDecrease(value: CalcNumber, percent: CalcNumber): CalcNumber =
        value * (CalcNumber.ONE - percent / HUNDRED)

    /** Recovers the original before an increase: final / (1 + p/100). */
    fun originalBeforeIncrease(final: CalcNumber, percent: CalcNumber): CalcNumber {
        val factor = CalcNumber.ONE + percent / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("factor 1 + p/100 is zero at p = -100"))
        return final / factor
    }

    /** Recovers the original before a decrease: final / (1 - p/100). */
    fun originalBeforeDecrease(final: CalcNumber, percent: CalcNumber): CalcNumber {
        val factor = CalcNumber.ONE - percent / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("factor 1 - p/100 is zero at p = 100"))
        return final / factor
    }

    // ---- discounts ----------------------------------------------------------

    fun discountAmount(price: CalcNumber, percent: CalcNumber): CalcNumber = percentOf(price, percent)

    fun discountedPrice(price: CalcNumber, percent: CalcNumber): CalcNumber = applyDecrease(price, percent)

    fun discountPercentage(original: CalcNumber, final: CalcNumber): CalcNumber =
        percentageDecrease(original, final)

    fun originalPrice(final: CalcNumber, percent: CalcNumber): CalcNumber =
        originalBeforeDecrease(final, percent)

    /**
     * Successive discounts compound multiplicatively; they are **not** added.
     * 10% then 20% is 28% effective, not 30%.
     */
    fun successiveDiscounts(price: CalcNumber, percents: List<CalcNumber>): SuccessiveDiscounts {
        if (percents.isEmpty()) throw CalcException(CalcError.EmptyDataset("no discount percentages supplied"))
        var factor = CalcNumber.ONE
        for (p in percents) {
            if (p > HUNDRED) throw CalcException(CalcError.InvalidArgument("discount", "discount $p% exceeds 100%"))
            factor = factor * (CalcNumber.ONE - p / HUNDRED)
        }
        val final = price * factor
        val effective = (CalcNumber.ONE - factor) * HUNDRED
        return SuccessiveDiscounts(final, effective, factor)
    }

    data class SuccessiveDiscounts(
        val finalPrice: CalcNumber,
        val effectiveDiscountPercent: CalcNumber,
        val retainedFactor: CalcNumber,
    )

    // ---- tax (user supplied rate; never a hardcoded legal rate) -------------

    fun taxAdd(base: CalcNumber, ratePercent: CalcNumber): TaxBreakdown {
        val tax = percentOf(base, ratePercent)
        return TaxBreakdown(base, tax, base + tax, ratePercent, inclusive = false)
    }

    /** Extracts a tax that is already contained in [total]. */
    fun taxExtract(total: CalcNumber, ratePercent: CalcNumber): TaxBreakdown {
        val factor = CalcNumber.ONE + ratePercent / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("factor 1 + rate/100 is zero"))
        val base = total / factor
        return TaxBreakdown(base, total - base, total, ratePercent, inclusive = true)
    }

    data class TaxBreakdown(
        val base: CalcNumber,
        val tax: CalcNumber,
        val total: CalcNumber,
        val ratePercent: CalcNumber,
        val inclusive: Boolean,
    )

    // ---- profit, loss, markup, margin ---------------------------------------

    fun profit(cost: CalcNumber, selling: CalcNumber): CalcNumber = selling - cost

    fun profitPercentOnCost(cost: CalcNumber, selling: CalcNumber): CalcNumber =
        (selling - cost) / requireNonZero(cost, "cost price") * HUNDRED

    fun lossPercentOnCost(cost: CalcNumber, selling: CalcNumber): CalcNumber =
        (cost - selling) / requireNonZero(cost, "cost price") * HUNDRED

    fun markupPercent(cost: CalcNumber, selling: CalcNumber): CalcNumber =
        (selling - cost) / requireNonZero(cost, "cost price") * HUNDRED

    fun marginPercent(cost: CalcNumber, selling: CalcNumber): CalcNumber =
        (selling - cost) / requireNonZero(selling, "selling price") * HUNDRED

    fun sellingFromCostAndMarkup(cost: CalcNumber, markupPercent: CalcNumber): CalcNumber =
        cost * (CalcNumber.ONE + markupPercent / HUNDRED)

    fun sellingFromCostAndMargin(cost: CalcNumber, marginPercent: CalcNumber): CalcNumber {
        val factor = CalcNumber.ONE - marginPercent / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("a 100% margin is undefined"))
        if (factor.isNegative) throw CalcException(CalcError.InvalidArgument("margin", "margin above 100% is impossible"))
        return cost / factor
    }

    fun costFromSellingAndMargin(selling: CalcNumber, marginPercent: CalcNumber): CalcNumber =
        selling * (CalcNumber.ONE - marginPercent / HUNDRED)

    fun costFromSellingAndMarkup(selling: CalcNumber, markupPercent: CalcNumber): CalcNumber {
        val factor = CalcNumber.ONE + markupPercent / HUNDRED
        if (factor.isZero) throw CalcException(CalcError.DivisionByZero("factor 1 + markup/100 is zero"))
        return selling / factor
    }

    // ---- ratio and proportion ----------------------------------------------

    fun simplifyRatio(a: BigInteger, b: BigInteger): Pair<BigInteger, BigInteger> {
        if (a.signum() == 0 && b.signum() == 0) {
            throw CalcException(CalcError.InvalidArgument("ratio", "0 : 0 is not a ratio"))
        }
        val g = a.abs().gcd(b.abs())
        if (g.signum() == 0) return a to b
        return a.divide(g) to b.divide(g)
    }

    fun equivalentRatio(a: BigInteger, b: BigInteger, factor: BigInteger): Pair<BigInteger, BigInteger> {
        if (factor.signum() == 0) throw CalcException(CalcError.InvalidArgument("factor", "scaling by 0 destroys the ratio"))
        return a.multiply(factor) to b.multiply(factor)
    }

    /** a : b == c : x  ->  x = b c / a */
    fun directProportion(a: CalcNumber, b: CalcNumber, c: CalcNumber): CalcNumber =
        b * c / requireNonZero(a, "first term")

    /** a * b == c * x  ->  x = a b / c (inverse proportion) */
    fun inverseProportion(a: CalcNumber, b: CalcNumber, c: CalcNumber): CalcNumber =
        a * b / requireNonZero(c, "third term")

    fun compareRatios(a1: CalcNumber, b1: CalcNumber, a2: CalcNumber, b2: CalcNumber): Int {
        requireNonZero(b1, "second term"); requireNonZero(b2, "fourth term")
        return (a1 / b1).compareTo(a2 / b2)
    }
}
