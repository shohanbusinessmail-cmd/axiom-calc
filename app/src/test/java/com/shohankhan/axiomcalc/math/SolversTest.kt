package com.shohankhan.axiomcalc.math

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.complex.Complex
import com.shohankhan.axiomcalc.math.matrices.Matrix
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.solvers.Bases
import com.shohankhan.axiomcalc.math.solvers.CivilDate
import com.shohankhan.axiomcalc.math.solvers.Duration
import com.shohankhan.axiomcalc.math.solvers.Finance
import com.shohankhan.axiomcalc.math.solvers.Fraction
import com.shohankhan.axiomcalc.math.solvers.IntegerMath
import com.shohankhan.axiomcalc.math.solvers.LinearEquations
import com.shohankhan.axiomcalc.math.solvers.NumericSolvers
import com.shohankhan.axiomcalc.math.solvers.PercentMath
import com.shohankhan.axiomcalc.math.solvers.Quadratic
import com.shohankhan.axiomcalc.math.solvers.QuadraticKind
import com.shohankhan.axiomcalc.math.solvers.Sequences
import com.shohankhan.axiomcalc.math.solvers.SolverStatus
import com.shohankhan.axiomcalc.math.solvers.SystemOutcome
import com.shohankhan.axiomcalc.math.statistics.Statistics
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Edge cases and reference values for every solver module (spec 11.3). */
class SolversTest {

    private fun d(v: Number) = CalcNumber.of(v.toDouble())
    private fun expectError(block: () -> Unit): CalcError {
        try {
            block()
        } catch (e: CalcException) {
            return e.error
        }
        fail("expected a CalcException but none was thrown")
        error("unreachable")
    }

    // ---- statistics ---------------------------------------------------------------

    @Test fun statisticsReferenceValues() {
        val v = listOf(2, 4, 6, 8).map { CalcNumber.of(it) }
        assertEquals(20.0, Statistics.sum(v).toDouble(), 0.0)
        assertEquals(5.0, Statistics.mean(v).toDouble(), 0.0)
        assertEquals(5.0, Statistics.median(v).toDouble(), 0.0)
        assertEquals(6.0, Statistics.range(v).toDouble(), 0.0)
        assertEquals(5.0, Statistics.populationVariance(v).toDouble(), 1e-12)
        assertEquals(20.0 / 3.0, Statistics.sampleVariance(v).toDouble(), 1e-12)
    }

    @Test fun emptyDatasetIsTyped() = assertTrue(expectError { Statistics.mean(emptyList()) } is CalcError.EmptyDataset)

    @Test fun insufficientSampleIsTyped() {
        val e = expectError { Statistics.sampleVariance(listOf(CalcNumber.of(5))) }
        assertTrue(e is CalcError.InsufficientSample)
    }

    @Test fun modeHandlesTiesAndDistinct() {
        val tie = listOf(1, 1, 2, 2).map { CalcNumber.of(it) }
        assertEquals(2, Statistics.mode(tie).size)
        val distinct = listOf(1, 2, 3).map { CalcNumber.of(it) }
        assertTrue(Statistics.mode(distinct).isEmpty())
    }

    @Test fun weightedMean() {
        val v = listOf(2, 4).map { CalcNumber.of(it) }
        val w = listOf(1, 3).map { CalcNumber.of(it) }
        assertEquals(3.5, Statistics.weightedMean(v, w).toDouble(), 1e-12)
    }

    // ---- matrices -------------------------------------------------------------------

    @Test fun matrixProductAndTranspose() {
        val a = Matrix.parse("1, 2; 3, 4")
        val b = Matrix.parse("5, 6; 7, 8")
        val ab = a * b
        assertEquals(19.0, ab[0, 0], 1e-12)
        assertEquals(22.0, ab[0, 1], 1e-12)
        assertEquals(43.0, ab[1, 0], 1e-12)
        assertEquals(50.0, ab[1, 1], 1e-12)
        assertEquals(3.0, a.transpose()[0, 1], 0.0)
    }

    @Test fun determinantAndInverse() {
        val a = Matrix.parse("4, 7; 2, 6")
        assertEquals(10.0, a.determinant(), 1e-9)
        val inv = a.inverse()
        val identity = a * inv
        assertEquals(1.0, identity[0, 0], 1e-9)
        assertEquals(0.0, identity[0, 1], 1e-9)
    }

    @Test fun singularMatrixInverseIsTyped() =
        assertTrue(expectError { Matrix.parse("1, 2; 2, 4").inverse() } is CalcError.SingularMatrix)

    @Test fun dimensionMismatchIsTyped() =
        assertTrue(expectError { Matrix.parse("1, 2; 3, 4") + Matrix.parse("1, 2, 3; 4, 5, 6") } is CalcError.DimensionMismatch)

