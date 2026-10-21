package com.shohankhan.axiomcalc.math.functions

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.constants.ConstantRegistry
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import com.shohankhan.axiomcalc.math.solvers.IntegerMath
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.ln as ktLn
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan
import kotlin.math.tanh

/**
 * One callable mathematical function.
 *
 * @property name        case-insensitive identifier
 * @property minArgs     minimum accepted argument count
 * @property maxArgs     maximum accepted argument count ([Int.MAX_VALUE] = variadic)
 * @property signature   how the call is written, for help text
 * @property description one-line English explanation
 * @property domain      the documented input constraint
 * @property evaluate    implementation; must throw [CalcException] with a typed
 *                       [CalcError] for every invalid input rather than return
 *                       a plausible-looking number
 */
data class FunctionDefinition(
    val name: String,
    val minArgs: Int,
    val maxArgs: Int,
    val signature: String,
    val description: String,
    val domain: String,
    val evaluate: (List<CalcNumber>, AngleMode) -> CalcNumber,
)

/**
 * The single authoritative registry of every function the engine can call.
 *
 * The Standard calculator, the Scientific calculator, Quick Solve and the
 * Formula Library all resolve function names through this object, so a
 * semantics change can only ever be made in one place.
 */
object FunctionRegistry {

    private val PI_D: Double = ConstantRegistry.PI.value.toDouble()

    // ---------------- helpers ------------------------------------------------

    private fun CalcNumber.requireReal(condition: Boolean, operation: String, detail: String) {
        if (!condition) throw CalcException(CalcError.Domain(operation, detail))
    }

    private fun CalcNumber.requirePositive(operation: String): CalcNumber {
        if (signum <= 0) {
            throw CalcException(CalcError.Domain(operation, "argument $this must be > 0"))
        }
        return this
    }

    private fun toRadians(x: CalcNumber, mode: AngleMode): Double =
        if (mode == AngleMode.DEGREES) x.toDouble() * PI_D / 180.0 else x.toDouble()

    private fun fromRadians(rad: Double, mode: AngleMode): CalcNumber =
        CalcNumber.fromTranscendental(if (mode == AngleMode.DEGREES) rad * 180.0 / PI_D else rad)

    private fun exactInteger(x: CalcNumber, operation: String): BigInteger {
        val bi = x.toBigIntegerOrNull()
            ?: throw CalcException(CalcError.InvalidArgument(operation, "$x is not an integer"))
        return bi
    }

