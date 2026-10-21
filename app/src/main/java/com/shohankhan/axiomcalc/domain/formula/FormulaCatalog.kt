package com.shohankhan.axiomcalc.domain.formula

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.solvers.CivilDate
import com.shohankhan.axiomcalc.math.solvers.Duration
import com.shohankhan.axiomcalc.math.solvers.Finance
import com.shohankhan.axiomcalc.math.solvers.Fraction
import com.shohankhan.axiomcalc.math.solvers.LinearEquations
import com.shohankhan.axiomcalc.math.solvers.PercentMath
import com.shohankhan.axiomcalc.math.solvers.Sequences
import java.math.BigInteger

/** The 13 committed formula categories. */
enum class FormulaCategory(val id: String, val title: String) {
    PERCENTAGE("percentage", "Percentage"),
    DISCOUNT("discount", "Discount"),
    VAT("vat", "VAT & percentage tax"),
    PROFIT("profit", "Profit, loss, markup & margin"),
    RATIO("ratio", "Ratio & proportion"),
    AVERAGE("average", "Average & statistics"),
    INTEREST("interest", "Interest"),
    INSTALLMENTS("installments", "Installments"),
    GEOMETRY("geometry", "Geometry"),
    ALGEBRA("algebra", "Algebra"),
    FRACTIONS("fractions", "Fractions"),
    TIME("time", "Time arithmetic"),
    DATE("date", "Date arithmetic"),
}

data class FormInput(
    val id: String,
    val label: String,
    val unit: String? = null,
    val constraint: String? = null,
)

data class ResultLine(val label: String, val value: String, val note: String? = null)

data class FormResult(
    val lines: List<ResultLine>,
    val substitution: String,
    val steps: List<String> = emptyList(),
) {
    /** Formula Calculator style: lines plus the formula substitution string. */
    constructor(lines: List<ResultLine>, substitution: String) : this(lines, substitution, emptyList())

    /** Quick Solve style: lines plus shown working; the substitution line is the
     *  "Substitution:" step when one was supplied. */
    constructor(lines: List<ResultLine>, steps: List<String>) :
        this(lines, steps.firstOrNull { it.startsWith("Substitution:") } ?: "", steps)
}

data class FormulaForm(
    val id: String,
    val title: String,
    val category: FormulaCategory,
    val inputs: List<FormInput>,
    val formulaText: String,
    val explanation: String,
    val compute: (Map<String, String>) -> FormResult,
)

/**
 * The Formula Calculator catalog: every advertised category contains real,
 * validated, tested calculation forms. No category contains an empty card.
 *
 * Each form stores its formula text and builds its substitution string from the
 * *same* values it computes with, so the displayed working can never disagree
 * with the result.
 */
object FormulaCatalog {

    // ---- small helpers --------------------------------------------------------

    private fun raw(map: Map<String, String>, id: String): String =
        map[id] ?: throw CalcException(CalcError.InvalidArgument(id, "value for '$id' is missing"))

    /** Parses a numeric input, rejecting blanks and malformed text explicitly. */
    private fun n(map: Map<String, String>, id: String): CalcNumber {
        val text = raw(map, id).trim().replace(",", ".")
        if (text.isEmpty()) throw CalcException(CalcError.InvalidArgument(id, "no value entered for '$id'"))
        return try {
            CalcNumber.parse(text)
        } catch (e: NumberFormatException) {
            throw CalcException(CalcError.InvalidArgument(id, "'$text' is not a valid number for '$id'"))
        }
    }

    private fun pos(v: CalcNumber, id: String): CalcNumber {
        if (v.signum <= 0) throw CalcException(CalcError.InvalidArgument(id, "'$id' must be greater than zero, got $v"))
        return v
    }

    private fun nonNeg(v: CalcNumber, id: String): CalcNumber {
        if (v.signum < 0) throw CalcException(CalcError.InvalidArgument(id, "'$id' must not be negative, got $v"))
        return v
    }

    private fun num(label: String, v: CalcNumber, note: String? = null) =
        ResultLine(label, v.value.stripTrailingZeros().toPlainString(), note)

    private fun sub(formula: String, values: Map<String, String>) =
        formula + "  with  " + values.entries.joinToString(", ") { (k, v) -> "$k = ${v.trim()}" }

    private val P = FormulaCategory.PERCENTAGE
    private val D = FormulaCategory.DISCOUNT
    private val V = FormulaCategory.VAT
    private val PR = FormulaCategory.PROFIT
    private val R = FormulaCategory.RATIO
    private val A = FormulaCategory.AVERAGE
    private val I = FormulaCategory.INTEREST
    private val IN = FormulaCategory.INSTALLMENTS
    private val G = FormulaCategory.GEOMETRY
    private val AL = FormulaCategory.ALGEBRA
    private val F = FormulaCategory.FRACTIONS
    private val T = FormulaCategory.TIME
    private val DA = FormulaCategory.DATE

