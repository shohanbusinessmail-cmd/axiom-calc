package com.shohankhan.axiomcalc.feature.calculator

import com.shohankhan.axiomcalc.core.ui.KeyRole

/**
 * Every key the calculators expose.
 *
 * Modelling keys as an enum rather than as inline lambdas means the key→engine
 * mapping lives in one dispatch function that unit tests can exercise without a
 * Compose runtime, and the keypad layouts below are plain data that tests can
 * assert on (no duplicate keys, no unimplemented keys).
 */
enum class CalcKey(
    /** Visible glyph. Icons override this for the labelled action keys. */
    val label: String,
    val role: KeyRole,
    /** TalkBack announcement; falls back to [label] when null. */
    val description: String? = null,
) {
    // digits
    D0("0", KeyRole.DIGIT, "Zero"),
    D1("1", KeyRole.DIGIT, "One"),
    D2("2", KeyRole.DIGIT, "Two"),
    D3("3", KeyRole.DIGIT, "Three"),
    D4("4", KeyRole.DIGIT, "Four"),
    D5("5", KeyRole.DIGIT, "Five"),
    D6("6", KeyRole.DIGIT, "Six"),
    D7("7", KeyRole.DIGIT, "Seven"),
    D8("8", KeyRole.DIGIT, "Eight"),
    D9("9", KeyRole.DIGIT, "Nine"),
    DECIMAL(".", KeyRole.DIGIT, "Decimal point"),

    // operators
    PLUS("+", KeyRole.OPERATOR, "Plus"),
    MINUS("−", KeyRole.OPERATOR, "Minus"),
    TIMES("×", KeyRole.OPERATOR, "Times"),
    DIVIDE("÷", KeyRole.OPERATOR, "Divided by"),
    POWER("^", KeyRole.OPERATOR, "To the power"),
    MOD("mod", KeyRole.OPERATOR, "Modulo"),
    EQUALS("=", KeyRole.EQUALS, "Equals"),

    // grouping and postfix
    OPEN("(", KeyRole.FUNCTION, "Open parenthesis"),
    CLOSE(")", KeyRole.FUNCTION, "Close parenthesis"),
    PERCENT("%", KeyRole.FUNCTION, "Percent"),
    FACTORIAL("!", KeyRole.FUNCTION, "Factorial"),
    SIGN("±", KeyRole.FUNCTION, "Toggle sign"),
    ANS("Ans", KeyRole.FUNCTION, "Previous answer"),

    // editing
    AC("AC", KeyRole.FUNCTION, "All clear"),
    CE("CE", KeyRole.FUNCTION, "Clear entry"),
    BACK("⌫", KeyRole.FUNCTION, "Backspace"),
    UNDO("↶", KeyRole.NEUTRAL, "Undo"),
    REDO("↷", KeyRole.NEUTRAL, "Redo"),
    COPY("⧉", KeyRole.NEUTRAL, "Copy result"),
    PASTE("⎘", KeyRole.NEUTRAL, "Paste from clipboard"),
    SAVE("⤓", KeyRole.NEUTRAL, "Save calculation"),

    // memory
    MC("MC", KeyRole.MEMORY, "Memory clear"),
    MR("MR", KeyRole.MEMORY, "Memory recall"),
    M_PLUS("M+", KeyRole.MEMORY, "Memory add"),
    M_MINUS("M−", KeyRole.MEMORY, "Memory subtract"),
    MS("MS", KeyRole.MEMORY, "Memory store"),

    // scientific: trigonometry
    SIN("sin", KeyRole.FUNCTION, "Sine"),
    COS("cos", KeyRole.FUNCTION, "Cosine"),
    TAN("tan", KeyRole.FUNCTION, "Tangent"),
    ASIN("sin⁻¹", KeyRole.FUNCTION, "Arc sine"),
    ACOS("cos⁻¹", KeyRole.FUNCTION, "Arc cosine"),
    ATAN("tan⁻¹", KeyRole.FUNCTION, "Arc tangent"),
    SINH("sinh", KeyRole.FUNCTION, "Hyperbolic sine"),
    COSH("cosh", KeyRole.FUNCTION, "Hyperbolic cosine"),
    TANH("tanh", KeyRole.FUNCTION, "Hyperbolic tangent"),
    DEG_MODE("DEG", KeyRole.NEUTRAL, "Angle mode: degrees or radians"),

    // scientific: powers, roots, logs
    SQUARE("x²", KeyRole.FUNCTION, "Square"),
    CUBE("x³", KeyRole.FUNCTION, "Cube"),
    SQRT("√", KeyRole.FUNCTION, "Square root"),
    CBRT("∛", KeyRole.FUNCTION, "Cube root"),
    NTH_ROOT("ⁿ√", KeyRole.FUNCTION, "Nth root"),
    INV("1/x", KeyRole.FUNCTION, "Reciprocal"),
    LN("ln", KeyRole.FUNCTION, "Natural logarithm"),
    LOG("log", KeyRole.FUNCTION, "Common logarithm"),
    EXP_FN("eˣ", KeyRole.FUNCTION, "e to the power x"),
    TEN_X("10ˣ", KeyRole.FUNCTION, "Ten to the power x"),
    EE("EE", KeyRole.FUNCTION, "Times ten to the power"),

    // scientific: constants and combinatorics
    PI("π", KeyRole.FUNCTION, "Pi"),
    E_CONST("e", KeyRole.FUNCTION, "Euler's number"),
    TAU("τ", KeyRole.FUNCTION, "Tau"),
    PHI("φ", KeyRole.FUNCTION, "Golden ratio"),
    NCR("nCr", KeyRole.FUNCTION, "Combinations"),
    NPR("nPr", KeyRole.FUNCTION, "Permutations"),
    GCD("gcd", KeyRole.FUNCTION, "Greatest common divisor"),
    LCM("lcm", KeyRole.FUNCTION, "Least common multiple"),
    ABS("abs", KeyRole.FUNCTION, "Absolute value"),
}

