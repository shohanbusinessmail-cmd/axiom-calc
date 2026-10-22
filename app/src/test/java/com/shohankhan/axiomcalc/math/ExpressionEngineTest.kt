package com.shohankhan.axiomcalc.math

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.formatting.DisplayPolicy
import com.shohankhan.axiomcalc.core.formatting.NumberFormatter
import com.shohankhan.axiomcalc.core.formatting.NumberNotation
import com.shohankhan.axiomcalc.core.formatting.RoundingPolicy
import com.shohankhan.axiomcalc.math.evaluator.CalcResult
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mandatory mathematical reference suite plus grammar edge cases.
 * Tolerances are used only where the operation is genuinely approximate.
 */
class ExpressionEngineTest {

    private fun eval(source: String, mode: AngleMode = AngleMode.DEGREES, ans: CalcNumber = CalcNumber.ZERO): CalcNumber =
        when (val r = ExpressionEngine.evaluate(source, mode, ans)) {
            is CalcResult.Success -> r.value
            is CalcResult.Failure -> throw AssertionError("expected success for '$source' but got ${r.error.code}: ${r.error.detail}")
        }

    private fun dbl(source: String, mode: AngleMode = AngleMode.DEGREES): Double = eval(source, mode).toDouble()

    private fun errorOf(source: String): CalcError =
        ExpressionEngine.evaluate(source).errorOrNull()
            ?: throw AssertionError("expected a failure for '$source' but it evaluated")

    // ---- mandatory reference suite (MATHEMATICS_SPEC / spec 11.2) --------------

    @Test fun twoPlusTwo() = assertEquals(0.0, dbl("2 + 2") - 4.0, 0.0)
    @Test fun precedenceMulBeforeAdd() = assertEquals(14.0, dbl("2 + 3 * 4"), 0.0)
    @Test fun parenthesesFirst() = assertEquals(20.0, dbl("(2 + 3) * 4"), 0.0)
    @Test fun sqrt81() = assertEquals(9.0, dbl("sqrt(81)"), 0.0)
    @Test fun log10of1000() = assertEquals(3.0, dbl("log10(1000)"), 1e-12)
    @Test fun lnOfE() = assertEquals(1.0, dbl("ln(e)"), 1e-12)
    @Test fun sin30deg() = assertEquals(0.5, dbl("sin(30)"), 1e-12)
    @Test fun cos60deg() = assertEquals(0.5, dbl("cos(60)"), 1e-12)
    @Test fun percentOfContext() = assertEquals(30.0, dbl("200 * 15%"), 0.0)
    @Test fun discountTwentyOnFiveHundred() = assertEquals(400.0, dbl("500 * (1 - 20%)"), 0.0)
    @Test fun increaseFrom80to100() = assertEquals(25.0, dbl("(100 - 80) / 80 * 100"), 0.0)
    @Test fun gcd48and18() = assertEquals(6.0, dbl("gcd(48, 18)"), 0.0)
    @Test fun lcm12and18() = assertEquals(36.0, dbl("lcm(12, 18)"), 0.0)
    @Test fun quadraticRoots() {
        val s = com.shohankhan.axiomcalc.math.solvers.Quadratic.solveDoubles(1.0, -5.0, 6.0)
        assertEquals(listOf(2.0, 3.0), s.realRoots)
    }
    @Test fun meanOf246() {
        val v = listOf(2, 4, 6).map { CalcNumber.of(it) }
        assertEquals(4.0, com.shohankhan.axiomcalc.math.statistics.Statistics.mean(v).toDouble(), 0.0)
    }

    // ---- grammar edge cases (spec 7.2) ----------------------------------------

