package com.shohankhan.axiomcalc.domain

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.domain.calculator.CalculatorBuffer
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interaction contract of the keypad calculator: editing policy, undo/redo,
 * memory independence and the documented percentage-key semantics.
 */
class CalculatorBehaviorTest {

    private fun buffer(): CalculatorBuffer = CalculatorBuffer()

    private fun type(vararg keys: (CalculatorBuffer) -> Unit): CalculatorBuffer {
        val b = buffer()
        keys.forEach { it(b) }
        return b
    }

    private fun digits(b: CalculatorBuffer, text: String) {
        text.forEach { ch -> if (ch == '.') b.pressDecimalPoint() else b.pressDigit(ch) }
    }

    private fun value(b: CalculatorBuffer): Double =
        (b.evaluate(AngleMode.DEGREES, CalcNumber.ZERO) as CalcResult.Success).value.toDouble()

    // ---- basic keypad -----------------------------------------------------------

    @Test fun simpleArithmetic() {
        val b = type({ digits(it, "12") }, { it.pressOperator("+") }, { digits(it, "30") })
        assertEquals(42.0, value(b), 0.0)
    }

    @Test fun operatorReplacementPreventsDoubleOperation() {
        val b = type({ digits(it, "5") }, { it.pressOperator("+") }, { it.pressOperator("-") }, { digits(it, "2") })
        assertEquals("5-2", b.expressionText())
        assertEquals(3.0, value(b), 0.0)
    }

    @Test fun backspaceRemovesWholeFunctionToken() {
        val b = type({ it.pressFunction("sin(") }, { digits(it, "30") }, { it.pressCloseParen() })
        assertEquals("sin(30)", b.expressionText())
        b.pressBackspace()
        assertEquals("sin(30", b.expressionText())
        b.pressBackspace()
        assertEquals("sin(", b.expressionText())
        b.pressBackspace()
        assertEquals("", b.expressionText())
    }

    @Test fun clearEntryKeepsTheRestOfTheExpression() {
        val b = type({ digits(it, "12") }, { it.pressOperator("+") }, { digits(it, "99") })
        b.pressClearEntry()
        assertEquals("12+", b.expressionText())
    }

    @Test fun allClearEmptiesButKeepsMemory() {
        val b = type({ digits(it, "7") })
        b.memoryStore(CalcNumber.of(99))
        b.pressAllClear()
        assertEquals("", b.expressionText())
        assertTrue(b.memoryOccupied)
        assertEquals(99.0, b.memoryRecall()!!.toDouble(), 0.0)
    }

    @Test fun undoAndRedoRestoreExactlyOneKeyPress() {
        val b = type({ digits(it, "12") }, { it.pressOperator("+") }, { digits(it, "3") })
        assertEquals("12+3", b.expressionText())
        assertTrue(b.undo())
        assertEquals("12+", b.expressionText())
        assertTrue(b.undo())
        assertEquals("12", b.expressionText())
        assertTrue(b.redo())
        assertEquals("12+", b.expressionText())
        assertTrue(b.redo())
        assertEquals("12+3", b.expressionText())
        assertFalse(b.redo())
    }

    @Test fun signToggleActsOnCurrentEntry() {
        val b = type({ digits(it, "5") }, { it.pressOperator("-") }, { digits(it, "3") }, { it.pressSignToggle() })
        assertEquals("5--3", b.expressionText())
        assertEquals(8.0, value(b), 0.0)
        b.pressSignToggle()
        assertEquals(2.0, value(b), 0.0)
    }

    @Test fun signToggleAtStartCreatesUnaryMinus() {
        val b = type({ it.pressSignToggle() }, { digits(it, "4") })
        assertEquals(-4.0, value(b), 0.0)
    }

    @Test fun closeParenIsIgnoredWhenUnbalanced() {
        val b = type({ digits(it, "2") }, { it.pressCloseParen() })
        assertEquals("2", b.expressionText())
    }

    @Test fun missingParenthesesAreSuppliedAtEvaluationOnly() {
        val b = type({ it.pressFunction("sqrt(") }, { digits(it, "16") })
        assertEquals("sqrt(16", b.expressionText())
        assertEquals(4.0, value(b), 0.0)
    }

