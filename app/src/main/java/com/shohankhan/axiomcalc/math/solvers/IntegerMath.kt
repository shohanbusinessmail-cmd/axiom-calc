package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.Precision
import java.math.BigInteger

/**
 * Exact integer arithmetic: factorials, gcd/lcm, primality and factorization.
 *
 * Everything here is exact [BigInteger] arithmetic with explicit guard rails so
 * that hostile or mistaken input cannot consume unbounded CPU or memory:
 *
 *  * `n!` is limited to [Precision.MAX_FACTORIAL] (10 000!, ~35 660 digits).
 *  * factorization is limited to inputs below 10^18 (trial division + Pollard
 *    rho would be overkill for a calculator; trial division to sqrt(n) of a
 *    10^18 bound is at most 10^9 steps worst case, so we further cap the step
 *    budget and report [CalcError.ResourceLimit] instead of hanging).
 *  * primality uses deterministic Miller-Rabin for 64-bit inputs, which is
 *    exact for every n < 3,317,044,064,679,887,385,961,981.
 */
object IntegerMath {

    private const val FACTORIZATION_LIMIT = 1_000_000_000_000_000_000L // 10^18
    private const val FACTORIZATION_STEP_BUDGET = 20_000_000L

    fun factorial(n: BigInteger): BigInteger {
        if (n.signum() < 0) {
            throw CalcException(CalcError.Domain("factorial", "n = $n is negative; n! is defined for n >= 0"))
        }
        if (n.bitLength() > 32 || n.toInt() > Precision.MAX_FACTORIAL) {
            throw CalcException(
                CalcError.ResourceLimit("factorial", "n = $n exceeds the limit ${Precision.MAX_FACTORIAL}"),
            )
        }
        var acc = BigInteger.ONE
        for (i in 2..n.toInt()) acc = acc.multiply(BigInteger.valueOf(i.toLong()))
        guardDigits(acc, "factorial")
        return acc
    }

    fun gcd(a: BigInteger, b: BigInteger): BigInteger = a.abs().gcd(b.abs())

    fun gcd(vararg values: BigInteger): BigInteger {
        require(values.isNotEmpty())
        return values.reduce { acc, v -> acc.gcd(v.abs()) }.abs()
    }

    fun lcm(a: BigInteger, b: BigInteger): BigInteger {
        if (a.signum() == 0 || b.signum() == 0) return BigInteger.ZERO
        val g = a.abs().gcd(b.abs())
        val result = a.abs().divide(g).multiply(b.abs())
        guardDigits(result, "lcm")
        return result
    }

    fun lcm(vararg values: BigInteger): BigInteger =
        values.fold(BigInteger.ONE) { acc, v -> if (acc.signum() == 0) acc else lcm(acc, v) }

    fun permutations(n: BigInteger, r: BigInteger): BigInteger {
        requireNonNegative(n, "n"); requireNonNegative(r, "r")
        if (r > n) throw CalcException(CalcError.InvalidArgument("r", "r = $r > n = $n"))
        if (n.bitLength() > 32 || n.toInt() > Precision.MAX_FACTORIAL) {
            throw CalcException(CalcError.ResourceLimit("permutations", "n = $n is too large"))
        }
        var acc = BigInteger.ONE
        val ni = n.toInt(); val ri = r.toInt()
        for (i in 0 until ri) acc = acc.multiply(BigInteger.valueOf((ni - i).toLong()))
        guardDigits(acc, "permutations")
        return acc
    }

    fun combinations(n: BigInteger, r: BigInteger): BigInteger {
        requireNonNegative(n, "n"); requireNonNegative(r, "r")
        if (r > n) throw CalcException(CalcError.InvalidArgument("r", "r = $r > n = $n"))
        if (n.bitLength() > 32 || n.toInt() > Precision.MAX_FACTORIAL) {
            throw CalcException(CalcError.ResourceLimit("combinations", "n = $n is too large"))
        }
        val ni = n.toInt()
        val ri = minOf(r.toInt(), ni - r.toInt())
        var acc = BigInteger.ONE
        for (i in 0 until ri) {
            acc = acc.multiply(BigInteger.valueOf((ni - i).toLong()))
                .divide(BigInteger.valueOf((i + 1).toLong()))
        }
        guardDigits(acc, "combinations")
        return acc
    }

    // ------------------------------------------------------------------ primes

    fun isPrime(n: BigInteger): Boolean {
        if (n.signum() < 0) return false
        if (n.bitLength() <= 64) return isPrimeLong(n.toLong())
        return n.isProbablePrime(40)
    }

    fun isPrimeLong(n: Long): Boolean {
        if (n < 2) return false
        for (p in intArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)) {
            if (n % p == 0L) return n == p.toLong()
        }
        var d = n - 1
        var s = 0
        while (d % 2 == 0L) { d /= 2; s++ }
        for (a in longArrayOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37)) {
            if (millerRabinFails(n, a, d, s)) return false
        }
        return true
    }

    private fun millerRabinFails(n: Long, a: Long, d: Long, s: Int): Boolean {
        var x = modPow(a, d, n)
        if (x == 1L || x == n - 1) return false
        repeat(s - 1) {
            x = modMul(x, x, n)
            if (x == n - 1) return false
        }
        return true
    }

    private fun modMul(a: Long, b: Long, m: Long): Long =
        BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).mod(BigInteger.valueOf(m)).toLong()

    private fun modPow(base: Long, exp: Long, m: Long): Long =
        BigInteger.valueOf(base).modPow(BigInteger.valueOf(exp), BigInteger.valueOf(m)).toLong()

    /**
     * Prime factorization as ascending (prime, exponent) pairs.
     * Bounded by [FACTORIZATION_LIMIT] and a step budget so the UI can never
     * freeze on a pathological input.
     */
    fun primeFactorization(n: BigInteger): List<Pair<BigInteger, Int>> {
        if (n.signum() == 0) throw CalcException(CalcError.InvalidArgument("n", "0 has no prime factorization"))
        var remaining = n.abs()
        if (remaining.bitLength() > 63 || remaining.toLong() > FACTORIZATION_LIMIT) {
            throw CalcException(
                CalcError.ResourceLimit("factorization", "n = $n exceeds the supported bound 10^18"),
            )
        }
        val out = ArrayList<Pair<BigInteger, Int>>()
        var steps = 0L
        var f = BigInteger.valueOf(2L)
        val limitStep = {
            steps++
            if (steps > FACTORIZATION_STEP_BUDGET) {
                throw CalcException(CalcError.ResourceLimit("factorization steps", "budget exhausted for n = $n"))
            }
        }
        while (f.multiply(f) <= remaining) {
            limitStep()
            if (remaining.mod(f) == BigInteger.ZERO) {
                var count = 0
                while (remaining.mod(f) == BigInteger.ZERO) { remaining = remaining.divide(f); count++ }
                out += f to count
            }
            f = if (f == BigInteger.valueOf(2L)) BigInteger.valueOf(3) else f.add(BigInteger.valueOf(2L))
        }
        if (remaining > BigInteger.ONE) out += remaining to 1
        return out
    }

    private fun requireNonNegative(v: BigInteger, name: String) {
        if (v.signum() < 0) throw CalcException(CalcError.Domain(name, "$name = $v must be >= 0"))
    }

    private fun guardDigits(value: BigInteger, what: String) {
        val digits = value.toString().length
        if (digits > Precision.MAX_INTEGER_DIGITS) {
            throw CalcException(CalcError.Overflow("$what produced $digits digits (> ${Precision.MAX_INTEGER_DIGITS})"))
        }
    }
}