    @Test fun rankDetection() {
        assertEquals(2, Matrix.parse("1, 2; 3, 4").rank())
        assertEquals(1, Matrix.parse("1, 2; 2, 4").rank())
    }

    @Test fun oversizedMatrixIsResourceLimit() =
        assertTrue(expectError { Matrix.of(100, 100, List(10000) { 1.0 }) } is CalcError.ResourceLimit)

    @Test fun matrixParseRejectsRaggedRows() =
        assertTrue(expectError { Matrix.parse("1, 2; 3") } is CalcError.DimensionMismatch)

    // ---- complex -----------------------------------------------------------------------

    @Test fun complexArithmetic() {
        val a = Complex(1.0, 2.0); val b = Complex(3.0, 4.0)
        assertEquals(Complex(4.0, 6.0), a + b)
        assertEquals(Complex(-5.0, 10.0), a * b)
        val q = a / b
        assertEquals(0.44, q.re, 1e-12)
        assertEquals(0.08, q.im, 1e-12)
        assertEquals(Complex(1.0, -2.0), a.conjugate())
        assertEquals(Math.hypot(1.0, 2.0), a.magnitude(), 1e-12)
    }

    @Test fun complexDivisionByZeroIsTyped() =
        assertTrue(expectError { Complex(1.0, 1.0) / Complex.ZERO } is CalcError.DivisionByZero)

    @Test fun polarRoundTrip() {
        val z = Complex(3.0, 4.0)
        val p = z.toPolar()
        assertEquals(5.0, p.r, 1e-12)
        val back = p.toRectangular()
        assertEquals(3.0, back.re, 1e-9)
        assertEquals(4.0, back.im, 1e-9)
    }

    // ---- linear & quadratic ---------------------------------------------------------------

    @Test fun linearUnique() {
        val r = LinearEquations.solveOneVariable(d(3), d(-15))
        assertEquals(SystemOutcome.UNIQUE, r.outcome)
        assertEquals(5.0, r.x!!.toDouble(), 1e-12)
    }

    @Test fun linearNoSolutionAndInfinite() {
        assertEquals(SystemOutcome.NO_SOLUTION, LinearEquations.solveOneVariable(d(0), d(4)).outcome)
        assertEquals(SystemOutcome.INFINITELY_MANY, LinearEquations.solveOneVariable(d(0), d(0)).outcome)
    }

    @Test fun coefficientExtractionAndLinearityCheck() {
        val (a, b) = LinearEquations.linearCoefficients("3x + 5")
        assertEquals(3.0, a.toDouble(), 1e-9)
        assertEquals(5.0, b.toDouble(), 1e-9)
        val e = expectError { LinearEquations.linearCoefficients("x^2 + 1") }
        assertTrue(e is CalcError.Domain)
    }

    @Test fun simultaneousUnique() {
        val s = LinearEquations.solveSystem(
            listOf(listOf(1.0, 1.0), listOf(1.0, -1.0)),
            listOf(5.0, 1.0),
        )
        assertEquals(SystemOutcome.UNIQUE, s.outcome)
        assertEquals(3.0, s.solution!![0], 1e-9)
        assertEquals(2.0, s.solution[1], 1e-9)
    }

    @Test fun simultaneousNoSolution() {
        val s = LinearEquations.solveSystem(
            listOf(listOf(1.0, 1.0), listOf(2.0, 2.0)),
            listOf(3.0, 7.0),
        )
        assertEquals(SystemOutcome.NO_SOLUTION, s.outcome)
        assertNull(s.solution)
    }

    @Test fun simultaneousInfinite() {
        val s = LinearEquations.solveSystem(
            listOf(listOf(1.0, 1.0), listOf(2.0, 2.0)),
            listOf(3.0, 6.0),
        )
        assertEquals(SystemOutcome.INFINITELY_MANY, s.outcome)
    }

    @Test fun quadraticKinds() {
        assertEquals(QuadraticKind.TWO_DISTINCT_REAL, Quadratic.solveDoubles(1.0, -5.0, 6.0).kind)
        assertEquals(QuadraticKind.REPEATED_REAL, Quadratic.solveDoubles(1.0, -2.0, 1.0).kind)
        assertEquals(QuadraticKind.COMPLEX_CONJUGATE_PAIR, Quadratic.solveDoubles(1.0, 0.0, 1.0).kind)
        assertEquals(QuadraticKind.DEGENERATE_LINEAR, Quadratic.solveDoubles(0.0, 2.0, -4.0).kind)
        assertEquals(QuadraticKind.DEGENERATE_CONSTANT, Quadratic.solveDoubles(0.0, 0.0, 3.0).kind)
    }

    @Test fun quadraticStableRootsForIllConditionedCase() {
        // x^2 - 1e8 x + 1 = 0: naive formula loses the small root entirely.
        val s = Quadratic.solveDoubles(1.0, -1e8, 1.0)
        assertEquals(1e-8, s.realRoots[0], 1e-11)
        assertEquals(1e8, s.realRoots[1], 1.0)
    }

