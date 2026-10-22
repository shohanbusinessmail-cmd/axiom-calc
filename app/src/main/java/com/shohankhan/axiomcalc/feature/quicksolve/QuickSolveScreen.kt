package com.shohankhan.axiomcalc.feature.quicksolve

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.domain.solver.QuickSolve

/**
 * Quick Solve (primary destination 4).
 *
 * Lists the 26 named solver tools grouped by their declared group, with a search
 * box. Selecting a tool opens the shared runner, which shows the relationship,
 * the substitution and the restrictions for that tool — the same strings the
 * unit tests assert on.
 */
@Composable
fun QuickSolveScreen(
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }

    val tools = if (query.isBlank()) {
        QuickSolve.all
    } else {
        val q = query.trim().lowercase()
        QuickSolve.all.filter {
            it.title.lowercase().contains(q) ||
                it.group.lowercase().contains(q) ||
                it.summary.lowercase().contains(q)
        }
    }
    val grouped: List<Pair<String, List<QuickSolve.QuickTool>>> = tools
        .groupBy { it.group }
        .toSortedMap()
        .toList()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.quick_solve_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AxiomDimens.ListGap))

        if (tools.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_nav_quick_solve,
                title = stringResource(R.string.quick_solve_empty),
                body = stringResource(R.string.quick_solve_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
            ) {
                grouped.forEach { (group, groupTools) ->
                    item(key = "group-$group") {
                        Text(
                            text = group,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(groupTools, key = { it.id }) { tool ->
                        Card(
                            onClick = { onOpenTool(tool.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(AxiomDimens.Radius),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Row(
                                Modifier.padding(AxiomDimens.CardPadding),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = tool.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = tool.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = "${tool.inputs.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
