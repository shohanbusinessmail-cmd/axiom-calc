package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.complex.Complex
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/** The nature of the roots of a quadratic. */
enum class QuadraticKind {
    TWO_DISTINCT_REAL,
    REPEATED_REAL,
    COMPLEX_CONJUGATE_PAIR,
    DEGENERATE_LINEAR,
    DEGENERATE_CONSTANT,
}

data class QuadraticSolution(
    val a: Double,
    val b: Double,
    val c: Double,
    val discriminant: Double,
    val kind: QuadraticKind,
    val realRoots: List<Double>,
    val complexRoots: List<Complex>,
    /** The single root when the equation degenerated to linear. */
    val linearRoot: Double? = null,
) {
    /** Human-readable root list that never hides the degenerate cases. */
    fun describe(): String = when (kind) {
        QuadraticKind.TWO_DISTINCT_REAL -> "x = ${fmt(realRoots[0])} or x = ${fmt(realRoots[1])}"
        QuadraticKind.REPEATED_REAL -> "x = ${fmt(realRoots[0])} (repeated)"
        QuadraticKind.COMPLEX_CONJUGATE_PAIR ->
            "x = ${complexRoots[0].toRectangularString()} or x = ${complexRoots[1].toRectangularString()}"
        QuadraticKind.DEGENERATE_LINEAR ->
            if (linearRoot == null) "no solution" else "degenerate (a = 0): linear root x = ${fmt(linearRoot)}"
        QuadraticKind.DEGENERATE_CONSTANT ->
            if (abs(c) < 1e-15) "identity: every x satisfies 0 = 0" else "contradiction: no x satisfies ${fmt(c)} = 0"
    }

    private fun fmt(v: Double): String =
        String.format(Locale.ROOT, "%.10f", v).trimEnd('0').trimEnd('.')
}

/**
 * Quadratic equation solver for a x^2 + b x + c = 0.
 *
 * Numerical method: the standard discriminant test, but with the *stable*
 * root formula. Computing both roots as `(-b ± sqrt(D)) / 2a` loses precision
 * through catastrophic cancellation when |b| >> sqrt(D); instead one root is
 * computed with the sign that avoids cancellation and the other from the
 * identity x1 * x2 = c / a. This is the numerically appropriate method and it
 * is documented (MATHEMATICS_SPEC.md §Solvers).
 */
object Quadratic {

    fun solve(a: CalcNumber, b: CalcNumber, c: CalcNumber): QuadraticSolution {
        val ad = a.toDouble(); val bd = b.toDouble(); val cd = c.toDouble()
        if (ad == 0.0) {
            return if (bd == 0.0) {
                QuadraticSolution(ad, bd, cd, Double.NaN, QuadraticKind.DEGENERATE_CONSTANT, emptyList(), emptyList())
            } else {
                QuadraticSolution(ad, bd, cd, Double.NaN, QuadraticKind.DEGENERATE_LINEAR, emptyList(), emptyList(), linearRoot = -cd / bd)
            }
        }
        val disc = bd * bd - 4.0 * ad * cd
        return when {
            disc > 0.0 -> {
                val sq = sqrt(disc)
                val q = -(bd + (if (bd >= 0) sq else -sq)) / 2.0
                val x1 = q / ad
                val x2 = if (q == 0.0) (-bd + sq) / (2 * ad) else cd / q
                val roots = listOf(x1, x2).sorted()
                QuadraticSolution(ad, bd, cd, disc, QuadraticKind.TWO_DISTINCT_REAL, roots, emptyList())
            }
            disc == 0.0 -> {
                val x = -bd / (2.0 * ad)
                QuadraticSolution(ad, bd, cd, disc, QuadraticKind.REPEATED_REAL, listOf(x), emptyList())
            }
            else -> {
                val re = -bd / (2.0 * ad)
                val im = sqrt(-disc) / (2.0 * abs(ad))
                QuadraticSolution(
                    ad, bd, cd, disc, QuadraticKind.COMPLEX_CONJUGATE_PAIR, emptyList(),
                    listOf(Complex(re, im), Complex(re, -im)),
                )
            }
        }
    }

    /** Convenience for the mandatory regression suite: roots of x^2 - 5x + 6. */
    fun solveDoubles(a: Double, b: Double, c: Double): QuadraticSolution =
        solve(CalcNumber.of(a), CalcNumber.of(b), CalcNumber.of(c))

    /** Validates that user-supplied coefficients form a genuine quadratic. */
    fun requireQuadraticOrBetter(a: CalcNumber) {
        if (a.isZero) throw CalcException(CalcError.InvalidArgument("a", "a = 0 is not a quadratic; use the linear solver"))
    }
}
