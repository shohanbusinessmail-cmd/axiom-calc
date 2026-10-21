package com.shohankhan.axiomcalc.feature.mathlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.Axiom
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.AxiomField
import com.shohankhan.axiomcalc.core.ui.ResultRow
import com.shohankhan.axiomcalc.core.ui.SectionCard
import com.shohankhan.axiomcalc.core.ui.userMessage
import com.shohankhan.axiomcalc.core.formatting.NumberNotation
import com.shohankhan.axiomcalc.core.formatting.RoundingPolicy
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import com.shohankhan.axiomcalc.navigation.LabModuleId

/** Math Lab (primary destination 6): the module picker. */
@Composable
fun MathLabScreen(
    onOpenModule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modules = listOf(
        LabModuleId.MATRICES to (R.string.lab_tab_matrices to R.string.lab_matrices_body),
        LabModuleId.STATISTICS to (R.string.lab_tab_statistics to R.string.lab_statistics_body),
        LabModuleId.COMPLEX to (R.string.lab_tab_complex to R.string.lab_complex_body),
        LabModuleId.NUMERIC_SOLVER to (R.string.lab_tab_solvers to R.string.lab_solver_body),
        LabModuleId.BASES to (R.string.lab_tab_bases to R.string.lab_bases_body),
        LabModuleId.FORMATTING to (R.string.lab_tab_engineering to R.string.lab_formatting_body),
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
    ) {
        items(modules, key = { it.first }) { (id, texts) ->
            Card(
                onClick = { onOpenModule(id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(AxiomDimens.Radius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(AxiomDimens.CardPadding)) {
                    Text(stringResource(texts.first), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(texts.second),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Routes a module id to its panel; an unknown id shows an explicit empty state. */
@Composable
fun LabModuleScreen(moduleId: String, modifier: Modifier = Modifier) {
    when (moduleId) {
        LabModuleId.MATRICES -> MatrixLabPanel(modifier)
        LabModuleId.STATISTICS -> StatisticsLabPanel(modifier)
        LabModuleId.COMPLEX -> ComplexLabPanel(modifier)
        LabModuleId.NUMERIC_SOLVER -> SolverLabPanel(modifier)
        LabModuleId.BASES -> BasesLabPanel(modifier)
        LabModuleId.FORMATTING -> FormattingLabPanel(modifier)
        else -> com.shohankhan.axiomcalc.core.ui.EmptyState(
            iconRes = R.drawable.ic_nav_math_lab,
            title = stringResource(R.string.tool_not_found_title),
            body = stringResource(R.string.tool_not_found_body),
            modifier = modifier,
        )
    }
}

/** Shared renderer for every lab module's output. */
@Composable
private fun LabResults(state: LabState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.error != null -> SectionCard(title = stringResource(R.string.formulas_result_title)) {
                Text(
                    text = state.error!!.userMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Axiom.extended.errorText,
                )
            }

            state.lines.isNotEmpty() -> {
                SectionCard(title = stringResource(R.string.formulas_result_title)) {
                    state.lines.forEach { line ->
                        ResultRow(label = line.label, value = line.value)
                        if (line.note != null) {
                            Text(
                                text = line.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
                if (state.steps.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.quick_solve_steps_title)) {
                        state.steps.forEachIndexed { index, step ->
                            Text(
                                text = "${index + 1}. $step",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            else -> SectionCard(title = stringResource(R.string.formulas_result_title)) {
                Text(
                    text = stringResource(R.string.lab_run_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabScaffold(
    modifier: Modifier = Modifier,
    inputs: @Composable () -> Unit,
    results: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AxiomDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        inputs()
        results()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RunButton(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) { Text(label) }
    }
}

// ---- matrices ------------------------------------------------------------------------

@Composable
fun MatrixLabPanel(
    modifier: Modifier = Modifier,
    viewModel: MatrixLabViewModel = axiomViewModel { _, _ -> MatrixLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(title = stringResource(R.string.lab_matrices_inputs)) {
                AxiomField(
                    value = state.matrixA,
                    onValueChange = viewModel::setA,
                    label = stringResource(R.string.lab_matrix_a),
                    numeric = false,
                    hint = "1, 2; 3, 4",
                )
                AxiomField(
                    value = state.matrixB,
                    onValueChange = viewModel::setB,
                    label = stringResource(R.string.lab_matrix_b),
                    numeric = false,
                    hint = "5, 6; 7, 8",
                )
                AxiomField(
                    value = state.vectorB,
                    onValueChange = viewModel::setVector,
                    label = stringResource(R.string.lab_vector_b),
                    hint = "5, 11",
                )
                Text(
                    text = stringResource(R.string.lab_matrix_syntax),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            SectionCard(title = stringResource(R.string.lab_operation)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Chips wrap manually in two rows so no scrolling is needed.
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixOperation.entries.take(5).forEach { op ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.run(op) },
                                label = { Text(op.label) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MatrixOperation.entries.drop(5).forEach { op ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.run(op) },
                                label = { Text(op.label) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        results = { LabResults(state) },
    )
}

// ---- statistics ------------------------------------------------------------------------

@Composable
fun StatisticsLabPanel(
    modifier: Modifier = Modifier,
    viewModel: StatisticsLabViewModel = axiomViewModel { _, _ -> StatisticsLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(title = stringResource(R.string.lab_statistics_inputs)) {
                AxiomField(
                    value = state.dataset,
                    onValueChange = viewModel::setDataset,
                    label = stringResource(R.string.lab_dataset),
                    hint = "2, 4, 4, 6",
                )
                AxiomField(
                    value = state.weights,
                    onValueChange = viewModel::setWeights,
                    label = stringResource(R.string.lab_weights_optional),
                    hint = "1, 2, 3",
                )
                RunButton(stringResource(R.string.action_calculate)) { viewModel.run() }
            }
        },
        results = { LabResults(state) },
    )
}

// ---- complex ------------------------------------------------------------------------

@Composable
fun ComplexLabPanel(
    modifier: Modifier = Modifier,
    viewModel: ComplexLabViewModel = axiomViewModel { _, _ -> ComplexLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(
                title = stringResource(R.string.lab_complex_inputs),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.lab_polar_input),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Switch(checked = state.polarInput, onCheckedChange = viewModel::setPolarInput)
                    }
                },
            ) {
                Text(
                    text = stringResource(
                        if (state.polarInput) R.string.lab_complex_polar_hint else R.string.lab_complex_rect_hint,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AxiomField(
                        value = state.aRe,
                        onValueChange = { viewModel.set(ComplexField.A_RE, it) },
                        label = if (state.polarInput) "a r" else "a re",
                        modifier = Modifier.weight(1f),
                    )
                    AxiomField(
                        value = state.aIm,
                        onValueChange = { viewModel.set(ComplexField.A_IM, it) },
                        label = if (state.polarInput) "a θ" else "a im",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AxiomField(
                        value = state.bRe,
                        onValueChange = { viewModel.set(ComplexField.B_RE, it) },
                        label = if (state.polarInput) "b r" else "b re",
                        modifier = Modifier.weight(1f),
                    )
                    AxiomField(
                        value = state.bIm,
                        onValueChange = { viewModel.set(ComplexField.B_IM, it) },
                        label = if (state.polarInput) "b θ" else "b im",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SectionCard(title = stringResource(R.string.lab_operation)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComplexOperation.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { op ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.run(op) },
                                    label = { Text(op.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        results = { LabResults(state) },
    )
}

// ---- numeric solver ---------------------------------------------------------------------

@Composable
fun SolverLabPanel(
    modifier: Modifier = Modifier,
    viewModel: SolverLabViewModel = axiomViewModel { _, _ -> SolverLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(title = stringResource(R.string.lab_solver_inputs)) {
                AxiomField(
                    value = state.expression,
                    onValueChange = viewModel::setExpression,
                    label = stringResource(R.string.lab_solver_expression),
                    numeric = false,
                    hint = "x^3 - x - 2",
                )
                Text(
                    text = stringResource(R.string.lab_solver_variable_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    AxiomField(state.lower, viewModel::setLower, stringResource(R.string.lab_lower_bound), Modifier.weight(1f))
                    AxiomField(state.upper, viewModel::setUpper, stringResource(R.string.lab_upper_bound), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AxiomField(state.start, viewModel::setStart, stringResource(R.string.lab_start_value), Modifier.weight(1f))
                    AxiomField(state.tolerance, viewModel::setTolerance, stringResource(R.string.lab_tolerance), Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SolverMethod.entries.forEach { method ->
                        FilterChip(
                            selected = state.method == method,
                            onClick = { viewModel.setMethod(method) },
                            label = { Text(method.label) },
                        )
                    }
                }
                RunButton(stringResource(R.string.action_calculate)) { viewModel.run() }
            }
        },
        results = { LabResults(state) },
    )
}

// ---- bases -------------------------------------------------------------------------------

@Composable
fun BasesLabPanel(
    modifier: Modifier = Modifier,
    viewModel: BasesLabViewModel = axiomViewModel { _, _ -> BasesLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(title = stringResource(R.string.lab_bases_inputs)) {
                AxiomField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    label = stringResource(R.string.lab_value),
                    numeric = false,
                    hint = "255",
                )
                BaseChipsRow(
                    title = stringResource(R.string.lab_from_base),
                    bases = com.shohankhan.axiomcalc.math.solvers.Bases.SUPPORTED,
                    selected = state.fromBase,
                    onSelect = viewModel::setFromBase,
                )
                AxiomField(
                    value = state.operand,
                    onValueChange = viewModel::setOperand,
                    label = stringResource(R.string.lab_second_operand),
                    numeric = false,
                    hint = "16",
                )
                BaseChipsRow(
                    title = stringResource(R.string.lab_arithmetic_base),
                    bases = com.shohankhan.axiomcalc.math.solvers.Bases.SUPPORTED,
                    selected = state.operationBase,
                    onSelect = viewModel::setOperationBase,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    BaseOperation.entries.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { op ->
                                FilterChip(
                                    selected = false,
                                    onClick = { viewModel.run(op) },
                                    label = { Text(op.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
        results = { LabResults(state) },
    )
}

@Composable
private fun BaseChipsRow(
    title: String,
    bases: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        bases.forEach { base ->
            FilterChip(
                selected = selected == base,
                onClick = { onSelect(base) },
                label = { Text(base.toString()) },
            )
        }
    }
}

// ---- engineering notation --------------------------------------------------------------------

@Composable
fun FormattingLabPanel(
    modifier: Modifier = Modifier,
    viewModel: FormattingLabViewModel = axiomViewModel { _, _ -> FormattingLabViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LabScaffold(
        modifier = modifier,
        inputs = {
            SectionCard(title = stringResource(R.string.lab_formatting_inputs)) {
                AxiomField(state.value, viewModel::setValue, stringResource(R.string.lab_value), hint = "1234567")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    AxiomField(state.digits, viewModel::setDigits, stringResource(R.string.lab_significant_figures), Modifier.weight(1f))
                    AxiomField(state.decimals, viewModel::setDecimals, stringResource(R.string.lab_decimal_places), Modifier.weight(1f))
                }
                Column(Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.settings_number_format), style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberNotation.entries.forEach { notation ->
                            FilterChip(
                                selected = state.notation == notation,
                                onClick = { viewModel.setNotation(notation) },
                                label = { Text(notation.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.lab_rounding_mode),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundingPolicy.entries.forEach { policy ->
                            FilterChip(
                                selected = state.rounding == policy,
                                onClick = { viewModel.setRounding(policy) },
                                label = { Text(policy.label) },
                            )
                        }
                    }
                }
                RunButton(stringResource(R.string.action_calculate)) { viewModel.run() }
            }
        },
        results = { LabResults(state) },
    )
}
