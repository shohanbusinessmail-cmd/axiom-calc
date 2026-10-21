package com.shohankhan.axiomcalc.feature.mathlab

import androidx.lifecycle.ViewModel
import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.core.formatting.DisplayPolicy
import com.shohankhan.axiomcalc.core.formatting.NumberFormatter
import com.shohankhan.axiomcalc.core.formatting.NumberNotation
import com.shohankhan.axiomcalc.core.formatting.RoundingPolicy
import com.shohankhan.axiomcalc.math.complex.Complex
import com.shohankhan.axiomcalc.math.evaluator.ExpressionEngine
import com.shohankhan.axiomcalc.math.matrices.Matrix
import com.shohankhan.axiomcalc.math.precision.CalcNumber
import com.shohankhan.axiomcalc.math.solvers.Bases
import com.shohankhan.axiomcalc.math.solvers.LinearEquations
import com.shohankhan.axiomcalc.math.solvers.NumericSolvers
import com.shohankhan.axiomcalc.math.solvers.SolverStatus
import com.shohankhan.axiomcalc.math.statistics.Statistics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One labelled output line; shared by every lab module. */
data class LabLine(val label: String, val value: String, val note: String? = null)

/** Common shape of a lab module's state. */
abstract class LabState {
    abstract val lines: List<LabLine>
    abstract val error: CalcError?
    abstract val steps: List<String>
}

private val labPolicy = DisplayPolicy(autoPrecision = true, decimals = 10, grouping = true)

private fun fmt(value: CalcNumber): String = NumberFormatter.format(value, labPolicy)

private fun fmt(value: Double): String =
    NumberFormatter.format(CalcNumber.of(value), labPolicy)

/** Parses a whitespace/comma separated dataset, reporting typed errors. */
internal fun parseDataset(text: String): List<CalcNumber> =
    text.split(',', ' ', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }.map { CalcNumber.parse(it) }

// ---- matrices ---------------------------------------------------------------------

