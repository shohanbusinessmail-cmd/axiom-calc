package com.shohankhan.axiomcalc.feature.saved

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shohankhan.axiomcalc.R
import com.shohankhan.axiomcalc.core.design.AxiomDimens
import com.shohankhan.axiomcalc.core.ui.EmptyState
import com.shohankhan.axiomcalc.domain.model.SavedCalculation
import com.shohankhan.axiomcalc.domain.repository.SavedCalculationRepository
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Saved calculations.
 *
 * This is a deliberately separate list from history: entries here are named by
 * the user, kept permanently, and never touched by the retention policy. The
 * screen states that explicitly so the distinction is not implicit.
 */
class SavedViewModel(private val repository: SavedCalculationRepository) : ViewModel() {

    val items: StateFlow<List<SavedCalculation>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = _selected

    fun toggleSelection(id: Long) {
        _selected.value = if (id in _selected.value) _selected.value - id else _selected.value + id
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun deleteSelected(onDone: () -> Unit) {
        val ids = _selected.value.toList()
        viewModelScope.launch {
            repository.delete(ids)
            _selected.value = emptySet()
            onDone()
        }
    }
}

@Composable
fun SavedScreen(
    onReuse: (SavedCalculation) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = axiomViewModel { container, _ -> SavedViewModel(container.savedRepository) },
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
            Text(
                text = stringResource(R.string.saved_permanent_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.history_selected_count, selected.size),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.action_clear_selection))
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            }
            if (items.isEmpty()) {
                EmptyState(
                    iconRes = R.drawable.ic_bookmark,
                    title = stringResource(R.string.saved_empty_title),
                    body = stringResource(R.string.saved_empty_body),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
                ) {
                    items(items, key = { it.id }) { item ->
                        Card(
                            onClick = { onReuse(item) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(AxiomDimens.Radius),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.id in selected) {
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
                                    checked = item.id in selected,
                                    onCheckedChange = { viewModel.toggleSelection(item.id) },
                                    modifier = Modifier
                                        .size(AxiomDimens.MinTouchTarget)
                                        .semantics { contentDescription = "Select ${item.name}" },
                                )
                                Spacer(Modifier.width(4.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.expression,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = item.resultDisplay,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.saved_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected { confirmDelete = false }
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
