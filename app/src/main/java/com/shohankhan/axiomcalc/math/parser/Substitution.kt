package com.shohankhan.axiomcalc.math.parser

import com.shohankhan.axiomcalc.math.precision.CalcNumber

/**
 * Symbolic substitution of a single identifier by a numeric literal.
 *
 * This is the mechanism solvers use to keep displayed steps and computed values
 * identical: the *same* parsed tree is shown to the user (with the value
 * substituted in) and evaluated by the engine. No second implementation of any
 * formula exists anywhere in the codebase.
 */
fun Expr.substitute(name: String, value: CalcNumber): Expr = when (this) {
    is Expr.Num -> this
    is Expr.Ans -> this
    is Expr.Const -> if (this.name.equals(name, ignoreCase = true)) Expr.Num(value, position) else this
    is Expr.Neg -> Expr.Neg(operand.substitute(name, value), position)
    is Expr.Add -> Expr.Add(left.substitute(name, value), right.substitute(name, value), position)
    is Expr.Sub -> Expr.Sub(left.substitute(name, value), right.substitute(name, value), position)
    is Expr.Mul -> Expr.Mul(left.substitute(name, value), right.substitute(name, value), position)
    is Expr.Div -> Expr.Div(left.substitute(name, value), right.substitute(name, value), position)
    is Expr.ModOp -> Expr.ModOp(left.substitute(name, value), right.substitute(name, value), position)
    is Expr.Pow -> Expr.Pow(base.substitute(name, value), exponent.substitute(name, value), position)
    is Expr.Fact -> Expr.Fact(operand.substitute(name, value), position)
    is Expr.Pct -> Expr.Pct(operand.substitute(name, value), position)
    is Expr.FuncCall -> Expr.FuncCall(name, args.map { it.substitute(name, value) }, position)
}

/** Renders a tree back to canonical infix text (used for substituted steps). */
fun Expr.toSourceString(): String = when (this) {
    is Expr.Num -> value.toString()
    is Expr.Ans -> "ans"
    is Expr.Const -> name
    is Expr.Neg -> "(-${operand.toSourceString()})"
    is Expr.Add -> "(${left.toSourceString()} + ${right.toSourceString()})"
    is Expr.Sub -> "(${left.toSourceString()} - ${right.toSourceString()})"
    is Expr.Mul -> "(${left.toSourceString()} * ${right.toSourceString()})"
    is Expr.Div -> "(${left.toSourceString()} / ${right.toSourceString()})"
    is Expr.ModOp -> "(${left.toSourceString()} mod ${right.toSourceString()})"
    is Expr.Pow -> "(${base.toSourceString()}^${exponent.toSourceString()})"
    is Expr.Fact -> "${operand.toSourceString()}!"
    is Expr.Pct -> "${operand.toSourceString()}%"
    is Expr.FuncCall -> "$name(${args.joinToString(", ") { it.toSourceString() }})"
}