data class MatrixLabState(
    val matrixA: String = "1, 2; 3, 4",
    val matrixB: String = "5, 6; 7, 8",
    val vectorB: String = "",
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

class MatrixLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(MatrixLabState())
    val state: StateFlow<MatrixLabState> = _state

    fun setA(value: String) = _state.update { it.copy(matrixA = value) }
    fun setB(value: String) = _state.update { it.copy(matrixB = value) }
    fun setVector(value: String) = _state.update { it.copy(vectorB = value) }

    /**
     * Runs the selected operation.
     *
     * Every failure surfaces as a typed error ([CalcError.DimensionMismatch],
     * [CalcError.SingularMatrix], [CalcError.Syntax]); the panel never shows a
     * partially-filled matrix or a guessed inverse.
     */
    fun run(operation: MatrixOperation) {
        try {
            val a = Matrix.parse(_state.value.matrixA)
            val steps = ArrayList<String>()
            steps += "A is ${a.rows}×${a.cols}."
            val lines = ArrayList<LabLine>()
            when (operation) {
                MatrixOperation.DIMENSIONS -> {
                    lines += LabLine("A shape", "${a.rows} × ${a.cols}")
                    lines += LabLine("A trace", if (a.isSquare) fmt(a.trace()) else "—", if (a.isSquare) null else "trace needs a square matrix")
                    lines += LabLine("A is square", a.isSquare.toString())
                }

                MatrixOperation.TRANSPOSE -> {
                    val t = a.transpose()
                    steps += "Transpose swaps rows and columns: (Aᵀ)ᵢⱼ = Aⱼᵢ."
                    lines += LabLine("Aᵀ", t.format())
                }

                MatrixOperation.DETERMINANT -> {
                    if (!a.isSquare) throw CalcException(CalcError.DimensionMismatch("determinant needs a square matrix"))
                    val det = a.determinant()
                    steps += "Computed by LU decomposition with partial pivoting."
                    lines += LabLine("det(A)", fmt(det))
                    lines += LabLine("rank(A)", a.rank().toString())
                    lines += LabLine("singular", a.isSingular().toString())
                }

                MatrixOperation.INVERSE -> {
                    if (!a.isSquare) throw CalcException(CalcError.DimensionMismatch("inverse needs a square matrix"))
                    val inv = a.inverse()
                    steps += "Gauss–Jordan elimination with partial pivoting."
                    steps += "Restriction: a singular matrix (det = 0 within tolerance 1e-10) has no inverse."
                    lines += LabLine("det(A)", fmt(a.determinant()))
                    lines += LabLine("A⁻¹", inv.format())
                    val check = a * inv
                    lines += LabLine("A·A⁻¹", check.format(2), "verification: should be the identity")
                }

                MatrixOperation.ADD, MatrixOperation.SUBTRACT -> {
                    val b = Matrix.parse(_state.value.matrixB)
                    steps += "B is ${b.rows}×${b.cols}."
                    steps += "Restriction: both matrices must have the same shape."
                    val r = if (operation == MatrixOperation.ADD) a + b else a - b
                    lines += LabLine(if (operation == MatrixOperation.ADD) "A + B" else "A − B", r.format())
                }

                MatrixOperation.MULTIPLY -> {
                    val b = Matrix.parse(_state.value.matrixB)
                    steps += "B is ${b.rows}×${b.cols}."
                    steps += "Restriction: cols(A) must equal rows(B)."
                    val r = a * b
                    lines += LabLine("A · B", r.format())
                    lines += LabLine("shape", "${r.rows} × ${r.cols}")
                }

                MatrixOperation.SCALE -> {
                    val b = Matrix.parse(_state.value.matrixB)
                    if (b.rows != 1 || b.cols != 1) {
                        throw CalcException(CalcError.InvalidArgument("enter the scalar as a 1×1 matrix"))
                    }
                    lines += LabLine("${b.data[0]} · A", a.timesScalar(b.data[0]).format())
                }

                MatrixOperation.SOLVE -> {
                    val raw = _state.value.vectorB
                    if (raw.isBlank()) throw CalcException(CalcError.EmptyDataset("enter the right-hand side vector"))
                    val rhs = parseDataset(raw).map { it.toDouble() }.toDoubleArray()
                    if (!a.isSquare) throw CalcException(CalcError.DimensionMismatch("A must be square to solve A·x = b"))
                    if (rhs.size != a.rows) {
                        throw CalcException(
                            CalcError.DimensionMismatch("b has ${rhs.size} entries but A has ${a.rows} rows"),
                        )
                    }
                    steps += "Gauss–Jordan with partial pivoting on the augmented matrix [A|b]."
                    steps += "Ranks decide the outcome: rank(A) < rank([A|b]) means no solution;"
                    steps += "rank(A) < n means infinitely many; otherwise the solution is unique."
                    val solution = LinearEquations.solveSystem(a.toRows(), rhs.toList())
                    lines += LabLine("outcome", solution.outcome.name)
                    lines += LabLine("rank(A)", solution.rankOfCoefficients.toString())
                    lines += LabLine("rank([A|b])", solution.rankOfAugmented.toString())
                    val x = solution.solution
                    if (x != null) {
                        lines += LabLine("x", x.joinToString(",  ") { fmt(it) })
                        val residual = a.timesVector(x).mapIndexed { i, v -> v - rhs[i] }
                        lines += LabLine(
                            "max |residual|",
                            fmt(residual.maxOf { kotlin.math.abs(it) }),
                            "verification that A·x reproduces b",
                        )
                    } else {
                        lines += LabLine("x", "no unique solution")
                    }
                }
            }
            _state.update { it.copy(lines = lines, error = null, steps = steps) }
        } catch (e: CalcException) {
            _state.update { it.copy(lines = emptyList(), error = e.error, steps = emptyList()) }
        }
    }
}

enum class MatrixOperation(val label: String) {
    DIMENSIONS("Shape"),
    ADD("A + B"),
    SUBTRACT("A − B"),
    MULTIPLY("A · B"),
    SCALE("k · A"),
    TRANSPOSE("Aᵀ"),
    DETERMINANT("det"),
    INVERSE("A⁻¹"),
    SOLVE("Solve A·x = b"),
}

// ---- statistics ---------------------------------------------------------------------