    // ==========================================================================
    val all: List<FormulaForm> = listOf(
        // ---------------- A: percentage ----------------
        form("pct_of", "Percentage of a value", P,
            listOf(FormInput("value", "Value"), FormInput("percent", "Percentage", "%")),
            "result = value * percent / 100",
            "Finds percent% of value, e.g. 15% of 200 = 30.") { m ->
            val v = n(m, "value"); val p = n(m, "percent")
            FormResult(listOf(num("Result", PercentMath.percentOf(v, p))), sub("value * percent / 100", m))
        },
        form("pct_what", "What percent one value is of another", P,
            listOf(FormInput("part", "Part"), FormInput("whole", "Whole", constraint = "not zero")),
            "percent = part / whole * 100",
            "Expresses part as a percentage of whole. The whole may not be zero.") { m ->
            val part = n(m, "part"); val whole = n(m, "whole")
            FormResult(listOf(num("Percentage", PercentMath.whatPercent(part, whole), "%")), sub("part / whole * 100", m))
        },
        form("pct_increase", "Percentage increase", P,
            listOf(FormInput("from", "Original value", constraint = "not zero"), FormInput("to", "New value")),
            "increase = (to - from) / from * 100",
            "Relative rise from the original value.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            FormResult(listOf(num("Increase", PercentMath.percentageIncrease(from, to), "%")), sub("(to - from) / from * 100", m))
        },
        form("pct_decrease", "Percentage decrease", P,
            listOf(FormInput("from", "Original value", constraint = "not zero"), FormInput("to", "New value")),
            "decrease = (from - to) / from * 100",
            "Relative fall from the original value, reported as a positive number.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            FormResult(listOf(num("Decrease", PercentMath.percentageDecrease(from, to), "%")), sub("(from - to) / from * 100", m))
        },
        form("pct_change", "Percentage change (signed)", P,
            listOf(FormInput("from", "Original value", constraint = "not zero"), FormInput("to", "New value")),
            "change = (to - from) / |from| * 100",
            "Signed change: negative when the value fell.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            FormResult(listOf(num("Change", PercentMath.percentageChange(from, to), "%")), sub("(to - from) / |from| * 100", m))
        },
        form("pct_points", "Percentage-point difference", P,
            listOf(FormInput("from", "From", "%"), FormInput("to", "To", "%")),
            "points = to - from",
            "Arithmetic difference between two percentages, e.g. 18% to 20% is 2 points, not 11.1%.") { m ->
            val from = n(m, "from"); val to = n(m, "to")
            FormResult(listOf(num("Difference", PercentMath.percentagePoints(from, to), "percentage points")), sub("to - from", m))
        },
        form("pct_reverse_increase", "Reverse percentage (before an increase)", P,
            listOf(FormInput("final", "Final value"), FormInput("percent", "Increase applied", "%", "not -100")),
            "original = final / (1 + percent/100)",
            "Recovers the original value before a percent increase was applied.") { m ->
            val f = n(m, "final"); val p = n(m, "percent")
            FormResult(listOf(num("Original", PercentMath.originalBeforeIncrease(f, p))), sub("final / (1 + percent/100)", m))
        },
        form("pct_reverse_decrease", "Original value recovery (before a decrease)", P,
            listOf(FormInput("final", "Final value"), FormInput("percent", "Decrease applied", "%", "not 100")),
            "original = final / (1 - percent/100)",
            "Recovers the original value before a percent decrease was applied.") { m ->
            val f = n(m, "final"); val p = n(m, "percent")
            FormResult(listOf(num("Original", PercentMath.originalBeforeDecrease(f, p))), sub("final / (1 - percent/100)", m))
        },

        // ---------------- B: discount ----------------
        form("disc_amount", "Discount amount", D,
            listOf(FormInput("price", "Original price"), FormInput("percent", "Discount", "%")),
            "amount = price * percent / 100",
            "How much money the discount removes.") { m ->
            val price = n(m, "price"); val p = n(m, "percent")
            FormResult(listOf(num("Discount amount", PercentMath.discountAmount(price, p))), sub("price * percent / 100", m))
        },
        form("disc_final", "Final price after discount", D,
            listOf(FormInput("price", "Original price"), FormInput("percent", "Discount", "%")),
            "final = price * (1 - percent/100)",
            "What the customer pays.") { m ->
            val price = n(m, "price"); val p = n(m, "percent")
            FormResult(listOf(num("Final price", PercentMath.discountedPrice(price, p))), sub("price * (1 - percent/100)", m))
        },
        form("disc_percent", "Discount percentage", D,
            listOf(FormInput("original", "Original price", constraint = "not zero"), FormInput("final", "Final price")),
            "percent = (original - final) / original * 100",
            "The effective discount that was applied.") { m ->
            val o = n(m, "original"); val f = n(m, "final")
            FormResult(listOf(num("Discount", PercentMath.discountPercentage(o, f), "%")), sub("(original - final) / original * 100", m))
        },
        form("disc_original", "Original price recovery", D,
            listOf(FormInput("final", "Final price"), FormInput("percent", "Discount", "%", "not 100")),
            "original = final / (1 - percent/100)",
            "The pre-discount price.") { m ->
            val f = n(m, "final"); val p = n(m, "percent")
            FormResult(listOf(num("Original price", PercentMath.originalPrice(f, p))), sub("final / (1 - percent/100)", m))
        },
        form("disc_sequential", "Sequential discounts", D,
            listOf(FormInput("price", "Original price"), FormInput("d1", "First discount", "%"),
                FormInput("d2", "Second discount", "%"), FormInput("d3", "Third discount", "% (optional, 0 = none)")),
            "final = price * (1-d1/100) * (1-d2/100) * (1-d3/100)",
            "Successive discounts compound. 10% then 20% is an effective 28%, never 30%.") { m ->
            val price = n(m, "price")
            val percents = listOf(n(m, "d1"), n(m, "d2"), n(m, "d3")).filter { !it.isZero }
            val r = PercentMath.successiveDiscounts(price, percents)
            FormResult(
                listOf(
                    num("Final price", r.finalPrice),
                    num("Effective discount", r.effectiveDiscountPercent, "%"),
                ),
                sub("price * product of (1 - d/100)", m),
            )
        },
        form("disc_effective", "Effective combined discount", D,
            listOf(FormInput("d1", "First discount", "%"), FormInput("d2", "Second discount", "%")),
            "effective = (1 - (1-d1/100)(1-d2/100)) * 100",
            "The single discount equivalent to two successive discounts.") { m ->
            val r = PercentMath.successiveDiscounts(CalcNumber.of(100), listOf(n(m, "d1"), n(m, "d2")))
            FormResult(listOf(num("Effective discount", r.effectiveDiscountPercent, "%")), sub("(1 - (1-d1/100)(1-d2/100)) * 100", m))
        },

        // ---------------- C: VAT / percentage tax ----------------
        form("vat_add", "Add tax to a base amount", V,
            listOf(FormInput("base", "Base amount (excl. tax)"), FormInput("rate", "Tax rate you enter", "%")),
            "total = base * (1 + rate/100);  tax = total - base",
            "The rate is the value you type. Axiom Calc never assumes or looks up a legal rate.") { m ->
            val base = n(m, "base"); val rate = n(m, "rate")
            val r = PercentMath.taxAdd(base, rate)
            FormResult(
                listOf(num("Tax amount", r.tax), num("Total (incl. tax)", r.total)),
                sub("total = base * (1 + rate/100)", m),
            )
        },
        form("vat_extract", "Extract tax included in a total", V,
            listOf(FormInput("total", "Total (incl. tax)"), FormInput("rate", "Tax rate you enter", "%", "not -100")),
            "base = total / (1 + rate/100);  tax = total - base",
            "Splits a tax-inclusive total into base and tax.") { m ->
            val total = n(m, "total"); val rate = n(m, "rate")
            val r = PercentMath.taxExtract(total, rate)
            FormResult(
                listOf(num("Base (excl. tax)", r.base), num("Tax contained", r.tax)),
                sub("base = total / (1 + rate/100)", m),
            )
        },
        form("vat_base_from_tax", "Base from a known tax amount", V,
            listOf(FormInput("tax", "Tax amount"), FormInput("rate", "Tax rate you enter", "%", "not zero")),
            "base = tax / (rate/100)",
            "Recovers the taxable base when the tax amount is known.") { m ->
            val tax = n(m, "tax"); val rate = n(m, "rate")
            if (rate.isZero) throw CalcException(CalcError.DivisionByZero("rate is zero"))
            val base = tax / rate * CalcNumber.of(100)
            FormResult(listOf(num("Base", base), num("Total", base + tax)), sub("base = tax / (rate/100)", m))
        },
        form("vat_total_from_tax", "Total from base and tax amount", V,
            listOf(FormInput("base", "Base amount"), FormInput("tax", "Tax amount", constraint = "base not zero")),
            "rate = tax / base * 100;  total = base + tax",
            "Derives the effective rate and the total.") { m ->
            val base = pos(n(m, "base"), "base"); val tax = n(m, "tax")
            val rate = tax / base * CalcNumber.of(100)
            FormResult(listOf(num("Effective rate", rate, "%"), num("Total", base + tax)), sub("rate = tax / base * 100", m))
        },

        // ---------------- D: profit / loss / markup / margin ----------------
        form("profit_basic", "Profit or loss amount", PR,
            listOf(FormInput("cost", "Cost price"), FormInput("selling", "Selling price")),
            "profit = selling - cost",
            "Positive means profit, negative means loss.") { m ->
            val c = n(m, "cost"); val s = n(m, "selling")
            val profit = PercentMath.profit(c, s)
            FormResult(
                listOf(
                    num("Profit / loss", profit),
                    num("Outcome", if (profit.signum >= 0) CalcNumber.ONE else CalcNumber.ZERO, "1 = profit, 0 = loss"),
                ),
                sub("selling - cost", m),
            )
        },
        form("profit_percent", "Profit percentage (on cost)", PR,
            listOf(FormInput("cost", "Cost price", constraint = "not zero"), FormInput("selling", "Selling price")),
            "profit% = (selling - cost) / cost * 100",
            "Profit relative to the cost price.") { m ->
            val c = n(m, "cost"); val s = n(m, "selling")
            FormResult(listOf(num("Profit %", PercentMath.profitPercentOnCost(c, s), "%")), sub("(selling - cost) / cost * 100", m))
        },
        form("loss_percent", "Loss percentage (on cost)", PR,
            listOf(FormInput("cost", "Cost price", constraint = "not zero"), FormInput("selling", "Selling price")),
            "loss% = (cost - selling) / cost * 100",
            "Loss relative to the cost price, positive when selling < cost.") { m ->
            val c = n(m, "cost"); val s = n(m, "selling")
            FormResult(listOf(num("Loss %", PercentMath.lossPercentOnCost(c, s), "%")), sub("(cost - selling) / cost * 100", m))
        },
        form("markup_percent", "Markup percentage", PR,
            listOf(FormInput("cost", "Cost price", constraint = "not zero"), FormInput("selling", "Selling price")),
            "markup% = (selling - cost) / cost * 100",
            "Markup uses the COST as denominator. It equals profit-on-cost by definition.") { m ->
            val c = n(m, "cost"); val s = n(m, "selling")
            FormResult(listOf(num("Markup %", PercentMath.markupPercent(c, s), "%")), sub("(selling - cost) / cost * 100", m))
        },
        form("margin_percent", "Profit margin", PR,
            listOf(FormInput("cost", "Cost price"), FormInput("selling", "Selling price", constraint = "not zero")),
            "margin% = (selling - cost) / selling * 100",
            "Margin uses the SELLING price as denominator, so it is always below the markup for the same numbers.") { m ->
            val c = n(m, "cost"); val s = n(m, "selling")
            FormResult(listOf(num("Margin %", PercentMath.marginPercent(c, s), "%")), sub("(selling - cost) / selling * 100", m))
        },
        form("selling_from_markup", "Selling price from cost and markup", PR,
            listOf(FormInput("cost", "Cost price"), FormInput("markup", "Markup", "%")),
            "selling = cost * (1 + markup/100)",
            "Prices from a target markup on cost.") { m ->
            val c = n(m, "cost"); val mu = n(m, "markup")
            FormResult(listOf(num("Selling price", PercentMath.sellingFromCostAndMarkup(c, mu))), sub("cost * (1 + markup/100)", m))
        },
        form("selling_from_margin", "Selling price from cost and margin", PR,
            listOf(FormInput("cost", "Cost price"), FormInput("margin", "Margin", "%", "below 100")),
            "selling = cost / (1 - margin/100)",
            "Prices from a target margin on selling.") { m ->
            val c = n(m, "cost"); val ma = n(m, "margin")
            FormResult(listOf(num("Selling price", PercentMath.sellingFromCostAndMargin(c, ma))), sub("cost / (1 - margin/100)", m))
        },
        form("cost_from_margin", "Cost from selling and margin", PR,
            listOf(FormInput("selling", "Selling price"), FormInput("margin", "Margin", "%")),
            "cost = selling * (1 - margin/100)",
            "The maximum cost compatible with a target margin.") { m ->
            val s = n(m, "selling"); val ma = n(m, "margin")
            FormResult(listOf(num("Cost price", PercentMath.costFromSellingAndMargin(s, ma))), sub("selling * (1 - margin/100)", m))
        },

        // ---------------- E: ratio & proportion ----------------
        form("ratio_simplify", "Ratio simplification", R,
            listOf(FormInput("a", "First term", constraint = "integer"), FormInput("b", "Second term", constraint = "integer, not both zero")),
            "a : b  divided by gcd(|a|, |b|)",
            "Reduces a ratio to lowest integer terms.") { m ->
            val a = requireInt(n(m, "a"), "a"); val b = requireInt(n(m, "b"), "b")
            val (x, y) = PercentMath.simplifyRatio(a, b)
            FormResult(
                listOf(ResultLine("Simplified ratio", "$x : $y")),
                sub("a : b / gcd", m),
            )
        },
        form("ratio_equivalent", "Equivalent ratios", R,
            listOf(FormInput("a", "First term", constraint = "integer"), FormInput("b", "Second term", constraint = "integer"),
                FormInput("k", "Scale factor", constraint = "integer, not zero")),
            "(a*k) : (b*k)",
            "Scales a ratio without changing its value.") { m ->
            val a = requireInt(n(m, "a"), "a"); val b = requireInt(n(m, "b"), "b"); val k = requireInt(n(m, "k"), "k")
            val (x, y) = PercentMath.equivalentRatio(a, b, k)
            FormResult(listOf(ResultLine("Equivalent ratio", "$x : $y")), sub("(a*k) : (b*k)", m))
        },
        form("ratio_direct", "Direct proportion (missing fourth term)", R,
            listOf(FormInput("a", "a", constraint = "not zero"), FormInput("b", "b"), FormInput("c", "c")),
            "a : b = c : x   ->   x = b * c / a",
            "Solves the missing term of a direct proportion.") { m ->
            val a = n(m, "a"); val b = n(m, "b"); val c = n(m, "c")
            FormResult(listOf(num("x", PercentMath.directProportion(a, b, c))), sub("x = b * c / a", m))
        },
        form("ratio_inverse", "Inverse proportion (missing fourth term)", R,
            listOf(FormInput("a", "a"), FormInput("b", "b"), FormInput("c", "c", constraint = "not zero")),
            "a * b = c * x   ->   x = a * b / c",
            "Solves the missing term of an inverse proportion.") { m ->
            val a = n(m, "a"); val b = n(m, "b"); val c = n(m, "c")
            FormResult(listOf(num("x", PercentMath.inverseProportion(a, b, c))), sub("x = a * b / c", m))
        },
        form("ratio_compare", "Ratio comparison", R,
            listOf(FormInput("a1", "First ratio, first term"), FormInput("b1", "First ratio, second term", constraint = "not zero"),
                FormInput("a2", "Second ratio, first term"), FormInput("b2", "Second ratio, second term", constraint = "not zero")),
            "compare a1/b1 with a2/b2",
            "Reports whether the two ratios are equal, or which is larger.") { m ->
            val cmp = PercentMath.compareRatios(n(m, "a1"), n(m, "b1"), n(m, "a2"), n(m, "b2"))
            FormResult(
                listOf(ResultLine("Comparison", if (cmp == 0) "equal" else if (cmp > 0) "first is larger" else "second is larger")),
                sub("compare a1/b1 with a2/b2", m),
            )
        },

        // ---------------- F: average & statistics ----------------
        form("avg_mean", "Arithmetic mean", A,
            listOf(FormInput("values", "Values, separated by commas or spaces")),
            "mean = sum / count",
            "The ordinary average of a dataset.") { m ->
            val values = parseDataset(raw(m, "values"))
            val s = com.shohankhan.axiomcalc.math.statistics.Statistics.sum(values)
            val mean = com.shohankhan.axiomcalc.math.statistics.Statistics.mean(values)
            FormResult(listOf(num("Count", CalcNumber.of(values.size)), num("Sum", s), num("Mean", mean)), sub("sum / count", m))
        },
        form("avg_weighted", "Weighted mean", A,
            listOf(FormInput("values", "Values, comma separated"), FormInput("weights", "Weights, comma separated", constraint = "same count, sum not zero")),
            "mean = sum(v*w) / sum(w)",
            "Average where each value carries its own weight.") { m ->
            val values = parseDataset(raw(m, "values")); val weights = parseDataset(raw(m, "weights"))
            val mean = com.shohankhan.axiomcalc.math.statistics.Statistics.weightedMean(values, weights)
            FormResult(listOf(num("Weighted mean", mean)), sub("sum(v*w) / sum(w)", m))
        },
        form("avg_median", "Median", A,
            listOf(FormInput("values", "Values, comma separated")),
            "middle value of the sorted set (mean of the two middle values when even)",
            "The central value, robust to outliers.") { m ->
            val values = parseDataset(raw(m, "values"))
            FormResult(listOf(num("Median", com.shohankhan.axiomcalc.math.statistics.Statistics.median(values))), sub("sorted middle", m))
        },
        form("avg_mode", "Mode", A,
            listOf(FormInput("values", "Values, comma separated")),
            "value(s) with the highest frequency",
            "All modes are listed; a set with all-distinct values has no mode.") { m ->
            val values = parseDataset(raw(m, "values"))
            val modes = com.shohankhan.axiomcalc.math.statistics.Statistics.mode(values)
            FormResult(
                listOf(ResultLine("Mode", if (modes.isEmpty()) "none (all values distinct)" else modes.joinToString(", ") { it.value.stripTrailingZeros().toPlainString() })),
                sub("most frequent", m),
            )
        },
        form("avg_range", "Range, sum and count", A,
            listOf(FormInput("values", "Values, comma separated")),
            "range = max - min",
            "Spread and totals of the dataset.") { m ->
            val values = parseDataset(raw(m, "values"))
            val st = com.shohankhan.axiomcalc.math.statistics.Statistics
            FormResult(
                listOf(num("Count", CalcNumber.of(values.size)), num("Sum", st.sum(values)),
                    num("Min", st.min(values)), num("Max", st.max(values)), num("Range", st.range(values))),
                sub("range = max - min", m),
            )
        },

        // ---------------- G: interest ----------------
        form("int_simple", "Simple interest", I,
            listOf(FormInput("principal", "Principal"), FormInput("rate", "Annual rate", "%"), FormInput("years", "Time", "years")),
            "I = P * r * t / 100;  A = P + I",
            "Interest that does not compound.") { m ->
            val r = Finance.simpleInterest(n(m, "principal"), n(m, "rate"), n(m, "years"))
            FormResult(listOf(num("Interest", r.interest), num("Final amount", r.amount)), sub("I = P*r*t/100", m))
        },
        form("int_compound", "Compound interest", I,
            listOf(FormInput("principal", "Principal"), FormInput("rate", "Annual rate", "%"),
                FormInput("years", "Time", "years"), FormInput("n", "Compounds per year", constraint = "positive integer")),
            "A = P (1 + r/(100 n))^(n t)",
            "Interest added n times per year.") { m ->
            val r = Finance.compoundInterest(n(m, "principal"), n(m, "rate"), n(m, "years"), requireIntPos(n(m, "n"), "n").toInt())
            FormResult(
                listOf(num("Final amount", r.amount), num("Interest earned", r.interest), num("Periods", r.periods)),
                sub("A = P (1 + r/(100n))^(nt)", m),
            )
        },
        form("int_principal", "Principal from final amount", I,
            listOf(FormInput("amount", "Final amount"), FormInput("rate", "Annual rate", "%"),
                FormInput("years", "Time", "years"), FormInput("n", "Compounds per year")),
            "P = A / (1 + r/(100 n))^(n t)",
            "The deposit that grows to the given amount.") { m ->
            val p = Finance.compoundPrincipal(n(m, "amount"), n(m, "rate"), n(m, "years"), requireIntPos(n(m, "n"), "n").toInt())
            FormResult(listOf(num("Principal", p)), sub("P = A / factor^(nt)", m))
        },
        form("int_rate", "Rate from principal and amount", I,
            listOf(FormInput("principal", "Principal", constraint = "not zero"), FormInput("amount", "Final amount"),
                FormInput("years", "Time", "years", constraint = "not zero"), FormInput("n", "Compounds per year")),
            "r = 100 n ((A/P)^(1/(n t)) - 1)",
            "The annual rate implied by the growth.") { m ->
            val r = Finance.compoundRate(n(m, "principal"), n(m, "amount"), n(m, "years"), requireIntPos(n(m, "n"), "n").toInt())
            FormResult(listOf(num("Annual rate", r, "%")), sub("r = 100n((A/P)^(1/nt) - 1)", m))
        },
        form("int_time", "Time from principal and amount", I,
            listOf(FormInput("principal", "Principal", constraint = "not zero"), FormInput("amount", "Final amount"),
                FormInput("rate", "Annual rate", "%", constraint = "not zero"), FormInput("n", "Compounds per year")),
            "t = ln(A/P) / (n ln(1 + r/(100 n)))",
            "How long the growth takes.") { m ->
            val t = Finance.compoundYears(n(m, "principal"), n(m, "amount"), n(m, "rate"), requireIntPos(n(m, "n"), "n").toInt())
            FormResult(listOf(num("Time", t, "years")), sub("t = ln(A/P) / (n ln(factor))", m))
        },

        // ---------------- H: installments ----------------
        form("inst_payment", "Fixed-rate installment (amortization)", IN,
            listOf(FormInput("principal", "Principal", constraint = "positive"), FormInput("rate", "Annual rate", "%"),
                FormInput("years", "Term", "years"), FormInput("perYear", "Payments per year", constraint = "positive integer, e.g. 12")),
            "A = P i / (1 - (1 + i)^-N),  i = r/(100 * perYear),  N = perYear * years",
            "The level payment of a fixed-rate loan. A zero rate degenerates to P/N.") { m ->
            val a = Finance.amortization(n(m, "principal"), n(m, "rate"), n(m, "years"), requireIntPos(n(m, "perYear"), "perYear").toInt())
            FormResult(
                listOf(
                    num("Payment per period", a.periodicPayment),
                    num("Number of payments", CalcNumber.of(a.numberOfPayments)),
                    num("Total repayment", a.totalRepayment),
                    num("Total interest", a.totalInterest),
                ),
                sub("A = P i / (1 - (1+i)^-N)", m),
            )
        },
        form("inst_total", "Total repayment and interest", IN,
            listOf(FormInput("principal", "Principal", constraint = "positive"), FormInput("rate", "Annual rate", "%"),
                FormInput("years", "Term", "years"), FormInput("perYear", "Payments per year")),
            "total = A * N;  interest = total - P",
            "What the loan costs in total.") { m ->
            val a = Finance.amortization(n(m, "principal"), n(m, "rate"), n(m, "years"), requireIntPos(n(m, "perYear"), "perYear").toInt())
            FormResult(listOf(num("Total repayment", a.totalRepayment), num("Total interest", a.totalInterest)), sub("total = A*N", m))
        },

        // ---------------- I: geometry ----------------
        form("geo_square", "Square", G,
            listOf(FormInput("side", "Side", constraint = "positive")),
            "area = s^2;  perimeter = 4s;  diagonal = s*sqrt(2)",
            "Area, perimeter and diagonal of a square.") { m ->
            val s = pos(n(m, "side"), "side")
            FormResult(
                listOf(num("Area", s * s), num("Perimeter", s * CalcNumber.of(4)),
                    num("Diagonal", sqrtDec(s * s * CalcNumber.TWO))),
                sub("area = s^2", m),
            )
        },
        form("geo_rectangle", "Rectangle", G,
            listOf(FormInput("length", "Length", constraint = "positive"), FormInput("width", "Width", constraint = "positive")),
            "area = l*w;  perimeter = 2(l+w);  diagonal = sqrt(l^2+w^2)",
            "Area, perimeter and diagonal of a rectangle.") { m ->
            val l = pos(n(m, "length"), "length"); val w = pos(n(m, "width"), "width")
            FormResult(
                listOf(num("Area", l * w), num("Perimeter", (l + w) * CalcNumber.TWO),
                    num("Diagonal", sqrtDec(l * l + w * w))),
                sub("area = l*w", m),
            )
        },
        form("geo_triangle_bh", "Triangle from base and height", G,
            listOf(FormInput("base", "Base", constraint = "positive"), FormInput("height", "Height", constraint = "positive")),
            "area = base * height / 2",
            "Area of any triangle from a base and its perpendicular height.") { m ->
            val b = pos(n(m, "base"), "base"); val h = pos(n(m, "height"), "height")
            FormResult(listOf(num("Area", b * h / CalcNumber.TWO)), sub("area = b*h/2", m))
        },
        form("geo_triangle_heron", "Triangle from three sides (Heron)", G,
            listOf(FormInput("a", "Side a", constraint = "positive"), FormInput("b", "Side b", constraint = "positive"),
                FormInput("c", "Side c", constraint = "positive, triangle inequality")),
            "s = (a+b+c)/2;  area = sqrt(s(s-a)(s-b)(s-c))",
            "Area from three sides; the triangle inequality is validated.") { m ->
            val a = pos(n(m, "a"), "a"); val b = pos(n(m, "b"), "b"); val c = pos(n(m, "c"), "c")
            if (a + b <= c || a + c <= b || b + c <= a) {
                throw CalcException(CalcError.Domain("triangle", "these sides violate the triangle inequality"))
            }
            val s = (a + b + c) / CalcNumber.TWO
            val area = sqrtDec(s * (s - a) * (s - b) * (s - c))
            FormResult(listOf(num("Semi-perimeter", s), num("Area", area), num("Perimeter", a + b + c)), sub("Heron's formula", m))
        },
        form("geo_circle", "Circle", G,
            listOf(FormInput("radius", "Radius", constraint = "positive")),
            "area = pi r^2;  circumference = 2 pi r;  diameter = 2r",
            "Area, circumference and diameter of a circle.") { m ->
            val r = pos(n(m, "radius"), "radius")
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            FormResult(
                listOf(num("Area", pi * r * r), num("Circumference", CalcNumber.TWO * pi * r), num("Diameter", CalcNumber.TWO * r)),
                sub("area = pi r^2", m),
            )
        },
        form("geo_sphere", "Sphere", G,
            listOf(FormInput("radius", "Radius", constraint = "positive")),
            "surface = 4 pi r^2;  volume = (4/3) pi r^3",
            "Surface area and volume of a sphere.") { m ->
            val r = pos(n(m, "radius"), "radius")
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            FormResult(
                listOf(num("Surface area", CalcNumber.of(4) * pi * r * r),
                    num("Volume", pi * r * r * r * CalcNumber.of(4) / CalcNumber.of(3))),
                sub("V = (4/3) pi r^3", m),
            )
        },
        form("geo_cube", "Cube", G,
            listOf(FormInput("side", "Side", constraint = "positive")),
            "surface = 6 s^2;  volume = s^3;  space diagonal = s sqrt(3)",
            "Surface area, volume and space diagonal of a cube.") { m ->
            val s = pos(n(m, "side"), "side")
            FormResult(
                listOf(num("Surface area", CalcNumber.of(6) * s * s), num("Volume", s * s * s),
                    num("Space diagonal", sqrtDec(s * s * CalcNumber.of(3)))),
                sub("V = s^3", m),
            )
        },
        form("geo_cuboid", "Cuboid", G,
            listOf(FormInput("length", "Length", constraint = "positive"), FormInput("width", "Width", constraint = "positive"),
                FormInput("height", "Height", constraint = "positive")),
            "surface = 2(lw+lh+wh);  volume = lwh;  diagonal = sqrt(l^2+w^2+h^2)",
            "Surface area, volume and space diagonal of a rectangular box.") { m ->
            val l = pos(n(m, "length"), "length"); val w = pos(n(m, "width"), "width"); val h = pos(n(m, "height"), "height")
            FormResult(
                listOf(num("Surface area", CalcNumber.TWO * (l * w + l * h + w * h)),
                    num("Volume", l * w * h),
                    num("Space diagonal", sqrtDec(l * l + w * w + h * h))),
                sub("V = lwh", m),
            )
        },
        form("geo_cylinder", "Cylinder", G,
            listOf(FormInput("radius", "Radius", constraint = "positive"), FormInput("height", "Height", constraint = "positive")),
            "volume = pi r^2 h;  total surface = 2 pi r (r + h)",
            "Volume and total surface area of a right circular cylinder.") { m ->
            val r = pos(n(m, "radius"), "radius"); val h = pos(n(m, "height"), "height")
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            FormResult(
                listOf(num("Volume", pi * r * r * h), num("Total surface", CalcNumber.TWO * pi * r * (r + h))),
                sub("V = pi r^2 h", m),
            )
        },
        form("geo_cone", "Cone", G,
            listOf(FormInput("radius", "Radius", constraint = "positive"), FormInput("height", "Height", constraint = "positive")),
            "volume = (1/3) pi r^2 h;  slant = sqrt(r^2+h^2)",
            "Volume and slant height of a right circular cone.") { m ->
            val r = pos(n(m, "radius"), "radius"); val h = pos(n(m, "height"), "height")
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            FormResult(
                listOf(num("Volume", pi * r * r * h / CalcNumber.of(3)), num("Slant height", sqrtDec(r * r + h * h))),
                sub("V = (1/3) pi r^2 h", m),
            )
        },
        form("geo_trapezoid", "Trapezoid", G,
            listOf(FormInput("a", "Parallel side a", constraint = "positive"), FormInput("b", "Parallel side b", constraint = "positive"),
                FormInput("height", "Height", constraint = "positive")),
            "area = (a + b) h / 2",
            "Area of a trapezoid from its parallel sides and height.") { m ->
            val a = pos(n(m, "a"), "a"); val b = pos(n(m, "b"), "b"); val h = pos(n(m, "height"), "height")
            FormResult(listOf(num("Area", (a + b) * h / CalcNumber.TWO)), sub("area = (a+b)h/2", m))
        },
        form("geo_ellipse", "Ellipse", G,
            listOf(FormInput("a", "Semi-major axis", constraint = "positive"), FormInput("b", "Semi-minor axis", constraint = "positive")),
            "area = pi a b",
            "Area of an ellipse.") { m ->
            val a = pos(n(m, "a"), "a"); val b = pos(n(m, "b"), "b")
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            FormResult(listOf(num("Area", pi * a * b)), sub("area = pi a b", m))
        },
        form("geo_sector", "Circular sector", G,
            listOf(FormInput("radius", "Radius", constraint = "positive"), FormInput("angle", "Central angle", "degrees", "0 < angle <= 360")),
            "area = (angle/360) pi r^2;  arc = (angle/360) 2 pi r",
            "Area and arc length of a sector.") { m ->
            val r = pos(n(m, "radius"), "radius"); val ang = pos(n(m, "angle"), "angle")
            if (ang > CalcNumber.of(360)) throw CalcException(CalcError.Domain("angle", "a sector angle cannot exceed 360 degrees"))
            val pi = com.shohankhan.axiomcalc.math.constants.ConstantRegistry.PI.value
            val frac = ang / CalcNumber.of(360)
            FormResult(listOf(num("Area", frac * pi * r * r), num("Arc length", frac * CalcNumber.TWO * pi * r)), sub("area = (angle/360) pi r^2", m))
        },

        // ---------------- J: algebra ----------------
        form("alg_eval_linear", "Evaluate a linear expression", AL,
            listOf(FormInput("a", "Coefficient a"), FormInput("b", "Constant b"), FormInput("x", "x")),
            "value = a x + b",
            "Evaluates a linear expression at x.") { m ->
            val v = n(m, "a") * n(m, "x") + n(m, "b")
            FormResult(listOf(num("Value", v)), sub("a x + b", m))
        },
        form("alg_solve_linear_x", "Solve a x + b = c for x", AL,
            listOf(FormInput("a", "a", constraint = "not zero"), FormInput("b", "b"), FormInput("c", "c")),
            "x = (c - b) / a",
            "Rearranges a linear equation for its variable.") { m ->
            val root = LinearEquations.solveOneVariable(n(m, "a"), n(m, "b") - n(m, "c"))
            FormResult(
                listOf(ResultLine("Outcome", root.outcome.name), root.x?.let { num("x", it) }.let { l -> l ?: ResultLine("x", "none") }),
                sub("x = (c - b) / a", m),
            )
        },
        form("alg_solve_linear_a", "Solve a x + b = c for a", AL,
            listOf(FormInput("x", "x", constraint = "not zero"), FormInput("b", "b"), FormInput("c", "c")),
            "a = (c - b) / x",
            "Missing-coefficient solving where uniquely determined.") { m ->
            val a = (n(m, "c") - n(m, "b")) / n(m, "x")
            FormResult(listOf(num("a", a)), sub("a = (c - b) / x", m))
        },
        form("alg_poly_eval", "Polynomial evaluation (degree up to 3)", AL,
            listOf(FormInput("c0", "c0"), FormInput("c1", "c1"), FormInput("c2", "c2"), FormInput("c3", "c3"), FormInput("x", "x")),
            "p(x) = c0 + c1 x + c2 x^2 + c3 x^3",
            "Evaluates a cubic polynomial at x.") { m ->
            val x = n(m, "x")
            val v = n(m, "c0") + n(m, "c1") * x + n(m, "c2") * x * x + n(m, "c3") * x * x * x
            FormResult(listOf(num("p(x)", v)), sub("c0 + c1x + c2x^2 + c3x^3", m))
        },

        // ---------------- K: fractions ----------------
        form("frac_simplify", "Fraction simplification", F,
            listOf(FormInput("numerator", "Numerator", constraint = "integer"), FormInput("denominator", "Denominator", constraint = "integer, not zero")),
            "reduce by gcd",
            "Reduces a fraction to lowest terms.") { m ->
            val f = Fraction.of(requireInt(n(m, "numerator"), "numerator"), requireInt(n(m, "denominator"), "denominator"))
            FormResult(
                listOf(ResultLine("Simplified", f.toString()), ResultLine("Mixed form", f.toMixedString()), num("Decimal", f.ToDecimalSafe())),
                sub("reduce by gcd", m),
            )
        },
        form("frac_add", "Fraction addition", F,
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("n2", "Numerator 2", constraint = "integer"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "n1/d1 + n2/d2",
            "Exact fraction addition.") { m ->
            val r = fracOp(m, "add")
            FormResult(listOf(ResultLine("Result", r.toString()), ResultLine("Mixed form", r.toMixedString())), sub("n1/d1 + n2/d2", m))
        },
        form("frac_sub", "Fraction subtraction", F,
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("n2", "Numerator 2", constraint = "integer"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "n1/d1 - n2/d2",
            "Exact fraction subtraction.") { m ->
            val r = fracOp(m, "sub")
            FormResult(listOf(ResultLine("Result", r.toString()), ResultLine("Mixed form", r.toMixedString())), sub("n1/d1 - n2/d2", m))
        },
        form("frac_mul", "Fraction multiplication", F,
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("n2", "Numerator 2", constraint = "integer"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "(n1*n2)/(d1*d2)",
            "Exact fraction multiplication.") { m ->
            val r = fracOp(m, "mul")
            FormResult(listOf(ResultLine("Result", r.toString()), ResultLine("Mixed form", r.toMixedString())), sub("(n1*n2)/(d1*d2)", m))
        },
        form("frac_div", "Fraction division", F,
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("n2", "Numerator 2", constraint = "integer, not zero"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "(n1*d2)/(d1*n2)",
            "Exact fraction division; the divisor fraction may not be zero.") { m ->
            val r = fracOp(m, "div")
            FormResult(listOf(ResultLine("Result", r.toString()), ResultLine("Mixed form", r.toMixedString())), sub("(n1*d2)/(d1*n2)", m))
        },
        form("frac_compare", "Fraction comparison", F,
            listOf(FormInput("n1", "Numerator 1", constraint = "integer"), FormInput("d1", "Denominator 1", constraint = "integer, not zero"),
                FormInput("n2", "Numerator 2", constraint = "integer"), FormInput("d2", "Denominator 2", constraint = "integer, not zero")),
            "compare n1/d1 with n2/d2",
            "Exact comparison without decimal rounding.") { m ->
            val a = fracOf(m, 1); val b = fracOf(m, 2)
            val cmp = a.compareTo(b)
            FormResult(listOf(ResultLine("Comparison", if (cmp == 0) "equal" else if (cmp > 0) "first is larger" else "second is larger")), sub("cross-multiply", m))
        },
        form("frac_mixed", "Mixed number to improper fraction", F,
            listOf(FormInput("whole", "Whole part", constraint = "integer"), FormInput("numerator", "Numerator", constraint = "integer >= 0"),
                FormInput("denominator", "Denominator", constraint = "integer, not zero")),
            "(whole*denominator + numerator)/denominator",
            "Converts a mixed number to an improper fraction.") { m ->
            val w = requireInt(n(m, "whole"), "whole")
            val nu = requireInt(n(m, "numerator"), "numerator")
            if (nu.signum() < 0) throw CalcException(CalcError.InvalidArgument("numerator", "the fractional part must not be negative"))
            val de = requireInt(n(m, "denominator"), "denominator")
            val magnitude = w.abs().multiply(de).add(nu)
            val signed = if (w.signum() < 0) magnitude.negate() else magnitude
            val f = Fraction.of(signed, de)
            FormResult(listOf(ResultLine("Improper fraction", f.toString()), num("Decimal", f.ToDecimalSafe())), sub("(whole*d + n)/d", m))
        },
        form("frac_decimal", "Fraction to decimal", F,
            listOf(FormInput("numerator", "Numerator", constraint = "integer"), FormInput("denominator", "Denominator", constraint = "integer, not zero")),
            "numerator / denominator",
            "Decimal expansion, rounded to 12 places.") { m ->
            val f = Fraction.of(requireInt(n(m, "numerator"), "numerator"), requireInt(n(m, "denominator"), "denominator"))
            FormResult(listOf(num("Decimal", f.ToDecimalSafe()), ResultLine("Mixed form", f.toMixedString())), sub("n / d", m))
        },

        // ---------------- L: time arithmetic ----------------
        form("time_add", "Duration addition", T,
            listOf(FormInput("h1", "Hours 1"), FormInput("m1", "Minutes 1"), FormInput("s1", "Seconds 1"),
                FormInput("h2", "Hours 2"), FormInput("m2", "Minutes 2"), FormInput("s2", "Seconds 2")),
            "(h1:m1:s1) + (h2:m2:s2)",
            "Durations may exceed 24 hours; components are normalized.") { m ->
            val a = Duration.normalize(intOf(n(m, "h1")), intOf(n(m, "m1")), intOf(n(m, "s1")))
            val b = Duration.normalize(intOf(n(m, "h2")), intOf(n(m, "m2")), intOf(n(m, "s2")))
            val r = a + b
            FormResult(listOf(ResultLine("Total", r.format()), num("Decimal hours", CalcNumber.of(r.toDecimalHours()))), sub("add then normalize", m))
        },
        form("time_sub", "Duration subtraction", T,
            listOf(FormInput("h1", "Hours 1"), FormInput("m1", "Minutes 1"), FormInput("s1", "Seconds 1"),
                FormInput("h2", "Hours 2"), FormInput("m2", "Minutes 2"), FormInput("s2", "Seconds 2")),
            "(h1:m1:s1) - (h2:m2:s2)",
            "A negative result is allowed and displayed with a minus sign.") { m ->
            val a = Duration.normalize(intOf(n(m, "h1")), intOf(n(m, "m1")), intOf(n(m, "s1")))
            val b = Duration.normalize(intOf(n(m, "h2")), intOf(n(m, "m2")), intOf(n(m, "s2")))
            val r = a - b
            FormResult(listOf(ResultLine("Difference", r.format()), num("Decimal hours", CalcNumber.of(r.toDecimalHours()))), sub("subtract then normalize", m))
        },
        form("time_convert", "Duration conversion", T,
            listOf(FormInput("hours", "Hours"), FormInput("minutes", "Minutes"), FormInput("seconds", "Seconds")),
            "total seconds = h*3600 + m*60 + s",
            "Converts a duration to seconds, decimal minutes and decimal hours.") { m ->
            val d = Duration.normalize(intOf(n(m, "hours")), intOf(n(m, "minutes")), intOf(n(m, "seconds")))
            FormResult(
                listOf(num("Seconds", CalcNumber.of(d.totalSeconds)), num("Decimal minutes", CalcNumber.of(d.toDecimalMinutes())),
                    num("Decimal hours", CalcNumber.of(d.toDecimalHours()))),
                sub("h*3600 + m*60 + s", m),
            )
        },
        form("time_interval", "Interval between two clock times", T,
            listOf(FormInput("h1", "From hour", constraint = "0..23"), FormInput("m1", "From minute", constraint = "0..59"), FormInput("s1", "From second", constraint = "0..59"),
                FormInput("h2", "To hour", constraint = "0..23"), FormInput("m2", "To minute", constraint = "0..59"), FormInput("s2", "To second", constraint = "0..59")),
            "elapsed = to - from (same day)",
            "Elapsed time on one day; negative when the second time is earlier.") { m ->
            val r = Duration.betweenClockTimes(intOf(n(m, "h1")).toInt(), intOf(n(m, "m1")).toInt(), intOf(n(m, "s1")).toInt(),
                intOf(n(m, "h2")).toInt(), intOf(n(m, "m2")).toInt(), intOf(n(m, "s2")).toInt())
            FormResult(listOf(ResultLine("Elapsed", r.format()), num("Seconds", CalcNumber.of(r.totalSeconds))), sub("to - from", m))
        },

        // ---------------- M: date arithmetic ----------------
        form("date_diff", "Difference between two dates", DA,
            listOf(FormInput("start", "Start date", constraint = "yyyy-mm-dd"), FormInput("end", "End date", constraint = "yyyy-mm-dd")),
            "exclusive = end - start;  inclusive = exclusive + 1",
            "Both day-count conventions, labelled separately.") { m ->
            val a = CivilDate.parse(raw(m, "start")); val b = CivilDate.parse(raw(m, "end"))
            FormResult(
                listOf(
                    num("Elapsed days (exclusive)", CalcNumber.of(CivilDate.daysBetweenExclusive(a, b))),
                    num("Calendar days (inclusive)", CalcNumber.of(CivilDate.daysBetweenInclusive(a, b))),
                    num("Weeks (elapsed / 7)", CalcNumber.of(CivilDate.daysBetweenExclusive(a, b) / 7)),
                ),
                sub("end - start", m),
            )
        },
        form("date_add", "Add days to a date", DA,
            listOf(FormInput("date", "Date", constraint = "yyyy-mm-dd"), FormInput("days", "Days to add", constraint = "integer")),
            "result = date + days",
            "Calendar-correct addition across months, years and leap days.") { m ->
            val d = CivilDate.parse(raw(m, "date")); val days = requireInt(n(m, "days"), "days")
            val r = d + days.toLong()
            FormResult(listOf(ResultLine("Result date", r.toString()), ResultLine("Weekday", weekday(r))), sub("date + days", m))
        },
        form("date_sub", "Subtract days from a date", DA,
            listOf(FormInput("date", "Date", constraint = "yyyy-mm-dd"), FormInput("days", "Days to subtract", constraint = "integer")),
            "result = date - days",
            "Calendar-correct subtraction.") { m ->
            val d = CivilDate.parse(raw(m, "date")); val days = requireInt(n(m, "days"), "days")
            val r = d - days.toLong()
            FormResult(listOf(ResultLine("Result date", r.toString()), ResultLine("Weekday", weekday(r))), sub("date - days", m))
        },
        form("date_add_months", "Add months to a date", DA,
            listOf(FormInput("date", "Date", constraint = "yyyy-mm-dd"), FormInput("months", "Months to add", constraint = "integer")),
            "result = date + months (day clamped to month length)",
            "Adding a month to Jan 31 yields Feb 28/29, never an invalid date.") { m ->
            val d = CivilDate.parse(raw(m, "date")); val months = requireInt(n(m, "months"), "months")
            val r = d.plusMonths(months.toInt())
            FormResult(listOf(ResultLine("Result date", r.toString()), ResultLine("Weekday", weekday(r))), sub("date + months", m))
        },
        form("date_leap", "Leap-year test and day of year", DA,
            listOf(FormInput("date", "Date", constraint = "yyyy-mm-dd")),
            "leap = (y%4==0 && y%100!=0) || y%400==0",
            "Whether the year is a leap year and the date's day-of-year.") { m ->
            val d = CivilDate.parse(raw(m, "date"))
            val doy = CivilDate.daysBetweenInclusive(CivilDate(d.year, 1, 1), d)
            FormResult(
                listOf(ResultLine("Leap year", if (d.isLeapYear()) "yes" else "no"), num("Day of year", CalcNumber.of(doy)),
                    ResultLine("Weekday", weekday(d))),
                sub("leap rule", m),
            )
        },
    )

    // ---- shared form helpers ---------------------------------------------------

    private fun form(
        id: String,
        title: String,
        category: FormulaCategory,
        inputs: List<FormInput>,
        formulaText: String,
        explanation: String,
        compute: (Map<String, String>) -> FormResult,
    ): FormulaForm = FormulaForm(id, title, category, inputs, formulaText, explanation, compute)

    private fun requireInt(v: CalcNumber, id: String): BigInteger =
        v.toBigIntegerOrNull() ?: throw CalcException(CalcError.InvalidArgument(id, "'$id' must be an integer, got $v"))

    private fun requireIntPos(v: CalcNumber, id: String): BigInteger {
        val bi = requireInt(v, id)
        if (bi.signum() <= 0) throw CalcException(CalcError.InvalidArgument(id, "'$id' must be a positive integer"))
        if (bi.bitLength() > 20) throw CalcException(CalcError.ResourceLimit(id, "'$id' is implausibly large"))
        return bi
    }

    private fun intOf(v: CalcNumber): Long = requireInt(v, "time component").toLong()

    fun parseDataset(text: String): List<CalcNumber> {
        val parts = text.split(',', ';', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) throw CalcException(CalcError.EmptyDataset("no numbers supplied"))
        if (parts.size > 5_000) throw CalcException(CalcError.ResourceLimit("dataset size", "${parts.size} values"))
        return parts.map { p ->
            p.toBigDecimalOrNull()?.let { CalcNumber.of(it) }
                ?: throw CalcException(CalcError.InvalidArgument("value", "'$p' is not a number"))
        }
    }

    private fun fracOf(m: Map<String, String>, which: Int): Fraction =
        Fraction.of(requireInt(n(m, "n$which"), "n$which"), requireInt(n(m, "d$which"), "d$which"))

    private fun fracOp(m: Map<String, String>, op: String): Fraction {
        val a = fracOf(m, 1); val b = fracOf(m, 2)
        return when (op) {
            "add" -> a + b
            "sub" -> a - b
            "mul" -> a * b
            "div" -> a / b
            else -> throw CalcException(CalcError.UnsupportedOperation(op))
        }
    }


    private fun sqrtDec(v: CalcNumber): CalcNumber =
        CalcNumber.of(com.shohankhan.axiomcalc.math.precision.Precision.sqrt(v.value))

    private fun weekday(d: CivilDate): String {
        val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        return names[Math.floorMod(d.toEpochDay() + 3, 7).toInt()]
    }

    private fun Fraction.ToDecimalSafe(): CalcNumber = toDecimal(12)

    // ---- lookup ------------------------------------------------------------------

    fun byCategory(category: FormulaCategory): List<FormulaForm> = all.filter { it.category == category }
    fun byId(id: String): FormulaForm? = all.firstOrNull { it.id == id }

    fun search(query: String): List<FormulaForm> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { f ->
            f.title.lowercase().contains(q) ||
                f.category.title.lowercase().contains(q) ||
                f.explanation.lowercase().contains(q) ||
                f.formulaText.lowercase().contains(q)
        }
    }
}
