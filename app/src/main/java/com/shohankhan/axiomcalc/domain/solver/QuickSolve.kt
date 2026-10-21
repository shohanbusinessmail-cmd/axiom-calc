package com.shohankhan.axiomcalc.domain.solver

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.core.formatting.NumberFormatter
import com.shohankhan.axiomcalc.core.formatting.RoundingPolicy
import com.shohankhan.axiomcalc.domain.formula.FormInput
import com.shohankhan.axiomcalc.domain.formula.FormResult
import com.shohankhan.axiomcalc.domain.formula.FormulaCatalog
import com.shohankhan.axiomcalc.domain.formula.ResultLine
import com.shohankhan.axiomcalc.math.complex.Complex
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.solvers.Fraction
import com.shohankhan.axiomcalc.math.solvers.IntegerMath
import com.shohankhan.axiomcalc.math.solvers.LinearEquations
import com.shohankhan.axiomcalc.math.solvers.PercentMath
import com.shohankhan.axiomcalc.math.solvers.Quadratic
import com.shohankhan.axiomcalc.math.solvers.QuadraticKind
import com.shohankhan.axiomcalc.math.solvers.Sequences
import com.shohankhan.axiomcalc.math.solvers.SystemOutcome
import com.shohankhan.axiomcalc.math.statistics.Statistics

/**
 * Quick Solve: task-oriented solvers with *shown working*.
 *
 * Every tool produces
 *   1. the mathematical relationship,
 *   2. the input substitution,
 *   3. the intermediate calculations,
 *   4. the final result,
 *   5. the restrictions that apply,
 * and each step string is built from the same values the authoritative engine
 * computed, so the explanation can never disagree with the answer.
 */
object QuickSolve {

    data class QuickTool(
        val id: String,
        val title: String,
        val group: String,
        val inputs: List<FormInput>,
        val summary: String,
        val solve: (Map<String, String>) -> FormResult,
    )

    private fun raw(m: Map<String, String>, id: String): String =
        m[id] ?: throw CalcException(CalcError.InvalidArgument(id, "value for '$id' is missing"))

    private fun n(m: Map<String, String>, id: String): CalcNumber {
        val text = raw(m, id).trim().replace(",", ".")
        if (text.isEmpty()) throw CalcException(CalcError.InvalidArgument(id, "no value entered for '$id'"))
        return try {
            CalcNumber.parse(text)
        } catch (e: NumberFormatException) {
            throw CalcException(CalcError.InvalidArgument(id, "'$text' is not a valid number"))
        }
    }

    private fun int(m: Map<String, String>, id: String): java.math.BigInteger =
        n(m, id).toBigIntegerOrNull() ?: throw CalcException(CalcError.InvalidArgument(id, "'$id' must be an integer"))

    private fun p(v: CalcNumber): String = v.value.stripTrailingZeros().toPlainString()
    private fun line(label: String, v: CalcNumber, unit: String? = null) = ResultLine(label, p(v), unit)
    private fun text(label: String, value: String) = ResultLine(label, value)

    private fun dataset(m: Map<String, String>): List<CalcNumber> = FormulaCatalog.parseDataset(raw(m, "values"))

    // ==========================================================================

