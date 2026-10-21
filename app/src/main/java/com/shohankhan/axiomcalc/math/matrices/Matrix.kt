package com.shohankhan.axiomcalc.math.matrices

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException
import com.shohankhan.axiomcalc.math.precision.Precision
import kotlin.math.abs

/**
 * Dense real matrix with the numerically standard algorithms:
 *
 *  * determinant and inverse use LU / Gauss-Jordan elimination with **partial
 *    pivoting**, which is the stable choice for a calculator-scale problem.
 *  * rank uses row echelon reduction with an explicit tolerance; a pivot whose
 *    magnitude is below [TOLERANCE] is treated as zero, which is what makes
 *    singular-matrix detection honest rather than accidental.
 *
 * Entries are IEEE-754 doubles: exact for integer matrices of moderate size and
 * the conventional choice for linear algebra at this scale. Exact rational
 * linear algebra is intentionally not claimed (see MATHEMATICS_SPEC.md).
 */
class Matrix(val rows: Int, val cols: Int, val data: DoubleArray) {

    init {
        if (rows <= 0 || cols <= 0) {
            throw CalcException(CalcError.InvalidArgument("matrix size", "dimensions must be positive, got ${rows}x$cols"))
        }
        if (rows.toLong() * cols > Precision.MAX_MATRIX_CELLS) {
            throw CalcException(
                CalcError.ResourceLimit("matrix cells", "${rows}x$cols exceeds ${Precision.MAX_MATRIX_CELLS}"),
            )
        }
        require(data.size == rows * cols) { "data length ${data.size} != $rows*$cols" }
        for (v in data) {
            if (v.isNaN() || v.isInfinite()) {
                throw CalcException(CalcError.InvalidArgument("matrix entry", "entry is not finite: $v"))
            }
        }
    }

    operator fun get(r: Int, c: Int): Double = data[r * cols + c]

    val isSquare: Boolean get() = rows == cols

    fun transpose(): Matrix {
        val out = DoubleArray(rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) out[c * rows + r] = this[r, c]
        return Matrix(cols, rows, out)
    }

    operator fun plus(o: Matrix): Matrix = elementwise(o, "addition") { a, b -> a + b }
    operator fun minus(o: Matrix): Matrix = elementwise(o, "subtraction") { a, b -> a - b }

    private fun elementwise(o: Matrix, what: String, f: (Double, Double) -> Double): Matrix {
        if (rows != o.rows || cols != o.cols) {
            throw CalcException(
                CalcError.DimensionMismatch(
                    "$what requires equal dimensions: ${rows}x$cols vs ${o.rows}x${o.cols}",
                ),
            )
        }
        val out = DoubleArray(data.size) { i -> f(data[i], o.data[i]) }
        return Matrix(rows, cols, out)
    }

    operator fun times(o: Matrix): Matrix {
        if (cols != o.rows) {
            throw CalcException(
                CalcError.DimensionMismatch(
                    "multiplication requires left columns == right rows: ${rows}x$cols * ${o.rows}x${o.cols}",
                ),
            )
        }
        val out = DoubleArray(rows * o.cols)
        for (r in 0 until rows) {
            for (c in 0 until o.cols) {
                var sum = 0.0
                for (k in 0 until cols) sum += this[r, k] * o[k, c]
                out[r * o.cols + c] = sum
            }
        }
        return Matrix(rows, o.cols, out)
    }

    fun timesScalar(s: Double): Matrix = Matrix(rows, cols, DoubleArray(data.size) { data[it] * s })

    /** Matrix-vector product; the vector is a column of length [cols]. */
    fun timesVector(v: DoubleArray): DoubleArray {
        if (v.size != cols) {
            throw CalcException(
                CalcError.DimensionMismatch("vector length ${v.size} != matrix columns $cols"),
            )
        }
        return DoubleArray(rows) { r ->
            var sum = 0.0
            for (c in 0 until cols) sum += this[r, c] * v[c]
            sum
        }
    }

    fun determinant(): Double {
        requireSquare("determinant")
        val lu = luDecomposition()
        var det = 1.0
        for (i in 0 until rows) det *= lu[i * rows + i]
        return if (luSwaps % 2 == 0) det else -det
    }

    fun rank(): Int {
        val work = data.copyOf()
        var rank = 0
        var row = 0
        for (col in 0 until cols) {
            var pivot = -1
            var best = TOLERANCE
            for (r in row until rows) {
                val v = abs(work[r * cols + col])
                if (v > best) { best = v; pivot = r }
            }
            if (pivot < 0) continue
            swapRows(work, pivot, row)
            val pv = work[row * cols + col]
            for (r in row + 1 until rows) {
                val factor = work[r * cols + col] / pv
                if (factor == 0.0) continue
                for (c in col until cols) work[r * cols + c] -= factor * work[row * cols + c]
            }
            rank++
            row++
            if (row == rows) break
        }
        return rank
    }

    fun isSingular(): Boolean = isSquare && rank() < rows

