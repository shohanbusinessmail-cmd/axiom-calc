package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import java.math.BigInteger

/**
 * Integer base conversion and integer arithmetic in arbitrary bases 2..36.
 *
 * Documented rules (MATHEMATICS_SPEC.md §Number systems):
 *  * only integer values are supported; a fractional literal is rejected.
 *  * digits are validated against the target base before any conversion.
 *  * negative values are represented with a leading minus sign in every base.
 *  * values are exact BigInteger; a digit guard rail bounds memory.
 */
object Bases {

    val SUPPORTED: List<Int> = listOf(2, 8, 10, 16)

    fun requireSupported(base: Int) {
        if (base !in 2..36) throw CalcException(CalcError.InvalidArgument("base", "base $base is outside 2..36"))
    }

    /** Converts a digit string in [fromBase] to a digit string in [toBase]. */
    fun convert(text: String, fromBase: Int, toBase: Int): String {
        requireSupported(fromBase); requireSupported(toBase)
        val value = parse(text, fromBase)
        return render(value, toBase)
    }

    /** Parses digits in [base] into an exact integer, validating every digit. */
    fun parse(text: String, base: Int): BigInteger {
        requireSupported(base)
        val t = text.trim().replace(" ", "").replace("_", "")
        if (t.isEmpty()) throw CalcException(CalcError.InvalidArgument("value", "empty input"))
        val (negative, body) = when {
            t.startsWith("-") -> true to t.substring(1)
            t.startsWith("+") -> false to t.substring(1)
            else -> false to t
        }
        val cleaned = stripPrefix(body, base)
        if (cleaned.isEmpty()) throw CalcException(CalcError.InvalidArgument("value", "'$text' has no digits"))
        var acc = BigInteger.ZERO
        val radix = BigInteger.valueOf(base.toLong())
        for (ch in cleaned) {
            val digit = digitValue(ch)
                ?: throw CalcException(CalcError.InvalidArgument("digit", "'$ch' is not a valid digit"))
            if (digit >= base) {
                throw CalcException(
                    CalcError.InvalidArgument("digit", "'$ch' (value $digit) is not valid in base $base"),
                )
            }
            acc = acc.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }
        if (acc.toString().length > com.shohankhan.axiomcalc.math.precision.Precision.MAX_INTEGER_DIGITS) {
            throw CalcException(CalcError.Overflow("base conversion magnitude"))
        }
        return if (negative) acc.negate() else acc
    }

    private fun stripPrefix(body: String, base: Int): String = when (base) {
        16 -> if (body.startsWith("0x", true)) body.substring(2) else body
        2 -> if (body.startsWith("0b", true)) body.substring(2) else body
        8 -> if (body.startsWith("0o", true)) body.substring(2) else body
        else -> body
    }

    private fun digitValue(ch: Char): Int? = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'z' -> ch - 'a' + 10
        in 'A'..'Z' -> ch - 'A' + 10
        else -> null
    }

    /** Renders an exact integer in [base] with upper-case digits above 9. */
    fun render(value: BigInteger, base: Int): String {
        requireSupported(base)
        return value.toString(base).uppercase()
    }

    fun add(a: String, b: String, base: Int): String = render(parse(a, base).add(parse(b, base)), base)
    fun subtract(a: String, b: String, base: Int): String = render(parse(a, base).subtract(parse(b, base)), base)
    fun multiply(a: String, b: String, base: Int): String = render(parse(a, base).multiply(parse(b, base)), base)
    fun divide(a: String, b: String, base: Int): String {
        val divisor = parse(b, base)
        if (divisor.signum() == 0) throw CalcException(CalcError.DivisionByZero("integer division by zero"))
        return render(parse(a, base).divide(divisor), base)
    }

    /** Integer division plus remainder, both rendered in [base]. */
    fun divideWithRemainder(a: String, b: String, base: Int): Pair<String, String> {
        val divisor = parse(b, base)
        if (divisor.signum() == 0) throw CalcException(CalcError.DivisionByZero("integer division by zero"))
        val (q, r) = parse(a, base).divideAndRemainder(divisor)
        return render(q, base) to render(r, base)
    }
}