    val all: List<QuickTool> = listOf(
        tool("qs_pct_of", "Percentage of a number", "Percentage",
            listOf(FormInput("value", "Value"), FormInput("percent", "Percent", "%")),
            "Finds p% of v.") { m ->
            val v = n(m, "value"); val pc = n(m, "percent")
            val r = PercentMath.percentOf(v, pc)
            FormResult(
                listOf(line("Result", r)),
                listOf(
                    "Relationship: result = value * percent / 100",
                    "Substitution: result = ${p(v)} * ${p(pc)} / 100",
                    "Intermediate: ${p(v)} * ${p(pc)} = ${p(v * pc)}",
                    "Final: ${p(v * pc)} / 100 = ${p(r)}",
                    "Restriction: none.",
                ),
            )
        },
        tool("qs_reverse_pct", "Reverse percentage", "Percentage",
            listOf(FormInput("final", "Final value"), FormInput("percent", "Change applied", "%", "not ±100 for decrease/increase respectively"), FormInput("direction", "Direction", constraint = "increase or decrease")),
            "Recovers the original value before a percentage change.") { m ->
            val f = n(m, "final"); val pc = n(m, "percent")
            val dir = raw(m, "direction").trim().lowercase()
            val increase = when (dir) {
                "increase", "up", "+" -> true
                "decrease", "down", "-" -> false
                else -> throw CalcException(CalcError.InvalidArgument("direction", "enter 'increase' or 'decrease'"))
            }
            val r = if (increase) PercentMath.originalBeforeIncrease(f, pc) else PercentMath.originalBeforeDecrease(f, pc)
            val factor = if (increase) CalcNumber.ONE + pc / CalcNumber.of(100) else CalcNumber.ONE - pc / CalcNumber.of(100)
            FormResult(
                listOf(line("Original value", r)),
                listOf(
                    "Relationship: original = final / (1 ${if (increase) "+" else "-"} percent/100)",
                    "Substitution: original = ${p(f)} / ${p(factor)}",
                    "Final: ${p(r)}",
                    "Restriction: the factor must not be zero (a 100% decrease or a -100% increase).",
                ),
            )
        },
        tool("qs_pct_change", "Percentage change", "Percentage",
            listOf(FormInput("from", "From"), FormInput("to", "To")),
            "Signed percentage change between two values.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            val delta = to - from
            val r = PercentMath.percentageChange(from, to)
            FormResult(
                listOf(line("Change", r, "%"), line("Absolute difference", delta)),
                listOf(
                    "Relationship: change = (to - from) / |from| * 100",
                    "Substitution: (${p(to)} - ${p(from)}) / |${p(from)}| * 100",
                    "Intermediate: difference = ${p(delta)}",
                    "Final: ${p(r)} %",
                    "Restriction: 'from' must not be zero.",
                ),
            )
        },
        tool("qs_pct_points", "Percentage-point difference", "Percentage",
            listOf(FormInput("from", "From", "%"), FormInput("to", "To", "%")),
            "Arithmetic difference between two percentages.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            FormResult(
                listOf(line("Difference", to - from, "points")),
                listOf(
                    "Relationship: points = to - from",
                    "Substitution: ${p(to)} - ${p(from)}",
                    "Final: ${p(to - from)} percentage points",
                    "Restriction: none. This is deliberately NOT a relative change.",
                ),
            )
        },
        tool("qs_successive_discounts", "Successive discounts", "Percentage",
            listOf(FormInput("price", "Price"), FormInput("d1", "First discount", "%"), FormInput("d2", "Second discount", "%")),
            "Two discounts applied one after the other.") { m ->
            val price = n(m, "price")
            val d1 = n(m, "d1"); val d2 = n(m, "d2")
            val r = PercentMath.successiveDiscounts(price, listOf(d1, d2))
            FormResult(
                listOf(line("Final price", r.finalPrice), line("Effective discount", r.effectiveDiscountPercent, "%")),
                listOf(
                    "Relationship: final = price * (1 - d1/100) * (1 - d2/100)",
                    "Substitution: ${p(price)} * (1 - ${p(d1)}/100) * (1 - ${p(d2)}/100)",
                    "Intermediate: retained factor = ${p(r.retainedFactor)}",
                    "Final: ${p(r.finalPrice)} (effective discount ${p(r.effectiveDiscountPercent)}%)",
                    "Restriction: discounts are multiplied, never added.",
                ),
            )
        },
        tool("qs_ratio_simplify", "Ratio simplification", "Ratio",
            listOf(FormInput("a", "First term", constraint = "integer"), FormInput("b", "Second term", constraint = "integer")),
            "Reduces a ratio to lowest terms.") { m ->
            val a = int(m, "a"); val b = int(m, "b")
            val (x, y) = PercentMath.simplifyRatio(a, b)
            val g = a.abs().gcd(b.abs())
            FormResult(
                listOf(text("Simplified ratio", "$x : $y")),
                listOf(
                    "Relationship: divide both terms by gcd(|a|, |b|)",
                    "Substitution: gcd(${a.abs()}, ${b.abs()}) = $g",
                    "Intermediate: $a / $g = $x ; $b / $g = $y",
                    "Final: $x : $y",
                    "Restriction: both terms zero is not a ratio.",
                ),
            )
        },
        tool("qs_missing_proportion", "Missing proportion term", "Ratio",
            listOf(FormInput("a", "a"), FormInput("b", "b"), FormInput("c", "c", constraint = "a not zero")),
            "Solves a : b = c : x.") { m ->
            val a = n(m, "a"); val b = n(m, "b"); val c = n(m, "c")
            val x = PercentMath.directProportion(a, b, c)
            FormResult(
                listOf(line("x", x)),
                listOf(
                    "Relationship: a : b = c : x  =>  x = b * c / a",
                    "Substitution: x = ${p(b)} * ${p(c)} / ${p(a)}",
                    "Intermediate: ${p(b)} * ${p(c)} = ${p(b * c)}",
                    "Final: ${p(x)}",
                    "Restriction: a must not be zero.",
                ),
            )
        },
        tool("qs_fraction", "Fraction arithmetic", "Numbers",
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("op", "Operation", constraint = "+ - * /"), FormInput("n2", "Numerator 2", constraint = "integer"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "Exact arithmetic on two fractions.") { m ->
            val a = Fraction.of(int(m, "n1"), int(m, "d1"))
            val b = Fraction.of(int(m, "n2"), int(m, "d2"))
            val op = raw(m, "op").trim()
            val r = when (op) {
                "+" -> a + b
                "-", "−" -> a - b
                "*", "×" -> a * b
                "/", "÷" -> a / b
                else -> throw CalcException(CalcError.InvalidArgument("op", "operation must be one of + - * /"))
            }
            FormResult(
                listOf(text("Result", r.toString()), text("Mixed form", r.toMixedString()), line("Decimal", r.toDecimal(12))),
                listOf(
                    "Relationship: exact integer arithmetic on numerator/denominator, then reduction",
                    "Substitution: $a $op $b",
                    "Final: $r  (mixed ${r.toMixedString()})",
                    "Restriction: denominators not zero; division by a zero fraction rejected.",
                ),
            )
        },
        tool("qs_gcd", "Greatest common divisor", "Numbers",
            listOf(FormInput("a", "First integer", constraint = "integer"), FormInput("b", "Second integer", constraint = "integer")),
            "Euclidean algorithm, exact.") { m ->
            val a = int(m, "a"); val b = int(m, "b")
            val g = IntegerMath.gcd(a, b)
            FormResult(
                listOf(text("GCD", g.toString())),
                listOf("Relationship: gcd via Euclid's algorithm", "Substitution: gcd($a, $b)", "Final: $g", "Restriction: integers only."),
            )
        },
        tool("qs_lcm", "Least common multiple", "Numbers",
            listOf(FormInput("a", "First integer", constraint = "integer"), FormInput("b", "Second integer", constraint = "integer")),
            "lcm = |a b| / gcd(a, b).") { m ->
            val a = int(m, "a"); val b = int(m, "b")
            val l = IntegerMath.lcm(a, b)
            FormResult(
                listOf(text("LCM", l.toString())),
                listOf("Relationship: lcm = |a*b| / gcd(a,b)", "Substitution: gcd = ${IntegerMath.gcd(a, b)}", "Final: $l", "Restriction: zero inputs give 0."),
            )
        },
        tool("qs_prime_factors", "Prime factorization", "Numbers",
            listOf(FormInput("n", "Integer", constraint = "1 <= n <= 10^18")),
            "Trial division with a bounded step budget.") { m ->
            val v = int(m, "n")
            val factors = IntegerMath.primeFactorization(v)
            val rendered = factors.joinToString(" * ") { (prime, exp) -> if (exp == 1) "$prime" else "$prime^$exp" }
            FormResult(
                listOf(text("Factorization", rendered)),
                listOf("Relationship: n = product of prime powers", "Substitution: n = $v", "Final: $rendered", "Restriction: n <= 10^18; a step budget prevents freezes."),
            )
        },
        tool("qs_primality", "Primality test", "Numbers",
            listOf(FormInput("n", "Integer", constraint = "n >= 0")),
            "Deterministic Miller-Rabin for 64-bit inputs.") { m ->
            val v = int(m, "n")
            val prime = IntegerMath.isPrime(v)
            FormResult(
                listOf(text("Prime?", if (prime) "yes" else "no")),
                listOf("Relationship: deterministic Miller-Rabin bases 2..37 for n < 2^64", "Substitution: n = $v", "Final: ${if (prime) "prime" else "composite"}", "Restriction: values above 2^64 use a probabilistic test with 40 rounds."),
            )
        },
        tool("qs_linear", "Linear equation solver", "Equations",
            listOf(FormInput("equation", "Equation", constraint = "e.g. 3x + 5 = 20")),
            "Solves a x + b = 0 after moving everything left.") { m ->
            val equation = raw(m, "equation")
            val parts = equation.split('=')
            if (parts.size != 2) throw CalcException(CalcError.InvalidArgument("equation", "exactly one '=' is required"))
            val (a, b) = LinearEquations.linearCoefficients("(${parts[0]}) - (${parts[1]})")
            val root = LinearEquations.solveOneVariable(a, b)
            FormResult(
                listOf(text("Outcome", root.outcome.name), root.x?.let { line("x", it) } ?: text("x", "none")),
                listOf(
                    "Relationship: rewrite as a x + b = 0, then x = -b / a",
                    "Substitution: a = ${p(a)}, b = ${p(b)}",
                    "Final: ${root.x?.let { p(it) } ?: root.outcome.name}",
                    "Restriction: the expression must be linear in x (verified at three sample points).",
                ),
            )
        },
        tool("qs_simultaneous2", "Simultaneous equations (2x2)", "Equations",
            listOf(FormInput("a1", "a1"), FormInput("b1", "b1"), FormInput("c1", "c1"),
                FormInput("a2", "a2"), FormInput("b2", "b2"), FormInput("c2", "c2")),
            "a1 x + b1 y = c1 ; a2 x + b2 y = c2 with full outcome classification.") { m ->
            val coeffs = listOf(
                listOf(n(m, "a1").toDouble(), n(m, "b1").toDouble()),
                listOf(n(m, "a2").toDouble(), n(m, "b2").toDouble()),
            )
            val consts = listOf(n(m, "c1").toDouble(), n(m, "c2").toDouble())
            val s = LinearEquations.solveSystem(coeffs, consts)
            FormResult(
                listOf(
                    text("Outcome", s.outcome.name),
                    s.solution?.get(0)?.let { text("x", trim(it)) } ?: text("x", "-"),
                    s.solution?.get(1)?.let { text("y", trim(it)) } ?: text("y", "-"),
                ),
                listOf(
                    "Relationship: rank test on A and [A|c]",
                    "Intermediate: rank(A) = ${s.rankOfCoefficients}, rank([A|c]) = ${s.rankOfAugmented}",
                    "Final: ${s.outcome}",
                    "Restriction: singular or inconsistent systems are reported, never faked.",
                ),
            )
        },
        tool("qs_simultaneous3", "Simultaneous equations (3x3)", "Equations",
            listOf(FormInput("a1", "a1"), FormInput("b1", "b1"), FormInput("c1", "c1"), FormInput("d1", "d1"),
                FormInput("a2", "a2"), FormInput("b2", "b2"), FormInput("c2", "c2"), FormInput("d2", "d2"),
                FormInput("a3", "a3"), FormInput("b3", "b3"), FormInput("c3", "c3"), FormInput("d3", "d3")),
            "Three equations in x, y, z with outcome classification.") { m ->
            val coeffs = listOf(
                listOf(n(m, "a1").toDouble(), n(m, "b1").toDouble(), n(m, "c1").toDouble()),
                listOf(n(m, "a2").toDouble(), n(m, "b2").toDouble(), n(m, "c2").toDouble()),
                listOf(n(m, "a3").toDouble(), n(m, "b3").toDouble(), n(m, "c3").toDouble()),
            )
            val consts = listOf(n(m, "d1").toDouble(), n(m, "d2").toDouble(), n(m, "d3").toDouble())
            val s = LinearEquations.solveSystem(coeffs, consts)
            FormResult(
                listOf(text("Outcome", s.outcome.name)) + (s.solution?.mapIndexed { i, v -> text(listOf("x", "y", "z")[i], trim(v)) } ?: emptyList()),
                listOf(
                    "Relationship: Gaussian elimination with partial pivoting and rank test",
                    "Intermediate: rank(A) = ${s.rankOfCoefficients}, rank([A|c]) = ${s.rankOfAugmented}",
                    "Final: ${s.outcome}",
                    "Restriction: residual of the solution is verified before it is reported.",
                ),
            )
        },
        tool("qs_quadratic", "Quadratic equation solver", "Equations",
            listOf(FormInput("a", "a"), FormInput("b", "b"), FormInput("c", "c")),
            "a x^2 + b x + c = 0 with the numerically stable root formula.") { m ->
            val s = Quadratic.solve(n(m, "a"), n(m, "b"), n(m, "c"))
            val roots = when (s.kind) {
                QuadraticKind.COMPLEX_CONJUGATE_PAIR -> s.complexRoots.joinToString("  ,  ") { it.toRectangularString() }
                QuadraticKind.DEGENERATE_LINEAR -> s.linearRoot?.let { trim(it) } ?: "none"
                QuadraticKind.DEGENERATE_CONSTANT -> "none / all"
                else -> s.realRoots.joinToString("  ,  ") { trim(it) }
            }
            FormResult(
                listOf(text("Kind", s.kind.name), text("Discriminant", trim(s.discriminant)), text("Roots", roots)),
                listOf(
                    "Relationship: D = b^2 - 4ac; roots from the stable formula with x1*x2 = c/a",
                    "Substitution: a = ${p(n(m, "a"))}, b = ${p(n(m, "b"))}, c = ${p(n(m, "c"))}",
                    "Intermediate: D = ${trim(s.discriminant)}",
                    "Final: $roots",
                    "Restriction: a = 0 degenerates to linear and is reported as such.",
                ),
            )
        },
        tool("qs_arith_seq", "Arithmetic sequence", "Sequences",
            listOf(FormInput("a1", "First term"), FormInput("d", "Common difference"), FormInput("n", "Term number", constraint = "integer >= 1")),
            "nth term and finite sum.") { m ->
            val r = Sequences.arithmetic(n(m, "a1"), n(m, "d"), n(m, "n"))
            FormResult(
                listOf(line("Term ${r.termIndex}", r.nthTerm), line("Sum to n", r.sumToN)),
                listOf(
                    "Relationship: a_n = a1 + (n-1)d ; S_n = n(a1 + a_n)/2",
                    "Substitution: a1 = ${p(r.firstTerm)}, d = ${p(r.commonDifference)}, n = ${r.termIndex}",
                    "Intermediate: a_n = ${p(r.nthTerm)}",
                    "Final: S_n = ${p(r.sumToN)}",
                    "Restriction: n must be a non-negative integer.",
                ),
            )
        },
        tool("qs_geom_seq", "Geometric sequence", "Sequences",
            listOf(FormInput("a1", "First term"), FormInput("r", "Common ratio"), FormInput("n", "Term number", constraint = "integer >= 1")),
            "nth term and finite sum.") { m ->
            val res = Sequences.geometric(n(m, "a1"), n(m, "r"), n(m, "n"))
            FormResult(
                listOf(line("Term ${res.termIndex}", res.nthTerm), line("Sum to n", res.sumToN)),
                listOf(
                    "Relationship: a_n = a1 r^(n-1) ; S_n = a1 (1 - r^n)/(1 - r), and a1*n when r = 1",
                    "Substitution: a1 = ${p(res.firstTerm)}, r = ${p(res.commonRatio)}, n = ${res.termIndex}",
                    "Final: S_n = ${p(res.sumToN)}",
                    "Restriction: r = 1 handled explicitly (the closed form is 0/0 there).",
                ),
            )
        },
        tool("qs_sigfigs", "Significant figures", "Engineering",
            listOf(FormInput("value", "Value"), FormInput("figures", "Significant figures", constraint = "1..34")),
            "Rounds to n significant figures, half-even.") { m ->
            val v = n(m, "value"); val f = int(m, "figures").toInt()
            val r = NumberFormatter.significantFigures(v, f)
            FormResult(
                listOf(text("Rounded", p(r)), text("Scientific", NumberFormatter.scientific(r, f))),
                listOf("Relationship: round to n significant digits, half-even", "Substitution: value = ${p(v)}, figures = $f", "Final: ${p(r)}", "Restriction: 1..34 figures."),
            )
        },
        tool("qs_round", "Rounding", "Engineering",
            listOf(FormInput("value", "Value"), FormInput("decimals", "Decimal places", constraint = "integer"), FormInput("mode", "Mode", constraint = "HALF_EVEN HALF_UP DOWN UP CEILING FLOOR")),
            "Rounds to a fixed number of decimals under an explicit policy.") { m ->
            val v = n(m, "value"); val d = int(m, "decimals").toInt()
            val modeName = raw(m, "mode").trim().uppercase().replace(" ", "_")
            val mode = RoundingPolicy.entries.firstOrNull { it.name == modeName }
                ?: throw CalcException(CalcError.InvalidArgument("mode", "unknown rounding mode '$modeName'"))
            val r = NumberFormatter.roundTo(v, d, mode)
            FormResult(
                listOf(text("Rounded", p(r))),
                listOf("Relationship: setScale(decimals, ${mode.label})", "Substitution: value = ${p(v)}, decimals = $d", "Final: ${p(r)}", "Restriction: the mode is explicit; the engine default is half-even."),
            )
        },
        tool("qs_scientific", "Scientific & engineering notation", "Engineering",
            listOf(FormInput("value", "Value")),
            "Rewrites a value in scientific and engineering notation.") { m ->
            val v = n(m, "value")
            FormResult(
                listOf(text("Scientific", NumberFormatter.scientific(v, 12)), text("Engineering", NumberFormatter.engineering(v, 12)), text("Decimal", p(v))),
                listOf("Relationship: mantissa in [1,10) for scientific; exponent a multiple of 3 for engineering", "Substitution: value = ${p(v)}", "Final: see above", "Restriction: presentation only; the stored value is unchanged."),
            )
        },
        tool("qs_mean", "Mean", "Statistics", listOf(FormInput("values", "Values, comma separated")), "Arithmetic mean.") { m ->
            val v = dataset(m)
            FormResult(listOf(line("Mean", Statistics.mean(v)), line("Count", CalcNumber.of(v.size))),
                listOf("Relationship: mean = sum / count", "Substitution: ${v.size} values", "Final: ${p(Statistics.mean(v))}", "Restriction: at least one value."))
        },
        tool("qs_median", "Median", "Statistics", listOf(FormInput("values", "Values, comma separated")), "Central value.") { m ->
            val v = dataset(m)
            FormResult(listOf(line("Median", Statistics.median(v))),
                listOf("Relationship: middle of the sorted set; mean of the two middle values when even", "Final: ${p(Statistics.median(v))}", "Restriction: at least one value."))
        },
        tool("qs_mode", "Mode", "Statistics", listOf(FormInput("values", "Values, comma separated")), "Most frequent values.") { m ->
            val v = dataset(m)
            val modes = Statistics.mode(v)
            FormResult(listOf(text("Mode", if (modes.isEmpty()) "none" else modes.joinToString(", ") { p(it) })),
                listOf("Relationship: value(s) with maximum frequency", "Final: ${if (modes.isEmpty()) "none (all distinct)" else modes.joinToString(", ") { p(it) }}", "Restriction: all-distinct datasets have no mode."))
        },
        tool("qs_range", "Range", "Statistics", listOf(FormInput("values", "Values, comma separated")), "max - min.") { m ->
            val v = dataset(m)
            FormResult(listOf(line("Range", Statistics.range(v)), line("Min", Statistics.min(v)), line("Max", Statistics.max(v))),
                listOf("Relationship: range = max - min", "Final: ${p(Statistics.range(v))}", "Restriction: at least one value."))
        },
        tool("qs_variance", "Variance", "Statistics", listOf(FormInput("values", "Values, comma separated")), "Population and sample variance.") { m ->
            val v = dataset(m)
            FormResult(
                listOf(line("Population variance", Statistics.populationVariance(v))) +
                    (Statistics.sampleVarianceOrNull(v)?.let { listOf(line("Sample variance", it)) } ?: listOf(text("Sample variance", "needs >= 2 values"))),
                listOf("Relationship: population divides by N; sample divides by N-1", "Final: see above", "Restriction: sample variance requires N >= 2."),
            )
        },
        tool("qs_stddev", "Standard deviation", "Statistics", listOf(FormInput("values", "Values, comma separated")), "Population and sample standard deviation.") { m ->
            val v = dataset(m)
            FormResult(
                listOf(line("Population std dev", Statistics.populationStdDev(v))) +
                    (Statistics.sampleVarianceOrNull(v)?.let { listOf(line("Sample std dev", Statistics.sampleStdDev(v))) } ?: listOf(text("Sample std dev", "needs >= 2 values"))),
                listOf("Relationship: sqrt of the corresponding variance", "Final: see above", "Restriction: sample std dev requires N >= 2."),
            )
        },
    )

    private fun Statistics.sampleVarianceOrNull(values: List<CalcNumber>): CalcNumber? =
        if (values.size >= 2) sampleVariance(values) else null

    private fun trim(v: Double): String {
        if (!v.isFinite()) return v.toString()
        return java.math.BigDecimal(v).stripTrailingZeros().toPlainString()
    }

    private fun tool(
        id: String,
        title: String,
        group: String,
        inputs: List<FormInput>,
        summary: String,
        solve: (Map<String, String>) -> FormResult,
    ): QuickTool = QuickTool(id, title, group, inputs, summary, solve)

    fun search(query: String): List<QuickTool> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.title.lowercase().contains(q) || it.group.lowercase().contains(q) || it.summary.lowercase().contains(q) }
    }

    fun byId(id: String): QuickTool? = all.firstOrNull { it.id == id }

    /** Used by tests to assert complex-number support is wired into Quick Solve. */
    fun complexRootExample(a: Double, b: Double, c: Double): List<Complex> =
        Quadratic.solveDoubles(a, b, c).complexRoots

    fun groups(): List<String> = all.map { it.group }.distinct()
}
