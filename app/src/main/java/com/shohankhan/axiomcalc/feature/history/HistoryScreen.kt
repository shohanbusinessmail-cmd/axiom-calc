package com.shohankhan.axiomcalc.feature.history

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.infra.di.axiomViewModel

@Composable
fun rememberHistoryViewModel(): HistoryViewModel = axiomViewModel { container, _ ->
    HistoryViewModel(container.historyRepository, container.settings)
}

/**
 * History screen.
 *
 * Date grouping, text search, time-window and origin filters, and multi-select
 * deletion are all driven by [HistoryViewModel]. Selection uses a checkbox plus
 * long-press, so the interaction is discoverable with and without TalkBack.
 */
@Composable
fun HistoryScreen(
    onReuse: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = rememberHistoryViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    val selecting = state.selectedIds.isNotEmpty()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.history_search_hint)) },
                singleLine = true,
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        TextButton(onClick = { viewModel.setQuery("") }) {
                            Text(stringResource(R.string.action_clear_search))
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(HistoryFilter.entries.toList()) { entry ->
                    FilterChip(
                        selected = state.filter == entry,
                        onClick = { viewModel.setFilter(entry) },
                        label = {
                            Text(
                                stringResource(
                                    when (entry) {
                                        HistoryFilter.ALL -> R.string.history_filter_all
                                        HistoryFilter.TODAY -> R.string.history_filter_today
                                        HistoryFilter.WEEK -> R.string.history_filter_week
                                        HistoryFilter.MONTH -> R.string.history_filter_month
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CalcOrigin.entries.toList()) { origin ->
                    FilterChip(
                        selected = origin in state.origins,
                        onClick = { viewModel.toggleOrigin(origin) },
                        label = { Text(origin.label) },
                        modifier = Modifier.semantics {
                            contentDescription = "Filter by origin ${origin.label}"
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selecting) {
                        stringResource(R.string.history_selected_count, state.selectedIds.size)
                    } else {
                        stringResource(R.string.history_retention_note, state.retention.label)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (selecting) {
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.action_clear_selection))
                    }
                    TextButton(onClick = { confirmDeleteSelected = true }) {
                        Text(stringResource(R.string.action_delete))
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.selectAll(state.groups.flatMap { it.entries }.map { it.id })
                        },
                        enabled = state.groups.isNotEmpty(),
                    ) { Text(stringResource(R.string.action_select_all)) }
                    TextButton(
                        onClick = { confirmDeleteAll = true },
                        enabled = state.totalRows > 0,
                    ) { Text(stringResource(R.string.action_delete_all)) }
                }
            }

            if (state.groups.isEmpty()) {
                EmptyState(
                    iconRes = R.drawable.ic_history,
                    title = stringResource(R.string.history_empty_title),
                    body = stringResource(R.string.history_empty_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
                ) {
                    state.groups.forEach { group ->
                        item(key = "header-${group.label}") {
                            Text(
                                text = group.label,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                            )
                        }
                        items(group.entries, key = { it.id }) { entry ->
                            HistoryRow(
                                entry = entry,
                                selected = entry.id in state.selectedIds,
                                onToggleSelect = { viewModel.toggleSelection(entry.id) },
                                onReuse = { onReuse(entry) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text(stringResource(R.string.history_delete_many_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected { count ->
                        confirmDeleteSelected = false
                    }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.history_delete_all_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll { confirmDeleteAll = false }
                }) { Text(stringResource(R.string.action_delete_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun HistoryRow(
    entry: HistoryEntry,
    selected: Boolean,
    onToggleSelect: () -> Unit,
    onReuse: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onReuse,
                onLongClick = onToggleSelect,
            ),
        shape = RoundedCornerShape(AxiomDimens.Radius),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier
                    .size(AxiomDimens.MinTouchTarget)
                    .semantics { contentDescription = "Select this calculation" },
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.expression,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.resultDisplay,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.origin.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTime(entry.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
