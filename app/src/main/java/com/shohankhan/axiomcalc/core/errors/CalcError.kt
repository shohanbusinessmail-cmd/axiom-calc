package com.shohankhan.axiomcalc.core.errors

/**
 * Typed error model for every calculation failure in Axiom Calc.
 *
 * Design rules (see MATHEMATICS_SPEC.md section "Error model"):
 *  - A failure is always an explicit value, never a silently returned `0`.
 *  - [CalcError] carries a stable machine-readable [code] so the UI can map it
 *    to a localized, actionable message without string matching.
 *  - [detail] holds the technical diagnostic (position, operand, limit that was
 *    hit). It is logged and shown only in developer-facing surfaces, never as
 *    the primary user-facing message.
 */
sealed class CalcError(open val detail: String = "") {

    /** Stable machine-readable identifier, also used as the UI message key. */
    abstract val code: String

    /**
     * The expression was cut off mid-thought, e.g. `2 +` or `sqrt(`.
     * This is an expected state while typing and must not be shown as an error.
     */
    data class Incomplete(override val detail: String = "expression ended early") : CalcError(detail) {
        override val code: String get() = "INCOMPLETE"
    }

    /** The token stream violates the grammar at [position]. */
    data class Syntax(val position: Int, override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "SYNTAX"
    }

    /** An identifier resolved to neither a known function nor a known constant. */
    data class UnknownFunction(val name: String) : CalcError("unknown identifier '$name'") {
        override val code: String get() = "UNKNOWN_FUNCTION"
    }

    /** Parenthesis count did not balance. */
    data class UnbalancedParentheses(val position: Int = -1) : CalcError("unbalanced parentheses at $position") {
        override val code: String get() = "UNBALANCED_PARENTHESES"
    }

    /** Division or modulus by an exactly-zero divisor. */
    data class DivisionByZero(override val detail: String = "divisor is zero") : CalcError(detail) {
        override val code: String get() = "DIVISION_BY_ZERO"
    }

    /** Argument outside the mathematical domain of the operation. */
    data class Domain(val operation: String, override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "DOMAIN"
    }

    /** Magnitude exceeds the representable range of the engine. */
    data class Overflow(override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "OVERFLOW"
    }

    /** Magnitude underflows to zero at the working precision. */
    data class Underflow(override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "UNDERFLOW"
    }

    /** An argument failed a validation rule that is not a domain rule. */
    data class InvalidArgument(val argument: String, override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "INVALID_ARGUMENT"
    }

    /** The operation exists in the grammar but is not implemented for these operands. */
    data class UnsupportedOperation(override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "UNSUPPORTED_OPERATION"
    }

    /** An iterative method exhausted its iteration budget without converging. */
    data class NonConvergence(
        val iterations: Int,
        val lastResidual: Double,
        override val detail: String = "",
    ) : CalcError(detail) {
        override val code: String get() = "NON_CONVERGENCE"
    }

    /** A guard rail (depth, node count, factorial bound, digit bound) was exceeded. */
    data class ResourceLimit(val limit: String, override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "RESOURCE_LIMIT"
    }

    /** Two operands have incompatible shapes (matrices, vectors, datasets). */
    data class DimensionMismatch(override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "DIMENSION_MISMATCH"
    }

    /** A square matrix has no inverse (determinant zero at the rank tolerance). */
    data class SingularMatrix(override val detail: String = "") : CalcError(detail) {
        override val code: String get() = "SINGULAR_MATRIX"
    }

    /** A dataset was empty where at least one element is required. */
    data class EmptyDataset(override val detail: String = "the dataset has no elements") : CalcError(detail) {
        override val code: String get() = "EMPTY_DATASET"
    }

    /** A dataset had too few elements for the requested statistic. */
    data class InsufficientSample(val required: Int, val actual: Int) :
        CalcError("statistic needs >= $required observations but only $actual were supplied") {
        override val code: String get() = "INSUFFICIENT_SAMPLE"
    }
}

/**
 * Exception used internally by the recursive evaluator so that deeply nested
 * code can abort evaluation with a typed error without threading a Result
 * through every stack frame.
 *
 * It is **never** allowed to escape the public engine boundary: every public
 * entry point catches it and converts it to [CalcResult.Failure].
 */
internal class CalcException(val error: CalcError) : Exception(error.code)
