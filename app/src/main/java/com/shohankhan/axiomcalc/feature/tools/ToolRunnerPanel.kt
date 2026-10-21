package com.shohankhan.axiomcalc.feature.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.Axiom
import com.shohankhan.axiomcalc.core.ui.AxiomField
import com.shohankhan.axiomcalc.core.ui.ResultRow
import com.shohankhan.axiomcalc.core.ui.SectionCard
import com.shohankhan.axiomcalc.core.ui.userMessage

/**
 * The panel every form, solver tool and several Math Lab modules share.
 *
 * Layout order is fixed: what the tool computes, its inputs, then its results
 * and shown working. Results appear only once every declared input has a value,
 * so the screen never shows a partially-computed or stale answer.
 */
@Composable
fun ToolRunnerPanel(
    viewModel: ToolRunnerViewModel,
    modifier: Modifier = Modifier,
    onCopyResult: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spec = viewModel.spec

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = spec.title) {
            Text(
                text = spec.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (spec.formulaText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = spec.formulaText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        SectionCard(title = stringResource(R.string.formulas_inputs_title)) {
            spec.inputs.forEach { input ->
                AxiomField(
                    value = state.values[input.id].orEmpty(),
                    onValueChange = { viewModel.setValue(input.id, it) },
                    label = if (input.unit == null) input.label else "${input.label} (${input.unit})",
                    errorText = state.fieldErrors[input.id],
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                if (input.constraint != null) {
                    Text(
                        text = input.constraint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.clear() }) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }

        when {
            state.error != null -> SectionCard(title = stringResource(R.string.formulas_result_title)) {
                Text(
                    text = state.error!!.userMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Axiom.extended.errorText,
                )
            }

            state.result != null -> {
                SectionCard(
                    title = stringResource(R.string.formulas_result_title),
                    trailing = onCopyResult?.let { copy ->
                        {
                            TextButton(onClick = {
                                copy(
                                    state.result!!.lines.joinToString("\n") { "${it.label}: ${it.value}" },
                                )
                            }) { Text(stringResource(R.string.action_copy)) }
                        }
                    },
                ) {
                    state.result!!.lines.forEach { line ->
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

                if (state.result!!.substitution.isNotBlank()) {
                    SectionCard(title = stringResource(R.string.formulas_substitution)) {
                        Text(
                            text = state.result!!.substitution,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                if (state.result!!.steps.isNotEmpty()) {
                    SectionCard(
                        title = stringResource(R.string.quick_solve_steps_title),
                        trailing = {
                            TextButton(onClick = { viewModel.toggleSteps() }) {
                                Text(
                                    stringResource(
                                        if (state.showSteps) R.string.action_hide_steps else R.string.action_show_steps,
                                    ),
                                )
                            }
                        },
                    ) {
                        if (state.showSteps) {
                            state.result!!.steps.forEachIndexed { index, step ->
                                Text(
                                    text = "${index + 1}. $step",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        } else {
                            Text(
                                text = stringResource(R.string.quick_solve_steps_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            state.hasComputed -> Unit // error card above covers the failure case

            else -> SectionCard(title = stringResource(R.string.formulas_result_title)) {
                Text(
                    text = stringResource(R.string.formulas_awaiting_input),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionCard(title = stringResource(R.string.library_conditions)) {
            Text(
                text = spec.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