    fun inverse(): Matrix {
        requireSquare("inverse")
        val n = rows
        val aug = DoubleArray(n * 2 * n)
        for (r in 0 until n) {
            for (c in 0 until n) aug[r * 2 * n + c] = this[r, c]
            aug[r * 2 * n + n + r] = 1.0
        }
        for (col in 0 until n) {
            var pivot = -1
            var best = TOLERANCE
            for (r in col until n) {
                val v = abs(aug[r * 2 * n + col])
                if (v > best) { best = v; pivot = r }
            }
            if (pivot < 0) {
                throw CalcException(CalcError.SingularMatrix("matrix is singular; no inverse exists"))
            }
            swapRowsAug(aug, pivot, col, 2 * n)
            val pv = aug[col * 2 * n + col]
            for (c in 0 until 2 * n) aug[col * 2 * n + c] /= pv
            for (r in 0 until n) {
                if (r == col) continue
                val factor = aug[r * 2 * n + col]
                if (factor == 0.0) continue
                for (c in 0 until 2 * n) aug[r * 2 * n + c] -= factor * aug[col * 2 * n + c]
            }
        }
        val out = DoubleArray(n * n)
        for (r in 0 until n) for (c in 0 until n) out[r * n + c] = aug[r * 2 * n + n + c]
        return Matrix(n, n, out)
    }

    // ---- LU with partial pivoting (determinant support) ---------------------

    private var luSwaps = 0

    private fun luDecomposition(): DoubleArray {
        val n = rows
        val a = data.copyOf()
        luSwaps = 0
        for (col in 0 until n) {
            var pivot = col
            var best = abs(a[col * n + col])
            for (r in col + 1 until n) {
                val v = abs(a[r * n + col])
                if (v > best) { best = v; pivot = r }
            }
            if (best <= TOLERANCE) {
                throw CalcException(CalcError.SingularMatrix("matrix is singular; determinant is 0 by structure"))
            }
            if (pivot != col) {
                swapRows(a, pivot, col)
                luSwaps++
            }
            for (r in col + 1 until n) {
                val factor = a[r * n + col] / a[col * n + col]
                a[r * n + col] = factor
                for (c in col + 1 until n) a[r * n + c] -= factor * a[col * n + c]
            }
        }
        return a
    }

    private fun swapRows(a: DoubleArray, r1: Int, r2: Int) {
        for (c in 0 until cols) {
            val t = a[r1 * cols + c]; a[r1 * cols + c] = a[r2 * cols + c]; a[r2 * cols + c] = t
        }
    }

    private fun swapRowsAug(a: DoubleArray, r1: Int, r2: Int, width: Int) {
        for (c in 0 until width) {
            val t = a[r1 * width + c]; a[r1 * width + c] = a[r2 * width + c]; a[r2 * width + c] = t
        }
    }

    private fun requireSquare(operation: String) {
        if (!isSquare) {
            throw CalcException(
                CalcError.DimensionMismatch("$operation requires a square matrix, got ${rows}x$cols"),
            )
        }
    }

    fun format(decimals: Int = 4): String =
        (0 until rows).joinToString("\n") { r ->
            "[ " + (0 until cols).joinToString("  ") { c -> trim(this[r, c], decimals) } + " ]"
        }

    private fun trim(v: Double, decimals: Int): String {
        if (v == 0.0) return "0"
        val s = String.format("%.${decimals}f", v)
        return s.trimEnd('0').trimEnd('.')
    }

    override fun toString(): String = format()

    override fun equals(other: Any?): Boolean =
        other is Matrix && rows == other.rows && cols == other.cols && data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * (31 * rows + cols) + data.contentHashCode()

    companion object {
        /** Pivot magnitude below which a value counts as zero. */
        const val TOLERANCE: Double = 1e-10

        fun identity(n: Int): Matrix {
            val out = DoubleArray(n * n)
            for (i in 0 until n) out[i * n + i] = 1.0
            return Matrix(n, n, out)
        }

        fun of(rows: Int, cols: Int, values: List<Double>): Matrix {
            if (values.size != rows * cols) {
                throw CalcException(
                    CalcError.DimensionMismatch("expected ${rows * cols} entries but got ${values.size}"),
                )
            }
            return Matrix(rows, cols, values.toDoubleArray())
        }

        /**
         * Parses matrix text such as `1, 2; 3, 4` or the same on separate lines.
         * Rows are separated by `;` or newline, entries by `,` or whitespace.
         */
        fun parse(text: String): Matrix {
            val rowTexts = text.split(';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (rowTexts.isEmpty()) {
                throw CalcException(CalcError.InvalidArgument("matrix", "no rows supplied"))
            }
            val rowsList = rowTexts.map { row ->
                row.split(',', ' ', '\t').map { it.trim() }.filter { it.isNotEmpty() }.map { cell ->
                    cell.toDoubleOrNull()
                        ?: throw CalcException(CalcError.InvalidArgument("matrix entry", "'$cell' is not a number"))
                }
            }
            val cols = rowsList[0].size
            rowsList.forEachIndexed { i, row ->
                if (row.size != cols) {
                    throw CalcException(
                        CalcError.DimensionMismatch("row ${i + 1} has ${row.size} entries, expected $cols"),
                    )
                }
            }
            return Matrix(rowsList.size, cols, rowsList.flatten().toDoubleArray())
        }
    }
}
