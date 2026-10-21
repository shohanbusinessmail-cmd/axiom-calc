package com.shohankhan.axiomcalc.math.lexer

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException

/**
 * Lexical tokens of the expression grammar (see MATHEMATICS_SPEC.md §Grammar).
 *
 * Every token carries its 0-based [position] in the source text so syntax errors
 * can point the user at the offending character.
 */
sealed class Token {
    abstract val position: Int

    data class Number(val text: String, override val position: Int) : Token()
    data class Identifier(val name: String, override val position: Int) : Token()
    data class Plus(override val position: Int) : Token()
    data class Minus(override val position: Int) : Token()
    data class Star(override val position: Int) : Token()
    data class Slash(override val position: Int) : Token()
    data class Caret(override val position: Int) : Token()
    data class Percent(override val position: Int) : Token()
    data class Bang(override val position: Int) : Token()
    data class Mod(override val position: Int) : Token()
    data class LParen(override val position: Int) : Token()
    data class RParen(override val position: Int) : Token()
    data class Comma(override val position: Int) : Token()
    data class End(override val position: Int) : Token()
}

/**
 * Tokenizer for the Axiom Calc expression language.
 *
 * Deliberate lexical decisions, all documented in MATHEMATICS_SPEC.md:
 *
 *  1. Scientific notation wins over the Euler constant only when unambiguous:
 *     `2e3` and `2e-3` are literals; `2e` is `2 * e`; `2*e-3` is `2*e-3`.
 *  2. `mod` is a reserved word (infix remainder operator) and therefore cannot
 *     be used as a variable or function name.
 *  3. A small set of typographic characters that users paste from documents is
 *     normalized: `×` `÷` `−` `·` `π` `√` `τ` `φ`. Everything else unknown is a
 *     syntax error, never an implicit skip.
 *  4. Whitespace is insignificant.
 */
class Lexer(private val source: String) {

    private var pos = 0

    fun tokenize(): List<Token> {
        val out = ArrayList<Token>(source.length / 2 + 4)
        while (pos < source.length) {
            val c = source[pos]
            when {
                c.isWhitespace() -> pos++

                c.isDigit() || (c == '.' && pos + 1 < source.length && source[pos + 1].isDigit()) ->
                    out += readNumber()

                c.isLetter() || c == '_' -> out += readIdentifier()

                else -> {
                    val t: Token? = when (c) {
                        '+' -> Token.Plus(pos)
                        '-', '−' -> Token.Minus(pos)
                        '*', '×', '·' -> Token.Star(pos)
                        '/', '÷' -> Token.Slash(pos)
                        '^' -> Token.Caret(pos)
                        '%' -> Token.Percent(pos)
                        '!' -> Token.Bang(pos)
                        '(' -> Token.LParen(pos)
                        ')' -> Token.RParen(pos)
                        ',' -> Token.Comma(pos)
                        '√' -> { out += Token.Identifier("sqrt", pos); pos++; null }
                        'π' -> { out += Token.Identifier("pi", pos); pos++; null }
                        'τ' -> { out += Token.Identifier("tau", pos); pos++; null }
                        'φ' -> { out += Token.Identifier("phi", pos); pos++; null }
                        else -> throw CalcException(
                            CalcError.Syntax(pos, "unexpected character '$c' (U+${c.code.toString(16).uppercase()})"),
                        )
                    }
                    if (t != null) {
                        out += t
                        pos++
                    }
                }
            }
        }
        out += Token.End(pos)
        return out
    }

    private fun readNumber(): Token {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        if (pos < source.length && source[pos] == '.') {
            pos++
            while (pos < source.length && source[pos].isDigit()) pos++
            // A second decimal point is a malformed literal such as "1.2.3".
            // It must be an error, never a silent implicit multiplication.
            if (pos < source.length && source[pos] == '.') {
                throw CalcException(
                    CalcError.Syntax(pos, "malformed numeric literal: more than one decimal point"),
                )
            }
        }
        // Scientific notation: only when `e`/`E` is directly followed by a digit
        // or by a sign and a digit. This keeps `2e` meaning `2 * e`.
        if (pos < source.length && (source[pos] == 'e' || source[pos] == 'E')) {
            var probe = pos + 1
            if (probe < source.length && (source[probe] == '+' || source[probe] == '-')) probe++
            if (probe < source.length && source[probe].isDigit()) {
                pos = probe
                while (pos < source.length && source[pos].isDigit()) pos++
            }
        }
        return Token.Number(source.substring(start, pos), start)
    }

    private fun readIdentifier(): Token {
        val start = pos
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_')) pos++
        val name = source.substring(start, pos)
        return if (name == "mod") Token.Mod(start) else Token.Identifier(name, start)
    }

    companion object {
        fun tokenize(source: String): List<Token> = Lexer(source).tokenize()
    }
}