    /**
     * Newton iteration for the real n-th root at the engine's decimal precision.
     * Used instead of `pow(1/n)` in double so that exact roots stay exact
     * (`root(81, 4) == 3` exactly, not `3.0000000000000004`).
     */
    fun bigNthRoot(x: BigDecimal, n: Int): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val mc = MathContext34
        var y = BigDecimal(cbrtOrPow(x.toDouble(), n), mc)
        if (y.signum() == 0) y = BigDecimal.ONE
        val nBd = BigDecimal(n)
        val nMinus1 = BigDecimal(n - 1)
        // Convergence floor: stop once the Newton step moves less than 1e-30,
        // which is below the 34 significant digits of the working context.
        val floor = BigDecimal("1e-30")
        repeat(80) {
            // y' = ((n-1) y + x / y^(n-1)) / n
            val yPow = y.pow(n - 1, mc)
            val next = nMinus1.multiply(y, mc).add(x.divide(yPow, mc), mc).divide(nBd, mc)
            val delta = next.subtract(y, mc).abs()
            y = next
            if (delta.compareTo(floor) <= 0) return@repeat
        }
        return y.round(mc)
    }

    private fun cbrtOrPow(x: Double, n: Int): Double =
        if (x >= 0) Math.pow(x, 1.0 / n) else -Math.pow(-x, 1.0 / n)

    private val MathContext34 = Precision.MC

    // ---------------- trigonometric singularities ----------------------------

    private fun requireNotSingular(rad: Double, name: String) {
        if (abs(cos(rad)) < 1e-12) {
            throw CalcException(
                CalcError.Domain(name, "tan is undefined at this angle (cos == 0): singularity"),
            )
        }
    }

    private fun requireCotNotSingular(rad: Double, name: String) {
        if (abs(sin(rad)) < 1e-12) {
            throw CalcException(CalcError.Domain(name, "function undefined at this angle (sin == 0)"))
        }
    }

    // ---------------- registry ------------------------------------------------

    val all: List<FunctionDefinition> = listOf(
        // ---- roots and powers
        fn("sqrt", 1, 1, "sqrt(x)", "Square root", "x >= 0") { a, _ ->
            a[0].requireReal(a[0].signum >= 0, "sqrt", "sqrt of negative ${a[0]} is not real")
            CalcNumber.of(Precision.sqrt(a[0].value))
        },
        fn("cbrt", 1, 1, "cbrt(x)", "Cube root (real, sign preserving)", "all real x") { a, _ ->
            CalcNumber.fromTranscendental(cbrt(a[0].toDouble()))
        },
        fn("root", 2, 2, "root(x, n)", "n-th real root", "n != 0 integer; x >= 0 when n is even") { a, _ ->
            val n = exactInteger(a[1], "root n")
            if (n.signum() == 0) throw CalcException(CalcError.DivisionByZero("root index is zero"))
            if (n.abs().compareTo(BigInteger.valueOf(1_000_000L)) > 0) {
                throw CalcException(CalcError.ResourceLimit("root index", "|n| = ${n.abs()} exceeds 10^6"))
            }
            val ni = n.toInt()
            val negative = a[0].signum < 0
            if (negative && ni % 2 == 0) {
                throw CalcException(CalcError.Domain("root", "even root of a negative number is not real"))
            }
            val magnitude = bigNthRoot(a[0].abs().value, abs(ni))
            val signed = if (negative) magnitude.negate() else magnitude
            // root(x, -n) == 1 / root(x, n)
            if (ni < 0) CalcNumber.ONE.div(CalcNumber.of(signed)) else CalcNumber.of(signed)
        },
        fn("abs", 1, 1, "abs(x)", "Absolute value", "all real x") { a, _ -> a[0].abs() },
        fn("sign", 1, 1, "sign(x)", "Sign: -1, 0 or 1", "all real x") { a, _ -> CalcNumber.of(a[0].signum) },
        fn("floor", 1, 1, "floor(x)", "Greatest integer <= x", "all real x") { a, _ ->
            CalcNumber.of(a[0].value.setScale(0, RoundingMode.FLOOR))
        },
        fn("ceil", 1, 1, "ceil(x)", "Least integer >= x", "all real x") { a, _ ->
            CalcNumber.of(a[0].value.setScale(0, RoundingMode.CEILING))
        },
        fn("round", 1, 2, "round(x [, decimals])", "Round half away from zero", "all real x") { a, _ ->
            val places = if (a.size == 2) exactInteger(a[1], "round decimals").toInt() else 0
            if (places !in -100..100) throw CalcException(CalcError.InvalidArgument("decimals", "$places out of range"))
            CalcNumber.of(a[0].value.setScale(places, RoundingMode.HALF_UP))
        },
        fn("trunc", 1, 1, "trunc(x)", "Truncate toward zero", "all real x") { a, _ ->
            CalcNumber.of(a[0].value.setScale(0, RoundingMode.DOWN))
        },

        // ---- logarithms and exponential
        fn("ln", 1, 1, "ln(x)", "Natural logarithm", "x > 0") { a, _ ->
            a[0].requirePositive("ln")
            if (a[0] == CalcNumber.ONE) CalcNumber.ZERO
            else CalcNumber.fromTranscendental(ktLn(a[0].toDouble()))
        },
        fn("log10", 1, 1, "log10(x)", "Base-10 logarithm", "x > 0") { a, _ ->
            a[0].requirePositive("log10")
            CalcNumber.fromTranscendental(Math.log10(a[0].toDouble()))
        },
        fn("log2", 1, 1, "log2(x)", "Base-2 logarithm", "x > 0") { a, _ ->
            a[0].requirePositive("log2")
            CalcNumber.fromTranscendental(Math.log(a[0].toDouble()) / Math.log(2.0))
        },
        fn("log", 2, 2, "log(x, base)", "Logarithm in an arbitrary base", "x > 0; base > 0 and base != 1") { a, _ ->
            a[0].requirePositive("log")
            a[1].requirePositive("log base")
            if (a[1] == CalcNumber.ONE) throw CalcException(CalcError.Domain("log base", "base 1 is not allowed"))
            CalcNumber.fromTranscendental(
                Math.log(a[0].toDouble()) / Math.log(a[1].toDouble()),
            )
        },
        fn("exp", 1, 1, "exp(x)", "e raised to x", "all real x (magnitude bounded by double range)") { a, _ ->
            val d = a[0].toDouble()
            if (d > 709.78) throw CalcException(CalcError.Overflow("exp($d) exceeds the representable range"))
            val r = exp(d)
            if (r == 0.0) throw CalcException(CalcError.Underflow("exp($d) underflows to zero"))
            CalcNumber.fromTranscendental(r)
        },

        // ---- trigonometry (angle mode aware)
        fn("sin", 1, 1, "sin(x)", "Sine", "all real x, interpreted in the active angle mode") { a, m ->
            CalcNumber.fromTranscendental(sin(toRadians(a[0], m)))
        },
        fn("cos", 1, 1, "cos(x)", "Cosine", "all real x, interpreted in the active angle mode") { a, m ->
            CalcNumber.fromTranscendental(cos(toRadians(a[0], m)))
        },
        fn("tan", 1, 1, "tan(x)", "Tangent", "all real x except odd multiples of 90 degrees") { a, m ->
            val rad = toRadians(a[0], m)
            requireNotSingular(rad, "tan")
            CalcNumber.fromTranscendental(tan(rad))
        },
        fn("asin", 1, 1, "asin(x)", "Inverse sine", "-1 <= x <= 1") { a, m ->
            a[0].requireReal(abs(a[0].toDouble()) <= 1.0, "asin", "|x| must be <= 1, got ${a[0]}")
            fromRadians(asin(a[0].toDouble()), m)
        },
        fn("acos", 1, 1, "acos(x)", "Inverse cosine", "-1 <= x <= 1") { a, m ->
            a[0].requireReal(abs(a[0].toDouble()) <= 1.0, "acos", "|x| must be <= 1, got ${a[0]}")
            fromRadians(acos(a[0].toDouble()), m)
        },
        fn("atan", 1, 1, "atan(x)", "Inverse tangent", "all real x") { a, m ->
            fromRadians(atan(a[0].toDouble()), m)
        },
        fn("atan2", 2, 2, "atan2(y, x)", "Angle of the point (x, y)", "not both arguments zero") { a, m ->
            if (a[0].isZero && a[1].isZero) {
                throw CalcException(CalcError.Domain("atan2", "atan2(0, 0) is undefined"))
            }
            fromRadians(atan2(a[0].toDouble(), a[1].toDouble()), m)
        },

        // ---- hyperbolic
        fn("sinh", 1, 1, "sinh(x)", "Hyperbolic sine", "all real x") { a, _ ->
            CalcNumber.fromTranscendental(sinh(a[0].toDouble()))
        },
        fn("cosh", 1, 1, "cosh(x)", "Hyperbolic cosine", "all real x") { a, _ ->
            CalcNumber.fromTranscendental(cosh(a[0].toDouble()))
        },
        fn("tanh", 1, 1, "tanh(x)", "Hyperbolic tangent", "all real x") { a, _ ->
            CalcNumber.fromTranscendental(tanh(a[0].toDouble()))
        },
        fn("asinh", 1, 1, "asinh(x)", "Inverse hyperbolic sine", "all real x") { a, _ ->
            val d = a[0].toDouble()
            CalcNumber.fromTranscendental(Math.log(d + Math.sqrt(d * d + 1.0)))
        },
        fn("acosh", 1, 1, "acosh(x)", "Inverse hyperbolic cosine", "x >= 1") { a, _ ->
            val d = a[0].toDouble()
            a[0].requireReal(d >= 1.0, "acosh", "x must be >= 1, got ${a[0]}")
            CalcNumber.fromTranscendental(Math.log(d + Math.sqrt(d * d - 1.0)))
        },
        fn("atanh", 1, 1, "atanh(x)", "Inverse hyperbolic tangent", "-1 < x < 1") { a, _ ->
            val d = a[0].toDouble()
            a[0].requireReal(abs(d) < 1.0, "atanh", "|x| must be < 1, got ${a[0]}")
            CalcNumber.fromTranscendental(0.5 * Math.log((1 + d) / (1 - d)))
        },

        // ---- angle conversion
        fn("deg", 1, 1, "deg(rad)", "Radians to degrees", "all real x") { a, _ ->
            a[0].times(CalcNumber.of(180)).div(ConstantRegistry.PI.value)
        },
        fn("rad", 1, 1, "rad(deg)", "Degrees to radians", "all real x") { a, _ ->
            a[0].times(ConstantRegistry.PI.value).div(CalcNumber.of(180))
        },

        // ---- combinatorics and integer arithmetic
        fn("fact", 1, 1, "fact(n)", "Factorial n!", "integer n, 0 <= n <= 10000") { a, _ ->
            CalcNumber.of(IntegerMath.factorial(exactInteger(a[0], "fact")))
        },
        fn("ncr", 2, 2, "ncr(n, r)", "Combinations: ways to choose r from n", "integers, 0 <= r <= n") { a, _ ->
            CalcNumber.of(IntegerMath.combinations(exactInteger(a[0], "n"), exactInteger(a[1], "r")))
        },
        fn("npr", 2, 2, "npr(n, r)", "Permutations: ordered arrangements", "integers, 0 <= r <= n") { a, _ ->
            CalcNumber.of(IntegerMath.permutations(exactInteger(a[0], "n"), exactInteger(a[1], "r")))
        },
        fn("gcd", 2, Int.MAX_VALUE, "gcd(a, b, ...)", "Greatest common divisor", "integers") { a, _ ->
            CalcNumber.of(IntegerMath.gcd(*a.map { exactInteger(it, "gcd") }.toTypedArray()))
        },
        fn("lcm", 2, Int.MAX_VALUE, "lcm(a, b, ...)", "Least common multiple", "integers") { a, _ ->
            CalcNumber.of(IntegerMath.lcm(*a.map { exactInteger(it, "lcm") }.toTypedArray()))
        },
        fn("mod", 2, 2, "mod(a, b)", "Remainder of a divided by b", "b != 0") { a, _ -> a[0].mod(a[1]) },
        fn("hypot", 2, 2, "hypot(a, b)", "sqrt(a^2 + b^2) without intermediate overflow", "all real") { a, _ ->
            CalcNumber.of(Math.hypot(a[0].toDouble(), a[1].toDouble()))
        },
        fn("min", 2, Int.MAX_VALUE, "min(a, b, ...)", "Smallest argument", "all real") { a, _ ->
            a.minByOrNull { it.value }!!
        },
        fn("max", 2, Int.MAX_VALUE, "max(a, b, ...)", "Largest argument", "all real") { a, _ ->
            a.maxByOrNull { it.value }!!
        },
    )

    private val byName: Map<String, FunctionDefinition> = all.associateBy { it.name.lowercase() }

    fun find(name: String): FunctionDefinition? = byName[name.lowercase()]

    private fun fn(
        name: String,
        minArgs: Int,
        maxArgs: Int,
        signature: String,
        description: String,
        domain: String,
        evaluate: (List<CalcNumber>, AngleMode) -> CalcNumber,
    ): FunctionDefinition = FunctionDefinition(name, minArgs, maxArgs, signature, description, domain, evaluate)
}
