package com.shohankhan.axiomcalc.math.parser

import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * Abstract syntax tree of the expression language.
 *
 * The tree is immutable and carries source positions so the evaluator and the
 * UI can both explain *where* something went wrong.
 *
 * Operator precedence and associativity (lowest to highest binding power):
 *
 * ```
 *   1  +  -                 left associative
 *   2  *  /  mod            left associative
 *   3  juxtaposition        left associative   (2pi, 3(4+5), (2)(3))
 *   4  unary + -            prefix
 *   5  ^                    RIGHT associative   (2^3^2 == 2^(3^2) == 512)
 *   6  !  %                 postfix
 *   7  literals, constants, function calls, parentheses
 * ```
 *
 * Consequence, matching mainstream mathematical convention: `-2^2 == -4`
 * because exponentiation binds tighter than unary minus, while `2^-3` is
 * legal because the right operand of `^` is parsed at unary level.
 */
sealed interface Expr {
    val position: Int

    data class Num(val value: CalcNumber, override val position: Int) : Expr
    data class Const(val name: String, override val position: Int) : Expr
    data class Ans(override val position: Int) : Expr
    data class Neg(val operand: Expr, override val position: Int) : Expr
    data class Add(val left: Expr, val right: Expr, override val position: Int) : Expr
    data class Sub(val left: Expr, val right: Expr, override val position: Int) : Expr
    data class Mul(val left: Expr, val right: Expr, override val position: Int) : Expr
    data class Div(val left: Expr, val right: Expr, override val position: Int) : Expr
    data class ModOp(val left: Expr, val right: Expr, override val position: Int) : Expr
    data class Pow(val base: Expr, val exponent: Expr, override val position: Int) : Expr
    data class Fact(val operand: Expr, override val position: Int) : Expr
    data class Pct(val operand: Expr, override val position: Int) : Expr
    data class FuncCall(val name: String, val args: List<Expr>, override val position: Int) : Expr
}
