package com.shohankhan.axiomcalc.domain

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.TimeSource
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.constants.ConstantRegistry
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.matrices.Matrix
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Semantics that persistence depends on, tested without Android.
 *
 * Room, DataStore and WorkManager themselves need an instrumented environment
 * (see TESTING.md for what is NOT TESTED and why), but the rules those layers
 * implement are pure Kotlin and are verified here:
 *  * retention cutoff arithmetic against a fixed, injectable clock;
 *  * the canonical round-trip that makes "reuse a stored result" safe, i.e. a
 *    stored value is restored exactly and never parsed from display text;
 *  * matrix helpers the Math Lab uses;
 *  * constant symbol aliases the keypad inserts.
 */
class PersistenceSemanticsTest {

    private val now = 1_700_000_000_000L
    private val clock: TimeSource = TimeSource.fixed(now)

    // ---- retention ---------------------------------------------------------------------

    @Test fun defaultRetentionIsThirtyDays() {
        assertEquals(RetentionPolicy.THIRTY_DAYS, RetentionPolicy.DEFAULT)
    }

    @Test fun cutoffsAreExactlyOneSevenThirtyNinetyDays() {
        val day = RetentionPolicy.MILLIS_PER_DAY
        assertEquals(now - 1 * day, RetentionPolicy.ONE_DAY.cutoffMillis(now))
        assertEquals(now - 7 * day, RetentionPolicy.SEVEN_DAYS.cutoffMillis(now))
        assertEquals(now - 30 * day, RetentionPolicy.THIRTY_DAYS.cutoffMillis(now))
        assertEquals(now - 90 * day, RetentionPolicy.NINETY_DAYS.cutoffMillis(now))
    }

    @Test fun neverRetainHasNoCutoff() {
        assertNull(RetentionPolicy.NEVER.cutoffMillis(now))
        assertFalse(RetentionPolicy.NEVER.isExpired(timestampMillis = 0L, nowMillis = now))
    }

    @Test fun expiryIsInclusiveAtTheBoundary() {
        val day = RetentionPolicy.MILLIS_PER_DAY
        val policy = RetentionPolicy.SEVEN_DAYS
        val cutoff = policy.cutoffMillis(now)!!
        // Exactly at the cutoff the entry is still kept; one millisecond older
        // it is expired. The boundary must not be fuzzy.
        assertFalse(policy.isExpired(cutoff, now))
        assertTrue(policy.isExpired(cutoff - 1, now))
        assertTrue(policy.isExpired(now - 8 * day, now))
        assertFalse(policy.isExpired(now - 1 * day, now))
    }

    @Test fun retentionUsesTheInjectedClockNotTheSystemClock() {
        // A fixed clock in the distant past must not expire recent entries.
        val pastClock = TimeSource.fixed(now - 365 * RetentionPolicy.MILLIS_PER_DAY)
        assertFalse(RetentionPolicy.ONE_DAY.isExpired(now, pastClock.nowMillis()))
    }

    @Test fun retentionParsesFromNameAndDaysSafely() {
        assertEquals(RetentionPolicy.SEVEN_DAYS, RetentionPolicy.fromName("SEVEN_DAYS"))
        assertEquals(RetentionPolicy.DEFAULT, RetentionPolicy.fromName("NOT_A_POLICY"))
        assertEquals(RetentionPolicy.DEFAULT, RetentionPolicy.fromName(null))
        assertEquals(RetentionPolicy.NINETY_DAYS, RetentionPolicy.fromDays(90))
        assertEquals(RetentionPolicy.NEVER, RetentionPolicy.fromDays(null))
        assertEquals(RetentionPolicy.DEFAULT, RetentionPolicy.fromDays(13))
    }

    @Test fun retentionLabelsAreHumanReadable() {
        assertTrue(RetentionPolicy.NEVER.label.contains("Never"))
        assertTrue(RetentionPolicy.THIRTY_DAYS.label.contains("30"))
        assertTrue(RetentionPolicy.ONE_DAY.label.contains("1"))
    }

    // ---- canonical round-trip --------------------------------------------------------------

    @Test fun canonicalFormRoundTripsExactly() {
        val values = listOf("0.1", "3", "-2.5", "1000000", "0.000001", "123456789012345678901234567890")
        values.forEach { text ->
            val original = CalcNumber.parse(text)
            val restored = CalcNumber.fromCanonical(original.toCanonicalString())
            assertNotNull("could not restore $text", restored)
            assertEquals("round trip changed $text", 0, original.compareTo(restored!!))
        }
    }