data class StatisticsLabState(
    val dataset: String = "2, 4, 4, 6",
    val weights: String = "",
    val sample: Boolean = true,
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

class StatisticsLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(StatisticsLabState())
    val state: StateFlow<StatisticsLabState> = _state

    fun setDataset(value: String) = _state.update { it.copy(dataset = value) }
    fun setWeights(value: String) = _state.update { it.copy(weights = value) }
    fun setSample(value: Boolean) = _state.update { it.copy(sample = value) }

    /**
     * Computes the summary block.
     *
     * Population and sample statistics are labelled explicitly, and the sample
     * variance/standard deviation are reported as unavailable for n < 2 rather
     * than silently substituted — matching [Statistics.summarize].
     */
    fun run() {
        try {
            val values = parseDataset(_state.value.dataset)
            val summary = Statistics.summarize(values)
            val steps = ArrayList<String>()
            steps += "n = ${summary.count}."
            steps += "Sorted values: ${Statistics.sorted(values).joinToString(", ") { fmt(it) }}."
            steps += "Population variance divides by n; sample variance divides by n − 1."
            steps += "Restriction: sample variance and sample standard deviation need n ≥ 2."
            val lines = ArrayList<LabLine>()
            lines += LabLine("count", summary.count.toString())
            lines += LabLine("sum", fmt(summary.sum))
            lines += LabLine("mean", fmt(summary.mean))
            lines += LabLine("median", fmt(summary.median))
            lines += LabLine(
                "mode",
                if (summary.modes.isEmpty()) "none" else summary.modes.joinToString(", ") { fmt(it) },
                if (summary.modes.size > 1) "multimodal" else null,
            )
            lines += LabLine("min", fmt(summary.min))
            lines += LabLine("max", fmt(summary.max))
            lines += LabLine("range", fmt(summary.range))
            lines += LabLine("population variance", fmt(summary.populationVariance))
            lines += LabLine("population std dev", fmt(summary.populationStdDev))
            lines += LabLine(
                "sample variance",
                summary.sampleVariance?.let { fmt(it) } ?: "needs n ≥ 2",
            )
            lines += LabLine(
                "sample std dev",
                summary.sampleStdDev?.let { fmt(it) } ?: "needs n ≥ 2",
            )

            val weightText = _state.value.weights
            if (weightText.isNotBlank()) {
                val weights = parseDataset(weightText)
                steps += "Weighted mean = Σ(wᵢ·xᵢ) / Σwᵢ."
                lines += LabLine("weighted mean", fmt(Statistics.weightedMean(values, weights)))
            }
            _state.update {
                it.copy(
                    lines = lines,
                    error = null,
                    steps = steps,
                    sample = it.sample,
                )
            }
        } catch (e: CalcException) {
            _state.update { it.copy(lines = emptyList(), error = e.error, steps = emptyList()) }
        }
    }
}

// ---- complex --------------------------------------------------------------------------