/**
 * Keypad layouts as data.
 *
 * The Standard calculator gets a fixed grid; the Scientific calculator has a
 * compact layout (always visible) and an expanded panel (toggled) so the screen
 * still fits a small portrait phone.
 */
object KeypadLayouts {

    /** Utility strip: memory keys. */
    val memoryRow = listOf(CalcKey.MC, CalcKey.MR, CalcKey.M_PLUS, CalcKey.M_MINUS, CalcKey.MS)

    /** Standard calculator: five rows of four plus a wide equals row. */
    val standardRows: List<List<CalcKey>> = listOf(
        listOf(CalcKey.AC, CalcKey.CE, CalcKey.BACK, CalcKey.DIVIDE),
        listOf(CalcKey.D7, CalcKey.D8, CalcKey.D9, CalcKey.TIMES),
        listOf(CalcKey.D4, CalcKey.D5, CalcKey.D6, CalcKey.MINUS),
        listOf(CalcKey.D1, CalcKey.D2, CalcKey.D3, CalcKey.PLUS),
        listOf(CalcKey.SIGN, CalcKey.D0, CalcKey.DECIMAL, CalcKey.PERCENT),
    )

    /** Row under the grid: parentheses, previous answer, undo/redo, equals. */
    val standardFooterRow = listOf(
        CalcKey.OPEN, CalcKey.CLOSE, CalcKey.ANS, CalcKey.UNDO, CalcKey.REDO, CalcKey.EQUALS,
    )

    /** Scientific compact panel: the keys that fit beside the standard grid. */
    val scientificRows: List<List<CalcKey>> = listOf(
        listOf(CalcKey.DEG_MODE, CalcKey.SIN, CalcKey.COS, CalcKey.TAN, CalcKey.POWER),
        listOf(CalcKey.SQRT, CalcKey.SQUARE, CalcKey.LN, CalcKey.LOG, CalcKey.MOD),
        listOf(CalcKey.OPEN, CalcKey.CLOSE, CalcKey.FACTORIAL, CalcKey.PI, CalcKey.E_CONST),
    )

    /** Scientific expanded panel, toggled by the "more" control. */
    val scientificExpandedRows: List<List<CalcKey>> = listOf(
        listOf(CalcKey.ASIN, CalcKey.ACOS, CalcKey.ATAN, CalcKey.SINH, CalcKey.COSH),
        listOf(CalcKey.TANH, CalcKey.CUBE, CalcKey.CBRT, CalcKey.NTH_ROOT, CalcKey.INV),
        listOf(CalcKey.EXP_FN, CalcKey.TEN_X, CalcKey.EE, CalcKey.TAU, CalcKey.PHI),
        listOf(CalcKey.NCR, CalcKey.NPR, CalcKey.GCD, CalcKey.LCM, CalcKey.ABS),
    )
}