    @Test fun canonicalFormSurvivesVeryLargeAndVerySmallValues() {
        val huge = ExpressionEngine.evaluate("10^80").valueOrNull()!!
        val tiny = ExpressionEngine.evaluate("10^-80").valueOrNull()!!
        assertEquals(0, huge.compareTo(CalcNumber.fromCanonical(huge.toCanonicalString())!!))
        assertEquals(0, tiny.compareTo(CalcNumber.fromCanonical(tiny.toCanonicalString())!!))
        // The canonical form stays compact; it must not expand into thousands of
        // characters of zeros when stored in a database row.
        assertTrue(huge.toCanonicalString().length < 40)
        assertTrue(tiny.toCanonicalString().length < 40)
    }

    @Test fun canonicalParsingRejectsDisplayText() {
        // A formatted display string with grouping separators must NOT restore,
        // which is exactly why the app stores a separate canonical value.
        assertNull(CalcNumber.fromCanonical("1,234,567"))
        assertNull(CalcNumber.fromCanonical("not a number"))
        assertNull(CalcNumber.fromCanonical(""))
    }

    @Test fun reusedStoredValueFeedsTheEngineExactly() {
        // Simulates: user computes 0.1+0.2, the row is stored, later reused as Ans.
        val first = ExpressionEngine.evaluate("0.1+0.2").valueOrNull()!!
        val stored = first.toCanonicalString()
        val restored = CalcNumber.fromCanonical(stored)!!
        val reused = ExpressionEngine.evaluate("ans*10", AngleMode.DEGREES, restored)
        assertTrue(reused is CalcResult.Success)
        assertEquals(0, CalcNumber.parse("3").compareTo((reused as CalcResult.Success).value))
    }

    // ---- matrix helpers used by Math Lab ------------------------------------------------------

    @Test fun traceRequiresASquareMatrix() {
        assertEquals(5.0, Matrix.parse("1, 2; 3, 4").trace(), 1e-12)
        try {
            Matrix.parse("1, 2, 3; 4, 5, 6").trace()
            org.junit.Assert.fail("expected a dimension error")
        } catch (e: CalcException) {
            assertTrue(e.error is CalcError.DimensionMismatch)
        }
    }

    @Test fun toRowsPreservesShapeAndOrder() {
        val rows = Matrix.parse("1, 2; 3, 4; 5, 6").toRows()
        assertEquals(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0), listOf(5.0, 6.0)), rows)
    }

    // ---- constant aliases inserted by the keypad --------------------------------------------------

    @Test fun typographicSymbolsResolveToConstants() {
        assertEquals(ConstantRegistry.find("pi"), ConstantRegistry.find("π"))
        assertEquals(ConstantRegistry.find("tau"), ConstantRegistry.find("τ"))
        assertEquals(ConstantRegistry.find("phi"), ConstantRegistry.find("φ"))
        assertEquals(ConstantRegistry.find("e"), ConstantRegistry.find("e"))
        assertNull(ConstantRegistry.find("not-a-constant"))
    }

    @Test fun keypadInsertedSymbolsEvaluateInExpressions() {
        // The Scientific keypad inserts π and τ directly; the engine must accept them.
        val piResult = ExpressionEngine.evaluate("2π")
        assertTrue(piResult is CalcResult.Success)
        val expected = ExpressionEngine.evaluate("2*pi").valueOrNull()!!
        assertEquals(0, expected.compareTo((piResult as CalcResult.Success).value))

        // tau/2 equals pi mathematically, but the two constants are stored to 35
        // digits and every operation is rounded to DECIMAL128 (34 significant
        // digits), so they can differ in the last digit. Assert agreement to
        // within that rounding, not bit-for-bit equality.
        val tauResult = ExpressionEngine.evaluate("τ/2")
        assertTrue(tauResult is CalcResult.Success)
        val tauHalf = (tauResult as CalcResult.Success).value
        val pi = ExpressionEngine.evaluate("pi").valueOrNull()!!
        val difference = (tauHalf - pi).abs()
        assertTrue(
            "tau/2 and pi differ by $difference, more than DECIMAL128 rounding allows",
            difference < CalcNumber.parse("1E-30"),
        )
    }

    @Test fun allRegistryConstantsAreUsableByIdentifier() {
        ConstantRegistry.all.forEach { constant ->
            val result = ExpressionEngine.evaluate(constant.id)
            assertTrue("${constant.id} did not evaluate", result is CalcResult.Success)
            assertEquals(
                0,
                constant.value.compareTo((result as CalcResult.Success).value),
            )
        }
    }
}
