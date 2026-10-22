package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.evaluator.Evaluator
import com.shohankhan.axiomcalc.math.matrices.Matrix
import com.shohankhan.axiomcalc.math.parser.Expr
import com.shohankhan.axiomcalc.math.parser.Parser
import com.shohankhan.axiomcalc.math.parser.substitute
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import kotlin.math.abs

/** How a linear system resolved. Never a fabricated unique answer. */
enum class SystemOutcome { UNIQUE, NO_SOLUTION, INFINITELY_MANY }

/**
 * Linear equation solving.
 *
 * Single equation `a x + b = 0` and simultaneous systems of any size via
 * Gaussian elimination with rank classification:
 *
 *   rank(A) == rank([A|b]) == n   -> unique solution
 *   rank(A)  < rank([A|b])        -> no solution
 *   rank(A) == rank([A|b])  < n   -> infinitely many solutions
 *
 * The underdetermined and inconsistent cases are *reported*, not silently
 * resolved to a pseudo-inverse.
 */
object LinearEquations {

    /** Solves a x + b = 0. */
    fun solveOneVariable(a: CalcNumber, b: CalcNumber): LinearRoot = when {
        a.isZero && b.isZero -> LinearRoot(SystemOutcome.INFINITELY_MANY, null)
        a.isZero -> LinearRoot(SystemOutcome.NO_SOLUTION, null)
        else -> LinearRoot(SystemOutcome.UNIQUE, (b.negate() / a))
    }

    data class LinearRoot(val outcome: SystemOutcome, val x: CalcNumber?)

    /**
     * Extracts (a, b) from a linear expression in [variable] such that the
     * expression equals `a*variable + b`.
     *
     * Method: evaluate the *same parsed tree* at variable = 0 and = 1, then
     * verify linearity at variable = 2 (`value(2) == 2a + b`). A non-linear
     * expression is rejected with [CalcError.Domain] instead of being solved
     * incorrectly.
     */
    fun linearCoefficients(expression: String, variable: String = "x"): Pair<CalcNumber, CalcNumber> {
        val tree = try {
            Parser.parse(expression)
        } catch (e: CalcException) {
            throw CalcException(CalcError.InvalidArgument("equation", "cannot parse '$expression': ${e.error.code}"))
        }
        val v0 = evalAt(tree, variable, 0.0)
        val v1 = evalAt(tree, variable, 1.0)
        val v2 = evalAt(tree, variable, 2.0)
        val b = v0
        val a = v1 - v0
        val predicted = a * CalcNumber.TWO + b
        if (abs((predicted - v2).toDouble()) > 1e-9 * maxOf(1.0, abs(v2.toDouble()))) {
            throw CalcException(
                CalcError.Domain("linear solver", "'$expression' is not linear in '$variable'"),
            )
        }
        return a to b
    }

    /** Coefficients of `a x^2 + b x + c` for the quadratic solver, same technique. */
    fun quadraticCoefficients(expression: String, variable: String = "x"): Triple<CalcNumber, CalcNumber, CalcNumber> {
        val tree = try {
            Parser.parse(expression)
        } catch (e: CalcException) {
            throw CalcException(CalcError.InvalidArgument("equation", "cannot parse '$expression': ${e.error.code}"))
        }
        val f0 = evalAt(tree, variable, 0.0)              // c
        val f1 = evalAt(tree, variable, 1.0)              // a + b + c
        val fm1 = evalAt(tree, variable, -1.0)            // a - b + c
        val f2 = evalAt(tree, variable, 2.0)              // 4a + 2b + c
        val c = f0
        val aPlusB = f1 - c
        val aMinusB = fm1 - c
        val a = (aPlusB + aMinusB) / CalcNumber.TWO
        val b = (aPlusB - aMinusB) / CalcNumber.TWO
        val predicted = a * CalcNumber.of(4) + b * CalcNumber.TWO + c
        if (abs((predicted - f2).toDouble()) > 1e-9 * maxOf(1.0, abs(f2.toDouble()))) {
            throw CalcException(
                CalcError.Domain("quadratic solver", "'$expression' is not a polynomial of degree <= 2 in '$variable'"),
            )
        }
        return Triple(a, b, c)
    }

    private fun evalAt(tree: Expr, variable: String, value: Double): CalcNumber {
        val substituted = tree.substitute(variable, CalcNumber.of(value))
        return Evaluator(AngleMode.RADIANS).evaluate(substituted)
    }

    /**
     * Solves A x = b for an n x n system with full outcome classification.
     */
    fun solveSystem(coefficients: List<List<Double>>, constants: List<Double>): SystemSolution {
        val n = coefficients.size
        if (n == 0) throw CalcException(CalcError.EmptyDataset("the system has no equations"))
        coefficients.forEachIndexed { i, row ->
            if (row.size != n) {
                throw CalcException(
                    CalcError.DimensionMismatch("equation ${i + 1} has ${row.size} coefficients, expected $n"),
                )
            }
        }
        if (constants.size != n) {
            throw CalcException(
                CalcError.DimensionMismatch("${constants.size} constants for $n equations"),
            )
        }
        val a = Matrix.of(n, n, coefficients.flatten())
        val rankA = a.rank()
        val augmented = Matrix.of(
            n, n + 1,
            coefficients.mapIndexed { i, row -> row + constants[i] }.flatten(),
        )
        val rankAug = augmented.rank()
        return when {
            rankA < rankAug -> SystemSolution(SystemOutcome.NO_SOLUTION, null, rankA, rankAug)
            rankA < n -> SystemSolution(SystemOutcome.INFINITELY_MANY, null, rankA, rankAug)
            else -> {
                val inv = a.inverse()
                val x = inv.timesVector(constants.toDoubleArray())
                // Residual check: never report a solution that does not satisfy the system.
                val residual = a.timesVector(x).zip(constants).maxOf { (l, r) -> abs(l - r) }
                if (residual > 1e-6 * maxOf(1.0, constants.maxOf { abs(it) })) {
                    throw CalcException(
                        CalcError.NonConvergence(0, residual, "Gaussian solution failed residual check"),
                    )
                }
                SystemSolution(SystemOutcome.UNIQUE, x.toList(), rankA, rankAug)
            }
        }
    }

    data class SystemSolution(
        val outcome: SystemOutcome,
        val solution: List<Double>?,
        val rankOfCoefficients: Int,
        val rankOfAugmented: Int,
    )
}