    @Test fun quadraticComplexRootsAreConjugates() {
        val s = Quadratic.solveDoubles(1.0, 2.0, 5.0)
        assertEquals(s.complexRoots[0].re, s.complexRoots[1].re, 0.0)
        assertEquals(-s.complexRoots[0].im, s.complexRoots[1].im, 0.0)
    }

    // ---- sequences -------------------------------------------------------------------------

    @Test fun arithmeticSequence() {
        val r = Sequences.arithmetic(d(2), d(3), CalcNumber.of(10))
        assertEquals(29.0, r.nthTerm.toDouble(), 0.0)
        assertEquals(155.0, r.sumToN.toDouble(), 0.0)
    }

    @Test fun geometricSequenceAndRatioOne() {
        val r = Sequences.geometric(d(2), d(3), CalcNumber.of(5))
        assertEquals(162.0, r.nthTerm.toDouble(), 1e-9)
        assertEquals(242.0, r.sumToN.toDouble(), 1e-9)
        val one = Sequences.geometric(d(4), d(1), CalcNumber.of(6))
        assertEquals(24.0, one.sumToN.toDouble(), 0.0)
    }

    @Test fun infiniteGeometricDivergenceIsReported() =
        assertTrue(expectError { Sequences.geometricInfinite(d(1), d(2)) } is CalcError.NonConvergence)

    // ---- integer math ---------------------------------------------------------------------------

    @Test fun gcdLcmFactorial() {
        assertEquals(BigInteger.valueOf(6), IntegerMath.gcd(BigInteger.valueOf(48), BigInteger.valueOf(18)))
        assertEquals(BigInteger.valueOf(36), IntegerMath.lcm(BigInteger.valueOf(12), BigInteger.valueOf(18)))
        assertEquals(BigInteger.valueOf(120), IntegerMath.factorial(BigInteger.valueOf(5)))
        assertEquals(BigInteger.ONE, IntegerMath.factorial(BigInteger.ZERO))
    }

    @Test fun negativeFactorialIsDomain() =
        assertTrue(expectError { IntegerMath.factorial(BigInteger.valueOf(-1)) } is CalcError.Domain)

    @Test fun primalityAndFactorization() {
        assertTrue(IntegerMath.isPrimeLong(97))
        assertFalse(IntegerMath.isPrimeLong(91))
        assertEquals(listOf(2L to 3, 3L to 2, 5L to 1), IntegerMath.primeFactorization(BigInteger.valueOf(360)).map { it.first.toLong() to it.second })
    }

    @Test fun factorizationBoundIsEnforced() =
        assertTrue(expectError { IntegerMath.primeFactorization(BigInteger.valueOf(10L).pow(30)) } is CalcError.ResourceLimit)

    // ---- fractions ------------------------------------------------------------------------------

    @Test fun fractionArithmeticExact() {
        val half = Fraction.of(1, 2); val third = Fraction.of(1, 3)
        assertEquals("5/6", (half + third).toString())
        assertEquals("1/6", (half - third).toString())
        assertEquals("1/6", (half * third).toString())
        assertEquals("3/2", (half / third).toString())
    }

    @Test fun fractionMixedAndDecimal() {
        val f = Fraction.of(7, 2)
        assertEquals("3 1/2", f.toMixedString())
        assertEquals("3.5", f.toDecimal(4).value.stripTrailingZeros().toPlainString())
    }

    @Test fun fractionZeroDenominatorIsTyped() =
        assertTrue(expectError { Fraction.of(1, 0) } is CalcError.DivisionByZero)

    @Test fun fractionParseMixed() = assertEquals("7/2", Fraction.parse("3 1/2").toString())

    // ---- bases ------------------------------------------------------------------------------------

    @Test fun baseConversions() {
        assertEquals("11111111", Bases.convert("255", 10, 2))
        assertEquals("FF", Bases.convert("255", 10, 16))
        assertEquals("377", Bases.convert("255", 10, 8))
        assertEquals("255", Bases.convert("FF", 16, 10))
    }

    @Test fun invalidDigitForBaseIsTyped() =
        assertTrue(expectError { Bases.parse("19", 8) } is CalcError.InvalidArgument)

    @Test fun baseArithmetic() {
        assertEquals("1010", Bases.add("101", "101", 2))
        assertEquals("1A", Bases.multiply("2", "D", 16))
    }

    // ---- finance -------------------------------------------------------------------------------------

    @Test fun simpleInterestReference() {
        val r = Finance.simpleInterest(d(1000), d(5), d(2))
        assertEquals(100.0, r.interest.toDouble(), 1e-12)
        assertEquals(1100.0, r.amount.toDouble(), 1e-12)
    }

