package com.shohankhan.axiomcalc.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.core.ui.ResultRow
import com.shohankhan.axiomcalc.core.ui.SectionCard
import com.shohankhan.axiomcalc.domain.library.FormulaLibrary
import com.shohankhan.axiomcalc.feature.tools.ToolSpec
import com.shohankhan.axiomcalc.infra.di.axiomViewModel

/**
 * Formula detail (secondary destination).
 *
 * Shows the reference entry exactly as authored in the offline library — the
 * expression, every variable with its meaning, the conditions and restrictions,
 * and units when the formula has them. When an implementing calculator exists,
 * a button opens it; reference-only entries say so instead of offering a control
 * that leads nowhere.
 */
@Composable
fun LibraryDetailScreen(
    formulaId: String,
    onOpenCalculator: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = rememberLibraryViewModel(),
) {
    val formula = remember(formulaId) { FormulaLibrary.byId(formulaId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (formula == null) {
        EmptyState(
            iconRes = R.drawable.ic_nav_library,
            title = stringResource(R.string.tool_not_found_title),
            body = stringResource(R.string.library_detail_missing_body),
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val favorite = formula.id in state.favorites
    val implementation = remember(formula.formId) { formula.formId?.let { ToolSpec.resolve(it) } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AxiomDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formula.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = formula.category.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val favoriteLabel = stringResource(
                if (favorite) R.string.action_favorite_remove else R.string.action_favorite_add,
            )
            IconButton(
                onClick = { viewModel.toggleFavorite(formula.id, favorite) },
                modifier = Modifier
                    .size(AxiomDimens.MinTouchTarget)
                    .semantics { contentDescription = favoriteLabel },
            ) {
                Icon(
                    painter = painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_border),
                    contentDescription = null,
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        SectionCard(title = stringResource(R.string.library_expression)) {
            Text(
                text = formula.expression,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }

        if (formula.variables.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.library_variables)) {
                formula.variables.forEach { (symbol, meaning) ->
                    ResultRow(label = symbol, value = meaning)
                }
            }
        }

        SectionCard(title = stringResource(R.string.library_conditions)) {
            Text(formula.conditions, style = MaterialTheme.typography.bodyMedium)
        }

        if (formula.units != null) {
            SectionCard(title = stringResource(R.string.library_units)) {
                Text(formula.units!!, style = MaterialTheme.typography.bodyMedium)
            }
        }

        SectionCard(title = stringResource(R.string.library_open_calculator)) {
            if (implementation != null) {
                Text(
                    text = stringResource(R.string.library_implementation_available, implementation.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { formula.formId?.let(onOpenCalculator) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_open_calculator))
                }
            } else {
                Text(
                    text = stringResource(R.string.library_reference_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