data class ComplexLabState(
    val aRe: String = "3",
    val aIm: String = "4",
    val bRe: String = "1",
    val bIm: String = "-2",
    val polarInput: Boolean = false,
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

class ComplexLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(ComplexLabState())
    val state: StateFlow<ComplexLabState> = _state

    fun set(field: ComplexField, value: String) = _state.update {
        when (field) {
            ComplexField.A_RE -> it.copy(aRe = value)
            ComplexField.A_IM -> it.copy(aIm = value)
            ComplexField.B_RE -> it.copy(bRe = value)
            ComplexField.B_IM -> it.copy(bIm = value)
        }
    }

    fun setPolarInput(value: Boolean) = _state.update { it.copy(polarInput = value) }

    /**
     * Runs one complex operation.
     *
     * Inputs are read as rectangular (re, im) or polar (r, θ in radians)
     * according to [ComplexLabState.polarInput]; results are always reported in
     * both forms so no conversion is hidden from the user.
     */
    fun run(operation: ComplexOperation) {
        try {
            val polar = _state.value.polarInput
            val a = read(_state.value.aRe, _state.value.aIm, polar)
            val b = read(_state.value.bRe, _state.value.bIm, polar)
            val steps = ArrayList<String>()
            steps += if (polar) {
                "Inputs read as polar (r, θ in radians) and converted with re = r·cos θ, im = r·sin θ."
            } else {
                "Inputs read as rectangular (re, im)."
            }
            val result: Complex? = when (operation) {
                ComplexOperation.ADD -> a.add(b)
                ComplexOperation.SUBTRACT -> a.subtract(b)
                ComplexOperation.MULTIPLY -> a.multiply(b)
                ComplexOperation.DIVIDE -> a.divide(b)
                ComplexOperation.CONJUGATE -> a.conjugate()
                ComplexOperation.MAGNITUDE -> null
                ComplexOperation.ARGUMENT -> null
                ComplexOperation.CONVERT -> null
            }
            steps += "Restriction: division by 0 + 0i is undefined and is reported as an error."
            val lines = ArrayList<LabLine>()
            when (operation) {
                ComplexOperation.MAGNITUDE -> {
                    lines += LabLine("|a|", fmt(a.magnitude()))
                    lines += LabLine("|b|", fmt(b.magnitude()))
                }

                ComplexOperation.ARGUMENT -> {
                    lines += LabLine("arg(a)", fmt(a.argument()), "radians, principal value in (−π, π]")
                    lines += LabLine("arg(b)", fmt(b.argument()), "radians, principal value in (−π, π]")
                    lines += LabLine("arg(a) in degrees", fmt(Math.toDegrees(a.argument())))
                }

                ComplexOperation.CONVERT -> {
                    val pa = a.toPolar()
                    val pb = b.toPolar()
                    lines += LabLine("a rectangular", a.toRectangularString())
                    lines += LabLine("a polar", "r = ${fmt(pa.r)}, θ = ${fmt(pa.thetaRadians)} rad")
                    lines += LabLine("b rectangular", b.toRectangularString())
                    lines += LabLine("b polar", "r = ${fmt(pb.r)}, θ = ${fmt(pb.thetaRadians)} rad")
                }

                else -> {
                    val r = result ?: throw CalcException(CalcError.InvalidArgument("no result for $operation"))
                    lines += LabLine(operation.label, r.toRectangularString())
                    val pr = r.toPolar()
                    lines += LabLine("polar form", "r = ${fmt(pr.r)}, θ = ${fmt(pr.thetaRadians)} rad")
                    lines += LabLine("magnitude", fmt(r.magnitude()))
                    lines += LabLine("conjugate", r.conjugate().toRectangularString())
                }
            }
            _state.update { it.copy(lines = lines, error = null, steps = steps) }
        } catch (e: CalcException) {
            _state.update { it.copy(lines = emptyList(), error = e.error, steps = emptyList()) }
        }
    }

    private fun read(first: String, second: String, polar: Boolean): Complex {
        val x = first.trim().toDoubleOrThrow()
        val y = second.trim().toDoubleOrThrow()
        return if (polar) Complex.ofPolar(x, y) else Complex(x, y)
    }

    private fun String.toDoubleOrThrow(): Double = toDoubleOrNull()
        ?: throw CalcException(CalcError.InvalidArgument("'$this' is not a number"))
}

enum class ComplexField { A_RE, A_IM, B_RE, B_IM }

enum class ComplexOperation(val label: String) {
    ADD("a + b"),
    SUBTRACT("a − b"),
    MULTIPLY("a · b"),
    DIVIDE("a ÷ b"),
    CONJUGATE("conjugate(a)"),
    MAGNITUDE("magnitude"),
    ARGUMENT("argument"),
    CONVERT("convert forms"),
}

// ---- numerical solvers ------------------------------------------------------------------