    @Test fun compoundInterestReference() {
        val r = Finance.compoundInterest(d(1000), d(10), d(2), 1)
        assertEquals(1210.0, r.amount.toDouble(), 1e-9)
    }

    @Test fun amortizationZeroRateDegeneratesToDivision() {
        val a = Finance.amortization(d(1200), d(0), d(1), 12)
        assertEquals(100.0, a.periodicPayment.toDouble(), 1e-12)
        assertEquals(0.0, a.totalInterest.toDouble(), 1e-12)
    }

    @Test fun amortizationReference() {
        // 10 000 at 12%/year monthly for 1 year: payment ~= 888.49
        val a = Finance.amortization(d(10000), d(12), d(1), 12)
        assertEquals(888.49, a.periodicPayment.toDouble(), 0.01)
        assertEquals(12, a.numberOfPayments)
    }

    // ---- numeric solvers -------------------------------------------------------------------------------

    @Test fun bisectionConvergesAndValidatesResidual() {
        val r = NumericSolvers.bisection({ x -> x * x - 2.0 }, 0.0, 2.0)
        assertTrue(r.converged)
        assertEquals(Math.sqrt(2.0), r.root!!, 1e-9)
        assertTrue(r.residual <= 1e-9)
    }

    @Test fun bisectionInvalidBracketIsReported() {
        val r = NumericSolvers.bisection({ x -> x * x + 1.0 }, 0.0, 2.0)
        assertEquals(SolverStatus.INVALID_BRACKET, r.status)
        assertNull(r.root)
    }

    @Test fun newtonConverges() {
        val r = NumericSolvers.newtonRaphson({ x -> x * x * x - x - 2.0 }, 1.5)
        assertTrue(r.converged)
        assertEquals(1.5213797, r.root!!, 1e-6)
    }

    @Test fun newtonZeroDerivativeIsReported() {
        val r = NumericSolvers.newtonRaphson({ _: Double -> 5.0 }, 1.0)
        assertTrue(r.status == SolverStatus.ZERO_DERIVATIVE || r.status == SolverStatus.MAX_ITERATIONS)
    }

    // ---- dates and times ----------------------------------------------------------------------------------

    @Test fun leapYearHandling() {
        assertTrue(CivilDate.isLeap(2024))
        assertFalse(CivilDate.isLeap(1900))
        assertTrue(CivilDate.isLeap(2000))
        assertEquals(29, CivilDate.daysInMonth(2024, 2))
        assertEquals(28, CivilDate.daysInMonth(1900, 2))
    }

    @Test fun dayCountConventions() {
        val a = CivilDate(2024, 2, 28); val b = CivilDate(2024, 3, 1)
        assertEquals(2L, CivilDate.daysBetweenExclusive(a, b))
        assertEquals(3L, CivilDate.daysBetweenInclusive(a, b))
    }

    @Test fun dateAdditionAcrossLeapDay() {
        val r = CivilDate(2024, 2, 28) + 1
        assertEquals(CivilDate(2024, 2, 29), r)
        val r2 = CivilDate(2023, 2, 28) + 1
        assertEquals(CivilDate(2023, 3, 1), r2)
    }

    @Test fun invalidDateIsTyped() =
        assertTrue(expectError { CivilDate(2023, 2, 29) } is CalcError.InvalidArgument)

    @Test fun monthAdditionClampsDay() = assertEquals(CivilDate(2024, 2, 29), CivilDate(2024, 1, 31).plusMonths(1))

    @Test fun durationMayExceed24h() {
        val d = Duration.normalize(25, 75, 130)
        assertEquals("26h 17m 10s", d.format())
    }

    @Test fun durationSubtractionCanGoNegative() {
        val r = Duration.of(1, 0, 0) - Duration.of(2, 30, 0)
        assertEquals("-1h 30m 00s", r.format())
    }

    // ---- percentage policy helpers ------------------------------------------------------------------------------

    @Test fun successiveDiscountsDoNotAdd() {
        val r = PercentMath.successiveDiscounts(d(100), listOf(d(10), d(20)))
        assertEquals(72.0, r.finalPrice.toDouble(), 1e-12)
        assertEquals(28.0, r.effectiveDiscountPercent.toDouble(), 1e-12)
    }

    @Test fun markupVersusMargin() {
        assertEquals(25.0, PercentMath.markupPercent(d(80), d(100)).toDouble(), 1e-12)
        assertEquals(20.0, PercentMath.marginPercent(d(80), d(100)).toDouble(), 1e-12)
    }

    @Test fun zeroDenominatorGuards() {
        assertTrue(expectError { PercentMath.whatPercent(d(1), d(0)) } is CalcError.DivisionByZero)
        assertTrue(expectError { PercentMath.originalBeforeDecrease(d(90), d(100)) } is CalcError.DivisionByZero)
    }
}
