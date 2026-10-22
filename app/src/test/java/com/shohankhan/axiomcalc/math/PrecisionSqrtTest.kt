package com.shohankhan.axiomcalc.math

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import com.shohankhan.axiomcalc.math.statistics.Statistics
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for [Precision.sqrt], the single decimal square root of the engine.
 *
 * These exist because Android lint proved that the previous implementation was
 * a crash, not a style problem: it called `java.math.BigDecimal.sqrt`, which
 * only exists on **API level 33**. With minSdk 24 that is a `NoSuchMethodError`
 * on every Android 7.0 to 12L device, reachable from the Standard calculator,
 * from the statistics module and from thirteen geometry formulas. Every test in
 * the JVM suite passed while that was true, because the JVM has the method.
 *
 * The replacement is Newton-Raphson on [BigDecimal]. Its contract is verified
 * three ways: against independently computed 34-digit reference values, against
 * the defining property `r^2 == n` at working precision, and through the two
 * callers that used to reach the platform method.
 */
class PrecisionSqrtTest {

    /** 34 significant digits, HALF_EVEN - computed independently of this code. */
    private fun sqrt(value: String): BigDecimal = Precision.sqrt(BigDecimal(value))

    private fun assertSqrtEquals(expected: String, actual: BigDecimal) {
        assertEquals(
            "sqrt should equal $expected but was $actual",
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    @Test
    fun zeroAndPerfectSquaresAreExact() {
        assertEquals(0, BigDecimal.ZERO.compareTo(Precision.sqrt(BigDecimal.ZERO)))
        assertSqrtEquals("1", sqrt("1"))
        assertSqrtEquals("2", sqrt("4"))
        assertSqrtEquals("3", sqrt("9"))
        assertSqrtEquals("4", sqrt("16"))
        assertSqrtEquals("9", sqrt("81"))
        assertSqrtEquals("10", sqrt("100"))
        assertSqrtEquals("0.5", sqrt("0.25"))
        assertSqrtEquals("10000000000", sqrt("100000000000000000000"))
        // A large exact integer root. The argument is derived from the expected
        // root rather than written out, so this assertion cannot drift: an
        // earlier version of this test hardcoded a wrong square and the
        // implementation was right.
        val largeRoot = BigInteger.valueOf(123_456_789L)
        assertSqrtEquals(
            largeRoot.toString(),
            Precision.sqrt(BigDecimal(largeRoot.pow(2))),
        )
        val biggerRoot = BigInteger.valueOf(9_876_543_210_123L)
        assertSqrtEquals(
            biggerRoot.toString(),
            Precision.sqrt(BigDecimal(biggerRoot.pow(2))),
        )
    }

    @Test
    fun irrationalRootsMatchReferenceDigits() {
        assertSqrtEquals("1.414213562373095048801688724209698", sqrt("2"))
        assertSqrtEquals("1.732050807568877293527446341505872", sqrt("3"))
        assertSqrtEquals("2.645751311064590590501615753639260", sqrt("7"))
        assertSqrtEquals("3.162277660168379331998893544432719", sqrt("10"))
        assertSqrtEquals("0.3162277660168379331998893544432719", sqrt("0.1"))
        assertSqrtEquals("0.7071067811865475244008443621048490", sqrt("0.5"))
        assertSqrtEquals("3513641828.820144253093654172555782", sqrt("12345678901234567890"))
        assertSqrtEquals(
            "999999999999.9999999999995000000000",
            sqrt("999999999999999999999999"),
        )
    }

    @Test
    fun resultSquaredReproducesTheArgument() {
        // The defining property, checked far beyond the hand-written references
        // above: r*r must agree with n to well inside the working precision.
        val samples = listOf(
            "2", "3", "5", "6", "7", "8", "10", "11", "13", "17", "19", "23",
            "0.1", "0.2", "0.3", "0.7", "1.5", "2.718281828459045",
            "3.14159265358979323846", "12345.6789", "9876543210.123456789",
            "1e20", "1e-20", "7.5e33", "3.25e-17",
        )
        val tolerance = BigDecimal("1e-28")
        for (sample in samples) {
            val n = BigDecimal(sample)
            val r = Precision.sqrt(n)
            val residual = r.multiply(r).subtract(n).abs()
            val relative = residual.divide(n.abs(), Precision.MC)
            assertTrue(
                "sqrt($sample)^2 differs from $sample by $residual (relative $relative)",
                relative.compareTo(tolerance) <= 0,
            )
        }
    }

    @Test
    fun veryLargeAndVerySmallMagnitudesStillConverge() {
        // The seed is derived from the decimal exponent, so the iteration count
        // does not grow with the magnitude. If it did, the iteration cap would
        // trip and these would fail with a ResourceLimit instead of a root.
        val huge = BigDecimal(BigInteger.ONE, -100_000)          // 1e100000
        val hugeRoot = Precision.sqrt(huge)
        assertEquals(0, BigDecimal(BigInteger.ONE, -50_000).compareTo(hugeRoot))

        val tiny = BigDecimal(BigInteger.ONE, 100_000)           // 1e-100000
        val tinyRoot = Precision.sqrt(tiny)
        assertEquals(0, BigDecimal(BigInteger.ONE, 50_000).compareTo(tinyRoot))
    }

    @Test
    fun rootIsMonotonicAndNeverNegative() {
        var previous = Precision.sqrt(BigDecimal.ZERO)
        assertEquals(0, BigDecimal.ZERO.compareTo(previous))
        var x = BigDecimal("0.001")
        val step = BigDecimal("1.3719")
        repeat(40) {
            val current = Precision.sqrt(x)
            assertTrue("sqrt must increase with x at x=$x", current > previous)
            assertTrue("sqrt must never be negative at x=$x", current.signum() > 0)
            previous = current
            x = x.multiply(step).add(BigDecimal("0.013"))
        }
    }

    @Test
    fun negativeArgumentIsATypedDomainError() {
        // Never NaN, never a plausible-looking zero, never a raw exception.
        try {
            Precision.sqrt(BigDecimal("-4"))
            fail("sqrt(-4) must not succeed")
        } catch (e: CalcException) {
            assertTrue("expected a Domain error but got ${e.error}", e.error is CalcError.Domain)
        }
    }

    @Test
    fun engineSqrtUsesThePlatformIndependentRoot() {
        assertEquals(
            0,
            BigDecimal("9").compareTo(evaluate("sqrt(81)").value),
        )
        assertSqrtEquals("1.414213562373095048801688724209698", evaluate("sqrt(2)").value)
        assertSqrtEquals("0.5", evaluate("sqrt(0.25)").value)
    }

    @Test
    fun engineReportsDomainForSqrtOfNegative() {
        val result = ExpressionEngine.evaluate("sqrt(-4)", AngleMode.DEGREES, CalcNumber.ZERO)
        assertTrue("sqrt(-4) must fail", result is CalcResult.Failure)
        val error = (result as CalcResult.Failure).error
        assertTrue("expected a Domain error but got $error", error is CalcError.Domain)
    }

    @Test
    fun standardDeviationUsesThePlatformIndependentRoot() {
        val sample = listOf(2, 4, 4, 4, 5, 5, 7, 9).map { CalcNumber.of(it) }
        // Population: mean 5, sum of squares 32, variance 32/8 = 4, sd = 2.
        assertEquals(
            "population sd of 2,4,4,4,5,5,7,9",
            0,
            BigDecimal("2").compareTo(Statistics.populationStdDev(sample).value),
        )
        // Sample: variance 32/7, sd = 2.138089935299395077476427847038028.
        assertSqrtEquals(
            "2.138089935299395077476427847038028",
            Statistics.sampleStdDev(sample).value,
        )
    }

    private fun evaluate(source: String): CalcNumber =
        when (val r = ExpressionEngine.evaluate(source, AngleMode.DEGREES, CalcNumber.ZERO)) {
            is CalcResult.Success -> r.value
            is CalcResult.Failure ->
                throw AssertionError("expected success for '$source' but got ${r.error.code}")
        }
}