    @Test fun unaryMinusBindsLooserThanPower() = assertEquals(-4.0, dbl("-2^2"), 0.0)
    @Test fun parenthesisedNegativeBase() = assertEquals(4.0, dbl("(-2)^2"), 0.0)
    @Test fun negativeExponent() = assertEquals(0.125, dbl("2^-3"), 1e-15)
    @Test fun powerIsRightAssociative() = assertEquals(512.0, dbl("2^3^2"), 0.0)
    @Test fun nestedParentheses() = assertEquals(14.0, dbl("((2 + (3 * 4)) - 2) + 2"), 0.0)
    @Test fun nestedFunctionArguments() = assertEquals(3.0, dbl("sqrt(sqrt(sqrt(6561)))"), 1e-12)
    @Test fun implicitMultiplicationWithConstant() = assertEquals(2.0 * Math.PI, dbl("2pi"), 1e-12)
    @Test fun implicitMultiplicationWithParens() = assertEquals(27.0, dbl("3(4+5)"), 0.0)
    @Test fun juxtapositionBindsTighterThanDivision() = assertEquals(1.0 / (2 * Math.PI), dbl("1/2pi"), 1e-12)
    @Test fun scientificNotationLiteral() = assertEquals(1500.0, dbl("1.5e3"), 0.0)
    @Test fun scientificNotationNegativeExponent() = assertEquals(0.002, dbl("2e-3"), 1e-15)
    @Test fun twoEIsMultiplicationWithEuler() = assertEquals(2.0 * Math.E, dbl("2e"), 1e-12)
    @Test fun modOperator() = assertEquals(1.0, dbl("7 mod 3"), 0.0)
    @Test fun factorialPostfix() = assertEquals(120.0, dbl("5!"), 0.0)
    @Test fun previousAnswerSubstitution() = assertEquals(9.0, eval("ans + 5", ans = CalcNumber.of(4)).toDouble(), 0.0)
    @Test fun unicodeOperatorsAreAccepted() = assertEquals(7.0, dbl("3 × 2 + 1"), 0.0)
    @Test fun divisionSignAccepted() = assertEquals(4.0, dbl("8 ÷ 2"), 0.0)

    // ---- typed error model (spec 7.3) -----------------------------------------

    @Test fun divisionByZeroIsTyped() {
        val e = errorOf("1 / 0")
        assertTrue("expected DIVISION_BY_ZERO but was ${e.code}", e is CalcError.DivisionByZero)
    }
    @Test fun zeroDividedByZeroIsTyped() = assertTrue(errorOf("0 / 0") is CalcError.DivisionByZero)
    @Test fun incompleteExpression() = assertTrue(errorOf("2 +") is CalcError.Incomplete || errorOf("2 +") is CalcError.Syntax)
    @Test fun unbalancedParentheses() = assertTrue(errorOf("(2 + 3") is CalcError.UnbalancedParentheses)
    @Test fun syntaxErrorOnStrayOperator() = assertTrue(errorOf("2 + * 3") is CalcError.Syntax)
    @Test fun malformedLiteral() = assertTrue(errorOf("1.2.3") is CalcError.Syntax)
    @Test fun unknownFunctionIsTyped() {
        val e = errorOf("frobnicate(2)")
        assertTrue("expected UNKNOWN_FUNCTION but was ${e.code}", e is CalcError.UnknownFunction)
    }
    @Test fun sqrtOfNegativeIsDomain() = assertTrue(errorOf("sqrt(-1)") is CalcError.Domain)
    @Test fun lnOfZeroIsDomain() = assertTrue(errorOf("ln(0)") is CalcError.Domain)
    @Test fun logBaseOneIsDomain() = assertTrue(errorOf("log(8, 1)") is CalcError.Domain)
    @Test fun asinOutOfRangeIsDomain() = assertTrue(errorOf("asin(2)") is CalcError.Domain)
    @Test fun tanSingularityIsDomain() = assertTrue(errorOf("tan(90)") is CalcError.Domain)
    @Test fun negativeFactorialIsDomain() = assertTrue(errorOf("(-3)!") is CalcError.Domain)
    @Test fun factorialOfNonIntegerIsDomain() = assertTrue(errorOf("2.5!") is CalcError.Domain)
    @Test fun factorialAboveLimitIsResourceLimit() = assertTrue(errorOf("20000!") is CalcError.ResourceLimit)
    @Test fun zeroToNegativePowerIsDivisionByZero() = assertTrue(errorOf("0^-2") is CalcError.DivisionByZero)
    @Test fun zeroToZeroIsOne() = assertEquals(1.0, dbl("0^0"), 0.0)
    @Test fun negativeBaseFractionalExponentIsDomain() = assertTrue(errorOf("(-8)^0.5") is CalcError.Domain)
    @Test fun wrongArityIsInvalidArgument() = assertTrue(errorOf("sqrt(1, 2)") is CalcError.InvalidArgument)
    @Test fun overflowIsTyped() = assertTrue(errorOf("10^1000000") is CalcError.ResourceLimit || errorOf("10^1000000") is CalcError.Overflow)
    @Test fun deepParenthesesAreGroupingNotNodes() {
        // Parentheses collapse during parsing, so 400 of them are perfectly fine.
        assertEquals(1.0, dbl("(".repeat(400) + "1" + ")".repeat(400)), 0.0)
    }
    @Test fun excessiveNestingIsResourceLimit() {
        val deep = "abs(".repeat(400) + "1" + ")".repeat(400)
        assertTrue(errorOf(deep) is CalcError.ResourceLimit)
    }
    @Test fun longExpressionIsBounded() {
        val long = List(3000) { "1+" }.joinToString("") + "1"
        val e = errorOf(long)
        assertTrue("expected RESOURCE_LIMIT but was ${e.code}", e is CalcError.ResourceLimit)
    }

