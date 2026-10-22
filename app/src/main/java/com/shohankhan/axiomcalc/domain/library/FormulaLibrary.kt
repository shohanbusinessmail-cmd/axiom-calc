package com.shohankhan.axiomcalc.domain.library

/**
 * The offline Formula Library: a curated, reviewed reference collection.
 *
 * Each entry stores a stable id, the mathematical expression, its variable
 * definitions, the conditions under which it holds, its units where applicable,
 * and a link to the calculator form or tab that implements it. An entry is only
 * published here if the corresponding computation genuinely exists in the app,
 * so the library never advertises a feature that is not implemented.
 */
enum class LibraryCategory(val title: String) {
    ARITHMETIC("Arithmetic"),
    PERCENTAGES("Percentages"),
    RATIOS("Ratios & proportion"),
    ALGEBRA("Algebra"),
    GEOMETRY("Geometry"),
    TRIGONOMETRY("Trigonometry"),
    SEQUENCES("Sequences & series"),
    STATISTICS("Statistics"),
    INTEREST("Interest & finance"),
    COMBINATORICS("Combinatorics"),
    NUMBER_THEORY("Number theory"),
    MATRICES("Matrices"),
    COMPLEX("Complex numbers"),
}

data class LibraryFormula(
    val id: String,
    val title: String,
    val category: LibraryCategory,
    val expression: String,
    val variables: List<Pair<String, String>>,
    val conditions: String,
    val units: String?,
    /**
     * id of the implementation that computes this: either a FormulaCatalog form
     * or a QuickSolve tool. Verified by CatalogCompletenessTest to resolve.
     */
    val formId: String?,
    /** keyword used by search beyond title/expression. */
    val keywords: List<String> = emptyList(),
)

object FormulaLibrary {