data class SolverLabState(
    val expression: String = "x^3 - x - 2",
    val lower: String = "1",
    val upper: String = "2",
    val start: String = "1.5",
    val tolerance: String = "1e-10",
    val method: SolverMethod = SolverMethod.BISECTION,
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

enum class SolverMethod(val label: String) { BISECTION("Bisection"), NEWTON("Newton–Raphson") }

class SolverLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(SolverLabState())
    val state: StateFlow<SolverLabState> = _state

    fun setExpression(value: String) = _state.update { it.copy(expression = value) }
    fun setLower(value: String) = _state.update { it.copy(lower = value) }
    fun setUpper(value: String) = _state.update { it.copy(upper = value) }
    fun setStart(value: String) = _state.update { it.copy(start = value) }
    fun setTolerance(value: String) = _state.update { it.copy(tolerance = value) }
    fun setMethod(value: SolverMethod) = _state.update { it.copy(method = value) }

    /**
     * Finds a root of f(x) = 0 with the expression evaluated by the shared
     * engine.
     *
     * Convergence is only reported when the residual actually satisfies the
     * tolerance; otherwise the status is surfaced verbatim (invalid bracket, no
     * convergence) and no number is presented as a root.
     */
    fun run() {
        val s = _state.value
        val parseError = ExpressionEngine.parseError(s.expression.replace("x", "(x)"))
        if (parseError != null && parseError !is CalcError.Incomplete) {
            _state.update { it.copy(lines = emptyList(), error = parseError, steps = emptyList()) }
            return
        }
        val f: (Double) -> Double = { x ->
            val text = s.expression.replace("x", "($x)")
            ExpressionEngine.evaluate(text).valueOrNull()?.toDouble() ?: Double.NaN
        }
        val tol = s.tolerance.trim().toDoubleOrNull() ?: 1e-10
        val result = when (s.method) {
            SolverMethod.BISECTION -> NumericSolvers.bisection(
                f,
                s.lower.trim().toDoubleOrNull() ?: Double.NaN,
                s.upper.trim().toDoubleOrNull() ?: Double.NaN,
                tolerance = tol,
            )

            SolverMethod.NEWTON -> NumericSolvers.newtonRaphson(
                f,
                s.start.trim().toDoubleOrNull() ?: Double.NaN,
                tolerance = tol,
            )
        }
        val steps = ArrayList<String>()
        steps += "f(x) = ${s.expression}."
        steps += if (s.method == SolverMethod.BISECTION) {
            "Bisection needs a sign change on [${s.lower}, ${s.upper}]; the interval halves each step."
        } else {
            "Newton–Raphson iterates x ← x − f(x)/f′(x) with a numerical derivative from ${s.start}."
        }
        steps += "Tolerance ${tol}; convergence is only claimed when |f(x)| < tolerance."
        steps += "Restriction: a stalled iterate or a missing sign change is reported, never rounded into a root."
        val lines = ArrayList<LabLine>()
        if (result.status == SolverStatus.CONVERGED && result.root != null) {
            lines += LabLine("root", fmt(result.root))
            lines += LabLine("f(root)", fmt(result.residual), "residual at the reported root")
            lines += LabLine("iterations", result.iterations.toString())
            lines += LabLine("method", result.method)
            lines += LabLine("status", result.status.name)
        } else {
            lines += LabLine("status", result.status.name)
            lines += LabLine("iterations", result.iterations.toString())
            if (result.note.isNotBlank()) lines += LabLine("reason", result.note)
        }
        _state.update {
            it.copy(
                lines = lines,
                // A non-converged run is reported through the status line rather
                // than as an error banner, because it is a legitimate answer.
                error = null,
                steps = steps,
            )
        }
    }
}

// ---- bases -------------------------------------------------------------------------------

data class BasesLabState(
    val input: String = "255",
    val fromBase: Int = 10,
    val operand: String = "16",
    val operationBase: Int = 16,
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

class BasesLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(BasesLabState())
    val state: StateFlow<BasesLabState> = _state

    fun setInput(value: String) = _state.update { it.copy(input = value) }
    fun setFromBase(value: Int) = _state.update { it.copy(fromBase = value) }
    fun setOperand(value: String) = _state.update { it.copy(operand = value) }
    fun setOperationBase(value: Int) = _state.update { it.copy(operationBase = value) }

    /** Converts to every supported base, then runs the selected arithmetic op. */
    fun run(operation: BaseOperation) {
        try {
            val s = _state.value
            val steps = ArrayList<String>()
            steps += "Supported bases: ${Bases.SUPPORTED.joinToString(", ")}."
            steps += "Digits outside the source base are rejected rather than skipped."
            val lines = ArrayList<LabLine>()
            val value = Bases.parse(s.input, s.fromBase)
            Bases.SUPPORTED.forEach { base ->
                lines += LabLine("base $base", Bases.render(value, base))
            }
            if (operation != BaseOperation.CONVERT) {
                val base = s.operationBase
                Bases.requireSupported(base)
                val a = s.input
                val b = s.operand
                steps += "Arithmetic is performed in base $base on '$a' and '$b'."
                val (resultText, label) = when (operation) {
                    BaseOperation.ADD -> Bases.add(a, b, base) to "a + b"
                    BaseOperation.SUBTRACT -> Bases.subtract(a, b, base) to "a − b"
                    BaseOperation.MULTIPLY -> Bases.multiply(a, b, base) to "a × b"
                    BaseOperation.DIVIDE -> {
                        val (q, r) = Bases.divideWithRemainder(a, b, base)
                        "$q remainder $r" to "a ÷ b"
                    }

                    BaseOperation.CONVERT -> "" to ""
                }
                lines += LabLine("$label (base $base)", resultText)
            }
            _state.update { it.copy(lines = lines, error = null, steps = steps) }
        } catch (e: CalcException) {
            _state.update { it.copy(lines = emptyList(), error = e.error, steps = emptyList()) }
        }
    }
}