    // ---- angle mode ------------------------------------------------------------

    @Test fun radiansModeChangesTrig() = assertEquals(0.5, dbl("sin(pi/6)", AngleMode.RADIANS), 1e-12)
    @Test fun degreesModeIsDefault() = assertEquals(0.5, dbl("sin(30)"), 1e-12)
    @Test fun inverseTrigRespectsMode() = assertEquals(30.0, dbl("asin(0.5)"), 1e-9)

    // ---- precision & formatting -------------------------------------------------

    @Test fun decimalExactness() = assertEquals("0.3", eval("0.1 + 0.2").value.stripTrailingZeros().toPlainString())
    @Test fun integerResultsStayExact() = assertEquals("12345678901234567890", eval("12345678901234567890 * 1").value.toPlainString())
    @Test fun groupingApplied() = assertEquals("1,234,567", NumberFormatter.format(CalcNumber.of(1234567), DisplayPolicy(grouping = true)))
    @Test fun groupingCanBeDisabled() = assertEquals("1234567", NumberFormatter.format(CalcNumber.of(1234567), DisplayPolicy(grouping = false)))
    @Test fun fixedDecimals() = assertEquals("3.142", NumberFormatter.format(CalcNumber.parse("3.14159"), DisplayPolicy(autoPrecision = false, decimals = 3, grouping = false)))
    @Test fun scientificNotation() = assertEquals("1.234567e+6", NumberFormatter.format(CalcNumber.of(1234567), DisplayPolicy(notation = NumberNotation.SCIENTIFIC)))
    @Test fun engineeringNotation() = assertEquals("1.234567e+6", NumberFormatter.format(CalcNumber.of(1234567), DisplayPolicy(notation = NumberNotation.ENGINEERING)))
    @Test fun engineeringExponentMultipleOfThree() {
        val s = NumberFormatter.engineering(CalcNumber.of(12345), 7)
        val exponent = s.substringAfter('e').toInt()
        assertEquals(0, exponent % 3)
    }
    @Test fun significantFigures() = assertEquals("3.14", NumberFormatter.significantFigures(CalcNumber.parse("3.14159"), 3).value.toPlainString())
    @Test fun roundingHalfUp() = assertEquals("2.35", NumberFormatter.roundTo(CalcNumber.parse("2.345"), 2, RoundingPolicy.HALF_UP).value.toPlainString())
    @Test fun roundingHalfEven() = assertEquals("2.34", NumberFormatter.roundTo(CalcNumber.parse("2.345"), 2, RoundingPolicy.HALF_EVEN).value.toPlainString())
    @Test fun tinyValueSwitchesToScientific() {
        val out = NumberFormatter.format(CalcNumber.parse("0.0000001"), DisplayPolicy(grouping = false))
        assertTrue("expected scientific fallback but got $out", out.contains('e'))
    }

    // ---- preview semantics ---------------------------------------------------------

    @Test fun previewIsNullWhileIncomplete() {
        assertNull(ExpressionEngine.preview("2 +"))
        assertNull(ExpressionEngine.preview("sqrt("))
        assertNull(ExpressionEngine.preview(""))
    }
    @Test fun previewReturnsValueWhenComplete() =
        assertEquals(5.0, (ExpressionEngine.preview("2 + 3") as CalcResult.Success).value.toDouble(), 0.0)
    @Test fun previewSurfacesRealErrors() {
        val r = ExpressionEngine.preview("1 / 0")
        assertTrue(r is CalcResult.Failure && (r as CalcResult.Failure).error is CalcError.DivisionByZero)
    }
}