    val all: List<LibraryFormula> = listOf(
        // ---- arithmetic
        li("lib_abs", "Absolute value", LibraryCategory.ARITHMETIC, "abs(x) = |x|",
            listOf("x" to "any real number"), "none", null, null, listOf("magnitude", "modulus")),
        li("lib_mod", "Remainder (modulus)", LibraryCategory.ARITHMETIC, "a mod b = a - b*trunc(a/b)",
            listOf("a" to "dividend", "b" to "divisor"), "b != 0", null, null, listOf("remainder")),
        li("lib_floor_ceil", "Floor and ceiling", LibraryCategory.ARITHMETIC, "floor(x) <= x <= ceil(x)",
            listOf("x" to "any real number"), "floor rounds down, ceiling rounds up to integers", null, null, listOf("rounding", "integer part")),
        li("lib_power_rules", "Exponent rules", LibraryCategory.ARITHMETIC, "a^m * a^n = a^(m+n);  (a^m)^n = a^(m n)",
            listOf("a" to "base", "m" to "exponent", "n" to "exponent"), "a > 0 for fractional exponents", null, null, listOf("indices", "laws")),
        li("lib_sqrt", "Square and cube roots", LibraryCategory.ARITHMETIC, "sqrt(x) = x^(1/2);  cbrt(x) = x^(1/3)",
            listOf("x" to "radicand"), "sqrt requires x >= 0 in real mode", null, null, listOf("root")),

        // ---- percentages
        li("lib_pct_of", "Percentage of a value", LibraryCategory.PERCENTAGES, "p% of v = v * p / 100",
            listOf("v" to "value", "p" to "percentage"), "none", null, "pct_of"),
        li("lib_pct_what", "Value as a percentage", LibraryCategory.PERCENTAGES, "p = part / whole * 100",
            listOf("part" to "portion", "whole" to "reference"), "whole != 0", null, "pct_what"),
        li("lib_pct_change", "Percentage change", LibraryCategory.PERCENTAGES, "change = (new - old) / |old| * 100",
            listOf("old" to "original", "new" to "updated"), "old != 0", null, "pct_change"),
        li("lib_pct_points", "Percentage points", LibraryCategory.PERCENTAGES, "points = p2 - p1",
            listOf("p1" to "first percentage", "p2" to "second percentage"), "arithmetic difference, not relative", "percentage points", "pct_points"),
        li("lib_pct_reverse", "Reverse percentage", LibraryCategory.PERCENTAGES, "original = final / (1 +/- p/100)",
            listOf("final" to "value after change", "p" to "applied percentage"), "factor must not be zero", null, "pct_reverse_increase"),
        li("lib_discount_seq", "Successive discounts", LibraryCategory.PERCENTAGES, "final = v * prod(1 - d_i/100)",
            listOf("v" to "price", "d_i" to "each discount"), "discounts multiply, never add", null, "disc_sequential"),
        li("lib_markup_margin", "Markup and margin", LibraryCategory.PERCENTAGES,
            "markup = (s - c)/c * 100;  margin = (s - c)/s * 100",
            listOf("c" to "cost price", "s" to "selling price"),
            "markup divides by cost, margin divides by selling; they are not interchangeable", null, "margin_percent",
            listOf("profit", "markup", "margin")),

        // ---- ratios
        li("lib_ratio_simplify", "Ratio reduction", LibraryCategory.RATIOS, "a : b = (a/g) : (b/g), g = gcd(a,b)",
            listOf("a" to "first term", "b" to "second term"), "not both zero", null, "ratio_simplify"),
        li("lib_proportion", "Direct proportion", LibraryCategory.RATIOS, "a : b = c : x  =>  x = b c / a",
            listOf("a" to "first term", "b" to "second term", "c" to "third term"), "a != 0", null, "ratio_direct"),
        li("lib_inverse_prop", "Inverse proportion", LibraryCategory.RATIOS, "a * b = c * x  =>  x = a b / c",
            listOf("a" to "first", "b" to "second", "c" to "third"), "c != 0", null, "ratio_inverse"),

        // ---- algebra
        li("lib_linear", "Linear equation", LibraryCategory.ALGEBRA, "a x + b = 0  =>  x = -b / a",
            listOf("a" to "coefficient", "b" to "constant"), "a != 0 for a unique root", null, "alg_solve_linear_x"),
        li("lib_quadratic", "Quadratic formula", LibraryCategory.ALGEBRA, "x = (-b +/- sqrt(b^2 - 4ac)) / (2a)",
            listOf("a" to "quadratic coefficient", "b" to "linear coefficient", "c" to "constant"),
            "D < 0 gives a complex conjugate pair", null, null, listOf("discriminant", "roots")),
        li("lib_poly", "Polynomial evaluation", LibraryCategory.ALGEBRA, "p(x) = c0 + c1 x + c2 x^2 + c3 x^3",
            listOf("c_i" to "coefficients", "x" to "variable"), "none", null, "alg_poly_eval"),
        li("lib_diff_squares", "Difference of squares", LibraryCategory.ALGEBRA, "a^2 - b^2 = (a-b)(a+b)",
            listOf("a" to "first", "b" to "second"), "identity, always valid", null, null, listOf("factorisation")),

        // ---- geometry
        li("lib_circle", "Circle", LibraryCategory.GEOMETRY, "A = pi r^2;  C = 2 pi r",
            listOf("r" to "radius"), "r > 0", "same units as r (area squared)", "geo_circle"),
        li("lib_triangle", "Triangle area", LibraryCategory.GEOMETRY, "A = b h / 2;  Heron: sqrt(s(s-a)(s-b)(s-c))",
            listOf("b" to "base", "h" to "height", "s" to "semi-perimeter"), "triangle inequality for Heron", null, "geo_triangle_heron"),
        li("lib_pythagoras", "Pythagoras", LibraryCategory.GEOMETRY, "c = sqrt(a^2 + b^2)",
            listOf("a" to "leg", "b" to "leg", "c" to "hypotenuse"), "right-angled triangle", null, null),
        li("lib_sphere", "Sphere", LibraryCategory.GEOMETRY, "V = (4/3) pi r^3;  S = 4 pi r^2",
            listOf("r" to "radius"), "r > 0", null, "geo_sphere"),
        li("lib_cylinder", "Cylinder", LibraryCategory.GEOMETRY, "V = pi r^2 h;  S = 2 pi r (r + h)",
            listOf("r" to "radius", "h" to "height"), "r, h > 0", null, "geo_cylinder"),
        li("lib_cone", "Cone", LibraryCategory.GEOMETRY, "V = (1/3) pi r^2 h",
            listOf("r" to "radius", "h" to "height"), "r, h > 0", null, "geo_cone"),
        li("lib_trapezoid", "Trapezoid", LibraryCategory.GEOMETRY, "A = (a + b) h / 2",
            listOf("a" to "parallel side", "b" to "parallel side", "h" to "height"), "all positive", null, "geo_trapezoid"),
        li("lib_ellipse", "Ellipse", LibraryCategory.GEOMETRY, "A = pi a b",
            listOf("a" to "semi-major axis", "b" to "semi-minor axis"), "a, b > 0", null, "geo_ellipse"),

        // ---- trigonometry
        li("lib_pythag_trig", "Pythagorean identity", LibraryCategory.TRIGONOMETRY, "sin^2(t) + cos^2(t) = 1",
            listOf("t" to "angle"), "holds in any angle mode", null, null, listOf("identity")),
        li("lib_tan_def", "Tangent definition", LibraryCategory.TRIGONOMETRY, "tan(t) = sin(t) / cos(t)",
            listOf("t" to "angle"), "undefined where cos(t) = 0", null, null),
        li("lib_sin_rule", "Law of sines", LibraryCategory.TRIGONOMETRY, "a / sin(A) = b / sin(B) = c / sin(C)",
            listOf("a,b,c" to "sides", "A,B,C" to "opposite angles"), "any triangle", null, null),
        li("lib_cos_rule", "Law of cosines", LibraryCategory.TRIGONOMETRY, "c^2 = a^2 + b^2 - 2 a b cos(C)",
            listOf("a,b,c" to "sides", "C" to "angle opposite c"), "any triangle", null, null),
        li("lib_sector", "Circular sector", LibraryCategory.TRIGONOMETRY, "A = (t/360) pi r^2;  arc = (t/360) 2 pi r",
            listOf("t" to "central angle in degrees", "r" to "radius"), "0 < t <= 360", null, "geo_sector"),
        li("lib_hyperbolic", "Hyperbolic definitions", LibraryCategory.TRIGONOMETRY, "sinh(x) = (e^x - e^-x)/2;  cosh(x) = (e^x + e^-x)/2",
            listOf("x" to "real argument"), "none", null, null),

        // ---- sequences
        li("lib_arith_seq", "Arithmetic sequence", LibraryCategory.SEQUENCES, "a_n = a1 + (n-1)d;  S_n = n(a1 + a_n)/2",
            listOf("a1" to "first term", "d" to "common difference", "n" to "term count"), "n a positive integer", null, null),
        li("lib_geom_seq", "Geometric sequence", LibraryCategory.SEQUENCES, "a_n = a1 r^(n-1);  S_n = a1(1 - r^n)/(1 - r)",
            listOf("a1" to "first term", "r" to "common ratio", "n" to "term count"), "r = 1 handled as S_n = a1 n", null, null),
        li("lib_geom_inf", "Infinite geometric series", LibraryCategory.SEQUENCES, "S = a1 / (1 - r)",
            listOf("a1" to "first term", "r" to "common ratio"), "converges only for |r| < 1", null, null),

        // ---- statistics
        li("lib_mean", "Arithmetic mean", LibraryCategory.STATISTICS, "mean = sum(x_i) / n",
            listOf("x_i" to "observations", "n" to "count"), "n >= 1", null, "avg_mean"),
        li("lib_weighted_mean", "Weighted mean", LibraryCategory.STATISTICS, "mean = sum(w_i x_i) / sum(w_i)",
            listOf("x_i" to "observations", "w_i" to "weights"), "sum(w_i) != 0", null, "avg_weighted"),
        li("lib_median", "Median", LibraryCategory.STATISTICS, "middle of sorted data",
            listOf("x_i" to "observations"), "even n averages the two middle values", null, "avg_median"),
        li("lib_variance", "Variance", LibraryCategory.STATISTICS, "population: sum(d^2)/N;  sample: sum(d^2)/(N-1)",
            listOf("d" to "deviation from the mean", "N" to "count"), "sample variance needs N >= 2", null, null),
        li("lib_stddev", "Standard deviation", LibraryCategory.STATISTICS, "sigma = sqrt(variance)",
            listOf("variance" to "population or sample variance"), "matches the variance variant used", null, null),

        // ---- interest
        li("lib_simple_interest", "Simple interest", LibraryCategory.INTEREST, "I = P r t / 100",
            listOf("P" to "principal", "r" to "annual rate percent", "t" to "years"), "rate per year", null, "int_simple"),
        li("lib_compound", "Compound interest", LibraryCategory.INTEREST, "A = P (1 + r/(100 n))^(n t)",
            listOf("P" to "principal", "r" to "annual rate percent", "n" to "compounds per year", "t" to "years"), "factor must stay positive", null, "int_compound"),
        li("lib_amortization", "Loan installment", LibraryCategory.INTEREST, "A = P i / (1 - (1 + i)^-N)",
            listOf("P" to "principal", "i" to "periodic rate", "N" to "payments"), "i = 0 degenerates to P/N", null, "inst_payment"),

        // ---- combinatorics
        li("lib_factorial", "Factorial", LibraryCategory.COMBINATORICS, "n! = 1*2*...*n;  0! = 1",
            listOf("n" to "non-negative integer"), "n <= 10000 in this app", null, null),
        li("lib_combinations", "Combinations", LibraryCategory.COMBINATORICS, "C(n, r) = n! / (r! (n-r)!)",
            listOf("n" to "pool size", "r" to "selections"), "0 <= r <= n", null, null),
        li("lib_permutations", "Permutations", LibraryCategory.COMBINATORICS, "P(n, r) = n! / (n-r)!",
            listOf("n" to "pool size", "r" to "selections"), "0 <= r <= n", null, null),

        // ---- number theory
        li("lib_gcd", "Greatest common divisor", LibraryCategory.NUMBER_THEORY, "gcd(a, b) via Euclid",
            listOf("a" to "integer", "b" to "integer"), "gcd(0,0) = 0", null, "qs_gcd"),
        li("lib_lcm", "Least common multiple", LibraryCategory.NUMBER_THEORY, "lcm(a, b) = |a b| / gcd(a, b)",
            listOf("a" to "integer", "b" to "integer"), "zero input gives 0", null, "qs_lcm"),
        li("lib_primes", "Prime factorization", LibraryCategory.NUMBER_THEORY, "n = prod p_i^e_i",
            listOf("n" to "positive integer"), "n <= 10^18 in this app", null, "qs_prime_factors"),

        // ---- matrices
        li("lib_mat_mul", "Matrix product", LibraryCategory.MATRICES, "(AB)_ij = sum_k A_ik B_kj",
            listOf("A" to "m x n matrix", "B" to "n x p matrix"), "inner dimensions must match", null, null),
        li("lib_det2", "2x2 determinant", LibraryCategory.MATRICES, "det = a d - b c",
            listOf("a,b,c,d" to "entries"), "zero determinant means singular", null, null),
        li("lib_inverse", "Matrix inverse", LibraryCategory.MATRICES, "A A^-1 = I",
            listOf("A" to "square matrix"), "exists only when det(A) != 0", null, null),
        li("lib_rank", "Matrix rank", LibraryCategory.MATRICES, "rank = number of pivots in row echelon form",
            listOf("A" to "any matrix"), "computed with an explicit tolerance", null, null),

        // ---- complex
        li("lib_complex_mul", "Complex product", LibraryCategory.COMPLEX, "(a+bi)(c+di) = (ac-bd) + (ad+bc)i",
            listOf("a,b" to "first", "c,d" to "second"), "none", null, null),
        li("lib_complex_div", "Complex quotient", LibraryCategory.COMPLEX, "(a+bi)/(c+di) = ((ac+bd) + (bc-ad)i)/(c^2+d^2)",
            listOf("a,b" to "numerator", "c,d" to "denominator"), "c^2 + d^2 != 0", null, null),
        li("lib_complex_polar", "Polar form", LibraryCategory.COMPLEX, "z = r (cos t + i sin t),  r = |z|, t = arg(z)",
            listOf("r" to "magnitude", "t" to "argument in radians"), "argument is the principal value in (-pi, pi]", null, null),
        li("lib_euler", "Euler's formula", LibraryCategory.COMPLEX, "e^(i t) = cos(t) + i sin(t)",
            listOf("t" to "angle in radians"), "reference identity", null, null),
    )

    private fun li(
        id: String,
        title: String,
        category: LibraryCategory,
        expression: String,
        variables: List<Pair<String, String>>,
        conditions: String,
        units: String?,
        formId: String?,
        keywords: List<String> = emptyList(),
    ): LibraryFormula = LibraryFormula(id, title, category, expression, variables, conditions, units, formId, keywords)

    fun byCategory(category: LibraryCategory): List<LibraryFormula> = all.filter { it.category == category }

    fun byId(id: String): LibraryFormula? = all.firstOrNull { it.id == id }

    fun search(query: String, category: LibraryCategory? = null): List<LibraryFormula> {
        val base = category?.let { byCategory(it) } ?: all
        val q = query.trim().lowercase()
        if (q.isEmpty()) return base
        return base.filter { f ->
            f.title.lowercase().contains(q) ||
                f.expression.lowercase().contains(q) ||
                f.category.title.lowercase().contains(q) ||
                f.conditions.lowercase().contains(q) ||
                f.keywords.any { it.lowercase().contains(q) } ||
                f.variables.any { (sym, desc) -> sym.lowercase().contains(q) || desc.lowercase().contains(q) }
        }
    }
}
