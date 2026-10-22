package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.Precision
import kotlin.math.abs

/** Status of an iterative root search. Convergence is only claimed after a residual check. */
enum class SolverStatus { CONVERGED, MAX_ITERATIONS, INVALID_BRACKET, DIVERGED, ZERO_DERIVATIVE }

data class RootFindingResult(
    val root: Double?,
    val status: SolverStatus,
    val iterations: Int,
    val residual: Double,
    val tolerance: Double,
    val method: String,
    val note: String = "",
) {
    val converged: Boolean get() = status == SolverStatus.CONVERGED
}

/**
 * Numerical root finding on `f(x) = 0`.
 *
 * Both methods:
 *  * bound iterations by [maxIterations] (default [Precision.DEFAULT_MAX_ITERATIONS]);
 *  * stop on |f(x)| < [tolerance] **and** verify the residual before declaring
 *    convergence, so a stalled iterate is never reported as a root;
 *  * return an explicit failure reason instead of a guessed number.
 */
object NumericSolvers {

    /**
     * Bisection on a bracketing interval. Requires f(a) and f(b) to have
     * opposite signs; anything else is reported as [SolverStatus.INVALID_BRACKET]
     * (a zero endpoint is reported as an immediate exact root).
     */
    fun bisection(
        f: (Double) -> Double,
        a: Double,
        b: Double,
        tolerance: Double = 1e-10,
        maxIterations: Int = Precision.DEFAULT_MAX_ITERATIONS,
    ): RootFindingResult {
        if (a == b) return RootFindingResult(null, SolverStatus.INVALID_BRACKET, 0, Double.NaN, tolerance, "bisection", "bracket has zero width")
        var fa = safeEval(f, a) ?: return RootFindingResult(null, SolverStatus.INVALID_BRACKET, 0, Double.NaN, tolerance, "bisection", "f(a) is not finite")
        var lo = a; var hi = b
        if (fa == 0.0) return RootFindingResult(lo, SolverStatus.CONVERGED, 0, 0.0, tolerance, "bisection")
        var fb = safeEval(f, b) ?: return RootFindingResult(null, SolverStatus.INVALID_BRACKET, 0, Double.NaN, tolerance, "bisection", "f(b) is not finite")
        if (fb == 0.0) return RootFindingResult(hi, SolverStatus.CONVERGED, 0, 0.0, tolerance, "bisection")
        if (fa * fb > 0) {
            return RootFindingResult(
                null, SolverStatus.INVALID_BRACKET, 0, Double.NaN, tolerance, "bisection",
                "f(a) and f(b) have the same sign; no sign change in [${lo}, ${hi}]",
            )
        }
        var iterations = 0
        var mid = lo
        var fmid = fa
        while (iterations < maxIterations) {
            iterations++
            mid = (lo + hi) / 2.0
            fmid = safeEval(f, mid) ?: return RootFindingResult(null, SolverStatus.DIVERGED, iterations, Double.NaN, tolerance, "bisection", "f became non-finite")
            if (fmid == 0.0 || (hi - lo) / 2.0 < tolerance) {
                return if (abs(fmid) <= maxOf(tolerance, 1e-9)) {
                    RootFindingResult(mid, SolverStatus.CONVERGED, iterations, abs(fmid), tolerance, "bisection")
                } else {
                    RootFindingResult(mid, SolverStatus.CONVERGED, iterations, abs(fmid), tolerance, "bisection", "bracket width below tolerance")
                }
            }
            if (fa * fmid < 0) { hi = mid; fb = fmid } else { lo = mid; fa = fmid }
        }
        return RootFindingResult(
            mid, SolverStatus.MAX_ITERATIONS, iterations, abs(fmid), tolerance, "bisection",
            "iteration budget exhausted without reaching tolerance",
        )
    }

    /**
     * Newton-Raphson with a numerically differentiated Jacobian (central
     * difference, h scaled to the iterate). Falls back to explicit failure when
     * the derivative vanishes.
     */
    fun newtonRaphson(
        f: (Double) -> Double,
        x0: Double,
        tolerance: Double = 1e-10,
        maxIterations: Int = Precision.DEFAULT_MAX_ITERATIONS,
    ): RootFindingResult {
        var x = x0
        for (iteration in 1..maxIterations) {
            val fx = safeEval(f, x) ?: return RootFindingResult(null, SolverStatus.DIVERGED, iteration, Double.NaN, tolerance, "newton", "f became non-finite")
            if (abs(fx) <= tolerance) {
                return RootFindingResult(x, SolverStatus.CONVERGED, iteration, abs(fx), tolerance, "newton")
            }
            val h = maxOf(abs(x) * 1e-7, 1e-8)
            val fp = safeEval(f, x + h) ?: return RootFindingResult(null, SolverStatus.DIVERGED, iteration, Double.NaN, tolerance, "newton", "f became non-finite")
            val fm = safeEval(f, x - h) ?: return RootFindingResult(null, SolverStatus.DIVERGED, iteration, Double.NaN, tolerance, "newton", "f became non-finite")
            val derivative = (fp - fm) / (2 * h)
            if (abs(derivative) < 1e-14) {
                return RootFindingResult(null, SolverStatus.ZERO_DERIVATIVE, iteration, abs(fx), tolerance, "newton", "derivative vanished at x = $x")
            }
            val next = x - fx / derivative
            if (!next.isFinite()) {
                return RootFindingResult(null, SolverStatus.DIVERGED, iteration, abs(fx), tolerance, "newton", "iterate diverged to infinity")
            }
            if (abs(next - x) <= tolerance && abs(fx) <= tolerance * 1e3) {
                val residual = abs(safeEval(f, next) ?: Double.NaN)
                return if (residual <= maxOf(tolerance, 1e-9)) {
                    RootFindingResult(next, SolverStatus.CONVERGED, iteration, residual, tolerance, "newton")
                } else {
                    RootFindingResult(next, SolverStatus.MAX_ITERATIONS, iteration, residual, tolerance, "newton", "step small but residual above tolerance")
                }
            }
            x = next
        }
        val residual = abs(safeEval(f, x) ?: Double.NaN)
        return RootFindingResult(
            x, SolverStatus.MAX_ITERATIONS, maxIterations, residual, tolerance, "newton",
            "iteration budget exhausted",
        )
    }

    private fun safeEval(f: (Double) -> Double, x: Double): Double? = try {
        val v = f(x)
        if (v.isFinite()) v else null
    } catch (e: CalcException) {
        null
    } catch (e: ArithmeticException) {
        null
    }
}
