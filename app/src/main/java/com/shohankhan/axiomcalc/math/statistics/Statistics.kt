package com.shohankhan.axiomcalc.math.statistics

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import java.math.BigDecimal

/**
 * Descriptive statistics over exact decimal data.
 *
 * Definitions (documented in MATHEMATICS_SPEC.md and shown in the UI):
 *
 *  * **Population** variance divides by N: the dataset is treated as the whole
 *    population.
 *  * **Sample** variance divides by N-1 (Bessel's correction): the dataset is
 *    treated as a sample of a larger population. Requires N >= 2, otherwise
 *    [CalcError.InsufficientSample] is raised rather than returning NaN.
 *  * Median of an even-sized set is the mean of the two central values.
 *  * Mode returns *every* value sharing the maximum frequency; a dataset where
 *    all values are distinct is reported as having no mode (empty list), which
 *    is the convention stated in the Formula Library.
 *  * Range is max - min.
 */
object Statistics {

    private fun requireNotEmpty(values: List<CalcNumber>): List<CalcNumber> {
        if (values.isEmpty()) throw CalcException(CalcError.EmptyDataset())
        return values
    }

    fun count(values: List<CalcNumber>): Int = values.size

    fun sum(values: List<CalcNumber>): CalcNumber =
        requireNotEmpty(values).fold(CalcNumber.ZERO) { acc, v -> acc + v }

    fun mean(values: List<CalcNumber>): CalcNumber {
        val v = requireNotEmpty(values)
        return sum(v) / CalcNumber.of(v.size)
    }

    fun weightedMean(values: List<CalcNumber>, weights: List<CalcNumber>): CalcNumber {
        if (values.isEmpty()) throw CalcException(CalcError.EmptyDataset())
        if (values.size != weights.size) {
            throw CalcException(
                CalcError.DimensionMismatch("${values.size} values but ${weights.size} weights"),
            )
        }
        val weightSum = weights.fold(CalcNumber.ZERO) { acc, w -> acc + w }
        if (weightSum.isZero) {
            throw CalcException(CalcError.InvalidArgument("weights", "the weights sum to zero"))
        }
        val acc = values.zip(weights).fold(CalcNumber.ZERO) { a, (v, w) -> a + v * w }
        return acc / weightSum
    }

    fun sorted(values: List<CalcNumber>): List<CalcNumber> =
        requireNotEmpty(values).sortedBy { it.value }

    fun min(values: List<CalcNumber>): CalcNumber = sorted(values).first()
    fun max(values: List<CalcNumber>): CalcNumber = sorted(values).last()

    fun median(values: List<CalcNumber>): CalcNumber {
        val s = sorted(values)
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / CalcNumber.TWO
    }

    /** Every value sharing the maximum frequency; empty when all are distinct. */
    fun mode(values: List<CalcNumber>): List<CalcNumber> {
        val v = requireNotEmpty(values)
        val counts = LinkedHashMap<BigDecimal, Int>()
        val first = LinkedHashMap<BigDecimal, CalcNumber>()
        for (x in v) {
            val key = x.value.stripTrailingZeros()
            counts[key] = (counts[key] ?: 0) + 1
            first.putIfAbsent(key, x)
        }
        val maxCount = counts.values.max()
        if (maxCount == 1) return emptyList()
        return counts.filterValues { it == maxCount }.keys.map { first[it]!! }
    }

    fun range(values: List<CalcNumber>): CalcNumber = max(values) - min(values)

    fun populationVariance(values: List<CalcNumber>): CalcNumber = variance(values, sample = false)
    fun sampleVariance(values: List<CalcNumber>): CalcNumber = variance(values, sample = true)

    fun populationStdDev(values: List<CalcNumber>): CalcNumber =
        sqrtDecimal(populationVariance(values))

    fun sampleStdDev(values: List<CalcNumber>): CalcNumber =
        sqrtDecimal(sampleVariance(values))

    private fun variance(values: List<CalcNumber>, sample: Boolean): CalcNumber {
        val v = requireNotEmpty(values)
        if (sample && v.size < 2) {
            throw CalcException(CalcError.InsufficientSample(required = 2, actual = v.size))
        }
        val m = mean(v)
        val ss = v.fold(CalcNumber.ZERO) { acc, x -> val d = x - m; acc + d * d }
        val divisor = if (sample) v.size - 1 else v.size
        return ss / CalcNumber.of(divisor)
    }

    private fun sqrtDecimal(x: CalcNumber): CalcNumber =
        CalcNumber.of(x.value.sqrt(Precision.MC))

    /** A complete summary block, used by the Math Lab statistics panel. */
    fun summarize(values: List<CalcNumber>): Summary {
        val v = requireNotEmpty(values)
        return Summary(
            count = v.size,
            sum = sum(v),
            mean = mean(v),
            median = median(v),
            modes = mode(v),
            min = min(v),
            max = max(v),
            range = range(v),
            populationVariance = populationVariance(v),
            sampleVariance = if (v.size >= 2) sampleVariance(v) else null,
            populationStdDev = populationStdDev(v),
            sampleStdDev = if (v.size >= 2) sampleStdDev(v) else null,
        )
    }

    data class Summary(
        val count: Int,
        val sum: CalcNumber,
        val mean: CalcNumber,
        val median: CalcNumber,
        val modes: List<CalcNumber>,
        val min: CalcNumber,
        val max: CalcNumber,
        val range: CalcNumber,
        val populationVariance: CalcNumber,
        val sampleVariance: CalcNumber?,
        val populationStdDev: CalcNumber,
        val sampleStdDev: CalcNumber?,
    )
}
