package com.shohankhan.axiomcalc.math.evaluator

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.AngleMode
import com.shohankhan.axiomcalc.math.constants.ConstantRegistry
import com.shohankhan.axiomcalc.math.functions.FunctionRegistry
import com.shohankhan.axiomcalc.math.parser.Expr
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision
import com.shohankhan.axiomcalc.math.solvers.IntegerMath
import java.math.BigDecimal

/**
 * Tree-walking evaluator over [Expr].
 *
 * Semantics that are fixed here and documented in MATHEMATICS_SPEC.md:
 *
 *  * `0^0 == 1` (the convention used by combinatorics and by every mainstream
 *    computer algebra system).
 *  * `0` raised to a negative power is a division by zero, not infinity.
 *  * A negative base with a fractional exponent is a domain error in real mode;
 *    complex results are produced explicitly by the Math Lab complex tools and
 *    by the quadratic solver, never implicitly here.
 *  * Integer exponents are evaluated with exact decimal arithmetic; fractional
 *    exponents and all transcendentals are evaluated in double precision and
 *    re-rounded to the working context.
 *  * Postfix `%` means "divide by 100" in the expression language. The
 *    context-dependent percentage *key* of the Standard calculator is a separate,
 *    documented layer ([com.shohankhan.axiomcalc.domain.calculator.PercentagePolicy]).
 */
class Evaluator(
    private val angleMode: AngleMode = AngleMode.DEGREES,
    private val ans: CalcNumber = CalcNumber.ZERO,
) {

    fun evaluate(root: Expr): CalcNumber = eval(root, 0)

    private fun eval(node: Expr, depth: Int): CalcNumber {
        if (depth > Precision.MAX_EXPRESSION_DEPTH) {
            Precision.resourceLimit("expression depth", "$depth > ${Precision.MAX_EXPRESSION_DEPTH}")
        }
        val d = depth + 1
        return when (node) {
            is Expr.Num -> node.value
            is Expr.Ans -> ans
            is Expr.Const -> ConstantRegistry.find(node.name)?.value
                ?: throw CalcException(CalcError.UnknownFunction(node.name))
            is Expr.Neg -> eval(node.operand, d).negate()
            is Expr.Add -> eval(node.left, d) + eval(node.right, d)
            is Expr.Sub -> eval(node.left, d) - eval(node.right, d)
            is Expr.Mul -> eval(node.left, d) * eval(node.right, d)
            is Expr.Div -> eval(node.left, d) / eval(node.right, d)
            is Expr.ModOp -> eval(node.left, d).mod(eval(node.right, d))
            is Expr.Pow -> power(eval(node.base, d), eval(node.exponent, d))
            is Expr.Fact -> {
                val operand = eval(node.operand, d)
                val asInt = operand.toBigIntegerOrNull()
                    ?: throw CalcException(CalcError.Domain("factorial", "$operand is not an integer"))
                CalcNumber.of(IntegerMath.factorial(asInt))
            }
            is Expr.Pct -> eval(node.operand, d) / CalcNumber.of(100)
            is Expr.FuncCall -> call(node, d)
        }
    }

    private fun call(node: Expr.FuncCall, depth: Int): CalcNumber {
        val def = FunctionRegistry.find(node.name)
            ?: throw CalcException(CalcError.UnknownFunction(node.name))
        if (node.args.size < def.minArgs || node.args.size > def.maxArgs) {
            val expected = if (def.maxArgs == Int.MAX_VALUE) "${def.minArgs} or more" else "${def.minArgs}..${def.maxArgs}"
            throw CalcException(
                CalcError.InvalidArgument(
                    def.name,
                    "${node.name}() takes $expected argument(s) but received ${node.args.size}",
                ),
            )
        }
        val values = node.args.map { eval(it, depth) }
        return def.evaluate(values, angleMode)
    }

    private fun power(base: CalcNumber, exponent: CalcNumber): CalcNumber {
        val expIntegral = exponent.isIntegral
        val expBi = exponent.toBigIntegerOrNull()

        if (base.isZero) {
            return when {
                exponent.isZero -> CalcNumber.ONE            // documented: 0^0 == 1
                exponent.isNegative -> throw CalcException(
                    CalcError.DivisionByZero("zero raised to a negative power"),
                )
                else -> CalcNumber.ZERO
            }
        }

        if (expIntegral && expBi != null) {
            val e = expBi
            if (e.bitLength() > 40 || e.abs().compareTo(java.math.BigInteger.valueOf(Precision.MAX_EXPONENT)) > 0) {
                Precision.resourceLimit("exponent magnitude", "|exponent| = ${e.abs()}")
            }
            val ei = e.toInt()
            return try {
                if (ei == 0) CalcNumber.ONE
                else CalcNumber.of(base.value.pow(ei, Precision.MC))
            } catch (ex: ArithmeticException) {
                throw CalcException(CalcError.Overflow("pow(${base}, ${exponent})"))
            }
        }

        if (base.isNegative) {
            throw CalcException(
                CalcError.Domain(
                    "power",
                    "negative base ${base} with non-integer exponent ${exponent} has no real result; " +
                        "use the Math Lab complex tools for the complex value",
                ),
            )
        }

        val b = base.toDouble()
        val e = exponent.toDouble()
        val result = Math.pow(b, e)
        if (result.isInfinite()) throw CalcException(CalcError.Overflow("pow($base, $exponent)"))
        if (result == 0.0) throw CalcException(CalcError.Underflow("pow($base, $exponent)"))
        return CalcNumber.fromTranscendental(result)
    }
}
