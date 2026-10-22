package com.shohankhan.axiomcalc.math.parser

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.lexer.Lexer
import com.shohankhan.axiomcalc.math.lexer.Token
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.precision.Precision

/**
 * Recursive-descent (precedence-climbing) parser implementing the grammar of
 * [Expr]. The class is stateless apart from the token cursor, which makes it
 * safe to run on any thread and trivial to unit test.
 */
class Parser(private val tokens: List<Token>) {

    private var index = 0
    private var nodes = 0

    private fun peek(): Token = tokens[index]
    private fun advance(): Token = tokens[index++]

    private fun newNode(): Int {
        nodes++
        if (nodes > Precision.MAX_EXPRESSION_NODES) {
            Precision.resourceLimit("expression nodes", "$nodes > ${Precision.MAX_EXPRESSION_NODES}")
        }
        return nodes
    }

    /** True when [t] can begin a primary expression (used for juxtaposition). */
    private fun startsPrimary(t: Token): Boolean = when (t) {
        is Token.Number, is Token.Identifier, is Token.LParen -> true
        else -> false
    }

    fun parseExpression(): Expr {
        val e = parseAddSub()
        val t = peek()
        if (t !is Token.End) {
            throw CalcException(CalcError.Syntax(t.position, "unexpected token after complete expression"))
        }
        return e
    }

    // ---- 1: additive -------------------------------------------------------
    private fun parseAddSub(): Expr {
        var left = parseMulDiv()
        while (true) {
            val p = peek().position
            left = when (advance()) {
                is Token.Plus -> Expr.Add(left, parseMulDiv(), p).also { newNode() }
                is Token.Minus -> Expr.Sub(left, parseMulDiv(), p).also { newNode() }
                else -> { index--; return left }
            }
        }
    }

    // ---- 2: multiplicative -------------------------------------------------
    private fun parseMulDiv(): Expr {
        var left = parseImplicit()
        while (true) {
            val p = peek().position
            left = when (advance()) {
                is Token.Star -> Expr.Mul(left, parseImplicit(), p).also { newNode() }
                is Token.Slash -> Expr.Div(left, parseImplicit(), p).also { newNode() }
                is Token.Mod -> Expr.ModOp(left, parseImplicit(), p).also { newNode() }
                else -> { index--; return left }
            }
        }
    }

    // ---- 3: juxtaposition (documented implicit multiplication) -------------
    private fun parseImplicit(): Expr {
        var left = parseUnary()
        while (startsPrimary(peek())) {
            val p = peek().position
            val right = parseUnary()
            left = Expr.Mul(left, right, p).also { newNode() }
        }
        return left
    }

    // ---- 4: unary sign -----------------------------------------------------
    private fun parseUnary(): Expr {
        val t = peek()
        return when (t) {
            is Token.Minus -> { advance(); Expr.Neg(parseUnary(), t.position).also { newNode() } }
            is Token.Plus -> { advance(); parseUnary() }
            else -> parsePower()
        }
    }

    // ---- 5: exponentiation, right associative ------------------------------
    private fun parsePower(): Expr {
        val left = parsePostfix()
        val t = peek()
        return if (t is Token.Caret) {
            advance()
            Expr.Pow(left, parseUnary(), t.position).also { newNode() }
        } else {
            left
        }
    }

    // ---- 6: postfix ! and % ------------------------------------------------
    private fun parsePostfix(): Expr {
        var e = parsePrimary()
        while (true) {
            when (peek()) {
                is Token.Bang -> { val p = advance().position; e = Expr.Fact(e, p).also { newNode() } }
                is Token.Percent -> { val p = advance().position; e = Expr.Pct(e, p).also { newNode() } }
                else -> return e
            }
        }
    }

    // ---- 7: primary --------------------------------------------------------
    private fun parsePrimary(): Expr {
        val t = advance()
        return when (t) {
            is Token.Number -> Expr.Num(CalcNumber.parse(t.text), t.position).also { newNode() }
            is Token.LParen -> {
                val inner = parseAddSub()
                val close = peek()
                if (close !is Token.RParen) {
                    throw CalcException(CalcError.UnbalancedParentheses(close.position))
                }
                advance()
                inner
            }
            is Token.Identifier -> {
                if (peek() is Token.LParen) {
                    advance() // consume '('
                    val args = ArrayList<Expr>(2)
                    if (peek() !is Token.RParen) {
                        args += parseAddSub()
                        while (peek() is Token.Comma) {
                            advance()
                            args += parseAddSub()
                        }
                    }
                    val close = peek()
                    if (close !is Token.RParen) {
                        throw CalcException(CalcError.UnbalancedParentheses(close.position))
                    }
                    advance()
                    Expr.FuncCall(t.name, args, t.position).also { newNode() }
                } else if (t.name.equals("ans", ignoreCase = true)) {
                    Expr.Ans(t.position).also { newNode() }
                } else {
                    Expr.Const(t.name, t.position).also { newNode() }
                }
            }
            else -> throw CalcException(
                CalcError.Syntax(t.position, "expected a value but found '${describe(t)}'"),
            )
        }
    }

    private fun describe(t: Token): String = when (t) {
        is Token.End -> "end of expression"
        is Token.Number -> t.text
        is Token.Identifier -> t.name
        is Token.Plus -> "+"
        is Token.Minus -> "-"
        is Token.Star -> "*"
        is Token.Slash -> "/"
        is Token.Caret -> "^"
        is Token.Percent -> "%"
        is Token.Bang -> "!"
        is Token.Mod -> "mod"
        is Token.LParen -> "("
        is Token.RParen -> ")"
        is Token.Comma -> ","
    }

    companion object {
        fun parse(source: String): Expr = Parser(Lexer.tokenize(source)).parseExpression()
    }
}