    @Test fun emptyBufferIsIncompleteNotError() {
        val b = buffer()
        val r = b.evaluate(AngleMode.DEGREES, CalcNumber.ZERO)
        assertTrue(r is CalcResult.Failure && (r as CalcResult.Failure).error is CalcError.Incomplete)
    }

    // ---- memory semantics ----------------------------------------------------------

    @Test fun memoryAddAndSubtract() {
        val b = buffer()
        b.memoryAdd(CalcNumber.of(10))
        b.memoryAdd(CalcNumber.of(5))
        assertEquals(15.0, b.memoryRecall()!!.toDouble(), 0.0)
        b.memorySubtract(CalcNumber.of(3))
        assertEquals(12.0, b.memoryRecall()!!.toDouble(), 0.0)
        b.memoryClear()
        assertNull(b.memoryRecall())
    }

    @Test fun memoryRecallInsertsLiteral() {
        val b = buffer()
        b.memoryStore(CalcNumber.of(25))
        b.pressMemoryRecall()
        b.pressOperator("+")
        digits(b, "5")
        assertEquals(30.0, value(b), 0.0)
    }

    // ---- percentage key semantics (documented policy) -------------------------------

    @Test fun multiplicativePercent() {
        val b = type({ digits(it, "200") }, { it.pressOperator("*") }, { digits(it, "15") }, { it.pressPercent() })
        assertEquals(30.0, value(b), 0.0)
    }

    @Test fun additivePercentUsesLeftOperand() {
        val b = type({ digits(it, "200") }, { it.pressOperator("+") }, { digits(it, "10") }, { it.pressPercent() })
        assertEquals(220.0, value(b), 0.0)
    }

    @Test fun additivePercentSubtraction() {
        val b = type({ digits(it, "200") }, { it.pressOperator("-") }, { digits(it, "10") }, { it.pressPercent() })
        assertEquals(180.0, value(b), 0.0)
    }

    @Test fun standalonePercentIsDivideByHundred() {
        val b = type({ digits(it, "15") }, { it.pressPercent() })
        assertEquals(0.15, value(b), 1e-15)
    }

    @Test fun divisionPercent() {
        val b = type({ digits(it, "30") }, { it.pressOperator("/") }, { digits(it, "15") }, { it.pressPercent() })
        assertEquals(200.0, value(b), 0.0)
    }

    @Test fun chainedAdditivePercents() {
        val b = type(
            { digits(it, "200") }, { it.pressOperator("+") }, { digits(it, "10") }, { it.pressPercent() },
            { it.pressOperator("+") }, { digits(it, "5") }, { it.pressPercent() },
        )
        assertEquals(231.0, value(b), 0.0)
    }

    // ---- retention policy -------------------------------------------------------------

    @Test fun retentionCutoffs() {
        val now = 1_000_000_000_000L
        assertEquals(now - RetentionPolicy.MILLIS_PER_DAY, RetentionPolicy.ONE_DAY.cutoffMillis(now))
        assertEquals(now - 30 * RetentionPolicy.MILLIS_PER_DAY, RetentionPolicy.THIRTY_DAYS.cutoffMillis(now))
        assertNull(RetentionPolicy.NEVER.cutoffMillis(now))
    }

    @Test fun retentionExpiryUsesInjectableClock() {
        val clock = TimeSource.fixed(50L * RetentionPolicy.MILLIS_PER_DAY)
        val now = clock.nowMillis()
        assertTrue(RetentionPolicy.THIRTY_DAYS.isExpired(10L * RetentionPolicy.MILLIS_PER_DAY, now))
        assertFalse(RetentionPolicy.THIRTY_DAYS.isExpired(40L * RetentionPolicy.MILLIS_PER_DAY, now))
        assertFalse(RetentionPolicy.NEVER.isExpired(0L, now))
    }

    @Test fun defaultRetentionIsThirtyDays() = assertEquals(RetentionPolicy.THIRTY_DAYS, RetentionPolicy.DEFAULT)
}