enum class BaseOperation(val label: String) {
    CONVERT("Convert"),
    ADD("Add"),
    SUBTRACT("Subtract"),
    MULTIPLY("Multiply"),
    DIVIDE("Divide"),
}

// ---- engineering notation / significant figures / rounding -----------------------------------

data class FormattingLabState(
    val value: String = "1234567",
    val digits: String = "4",
    val decimals: String = "2",
    val notation: NumberNotation = NumberNotation.ENGINEERING,
    val rounding: RoundingPolicy = RoundingPolicy.HALF_UP,
    override val lines: List<LabLine> = emptyList(),
    override val error: CalcError? = null,
    override val steps: List<String> = emptyList(),
) : LabState()

class FormattingLabViewModel : ViewModel() {

    private val _state = MutableStateFlow(FormattingLabState())
    val state: StateFlow<FormattingLabState> = _state

    fun setValue(value: String) = _state.update { it.copy(value = value) }
    fun setDigits(value: String) = _state.update { it.copy(digits = value) }
    fun setDecimals(value: String) = _state.update { it.copy(decimals = value) }
    fun setNotation(value: NumberNotation) = _state.update { it.copy(notation = value) }
    fun setRounding(value: RoundingPolicy) = _state.update { it.copy(rounding = value) }

    /**
     * Presentation-only module: it never changes the stored value, and it shows
     * the rules it applied so the user can see why a number looks the way it does.
     */
    fun run() {
        try {
            val s = _state.value
            val value = CalcNumber.parse(s.value.trim())
            val digits = s.digits.trim().toIntOrNull() ?: throw CalcException(
                CalcError.InvalidArgument("significant figures must be a whole number"),
            )
            val decimals = s.decimals.trim().toIntOrNull() ?: throw CalcException(
                CalcError.InvalidArgument("decimal places must be a whole number"),
            )
            if (digits < 1) throw CalcException(CalcError.InvalidArgument("significant figures must be at least 1"))
            val steps = ArrayList<String>()
            steps += "Scientific notation keeps the mantissa in [1, 10)."
            steps += "Engineering notation keeps the exponent a multiple of 3 and the mantissa in [1, 1000)."
            steps += "Automatic decimal notation switches to scientific outside an adjusted exponent of [−6, 12]."
            steps += "Rounding mode ${s.rounding.label} is applied only to the presentation layer."
            val lines = ArrayList<LabLine>()
            lines += LabLine("decimal", NumberFormatter.format(value, labPolicy.copy(notation = NumberNotation.DECIMAL)))
            lines += LabLine("scientific", NumberFormatter.scientific(value, digits))
            lines += LabLine("engineering", NumberFormatter.engineering(value, digits))
            lines += LabLine("$digits significant figures", NumberFormatter.format(NumberFormatter.significantFigures(value, digits), labPolicy))
            lines += LabLine("rounded to $decimals dp", NumberFormatter.format(NumberFormatter.roundTo(value, decimals, s.rounding), labPolicy))
            lines += LabLine("truncated to $decimals dp", NumberFormatter.format(NumberFormatter.truncateTo(value, decimals), labPolicy))
            lines += LabLine("inspection", NumberFormatter.inspect(value), "internal exact representation")
            _state.update { it.copy(lines = lines, error = null, steps = steps) }
        } catch (e: CalcException) {
            _state.update { it.copy(lines = emptyList(), error = e.error, steps = emptyList()) }
        }
    }
}
