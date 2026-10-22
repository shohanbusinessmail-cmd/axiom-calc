package com.shohankhan.axiomcalc.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.errors.CalcError

/**
 * Maps the typed error model to user-facing English text.
 *
 * Rules enforced here:
 *  - every code has an explicit message; nothing falls through to a raw
 *    exception text, a stack trace or the word "NaN";
 *  - messages say what is wrong and what the user can do, without pretending a
 *    value was produced;
 *  - [CalcError.Incomplete] is not an error while typing, so it maps to an empty
 *    string and callers keep showing the live preview instead.
 */
@StringRes
fun CalcError.messageRes(): Int = when (this) {
    is CalcError.Incomplete -> R.string.error_incomplete
    is CalcError.Syntax -> R.string.error_syntax
    is CalcError.UnknownFunction -> R.string.error_unknown_function
    is CalcError.UnbalancedParentheses -> R.string.error_unbalanced_parentheses
    is CalcError.DivisionByZero -> R.string.error_divide_by_zero
    is CalcError.Domain -> R.string.error_domain
    is CalcError.Overflow -> R.string.error_overflow
    is CalcError.Underflow -> R.string.error_underflow
    is CalcError.InvalidArgument -> R.string.error_invalid_argument
    is CalcError.UnsupportedOperation -> R.string.error_unsupported
    is CalcError.NonConvergence -> R.string.error_no_convergence
    is CalcError.ResourceLimit -> R.string.error_resource_limit
    is CalcError.DimensionMismatch -> R.string.error_dimension_mismatch
    is CalcError.SingularMatrix -> R.string.error_singular_matrix
    is CalcError.EmptyDataset -> R.string.error_empty_dataset
    is CalcError.InsufficientSample -> R.string.error_insufficient_sample
}

@Composable
fun CalcError.userMessage(): String = stringResource(messageRes())

/**
 * Optional technical detail, shown only in the History detail / developer
 * affordances. Never used as the primary message.
 */
fun CalcError.technicalDetail(): String = detail
