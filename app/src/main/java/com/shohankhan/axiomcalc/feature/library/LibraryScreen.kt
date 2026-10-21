package com.shohankhan.axiomcalc.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
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
import com.shohankhan.axiomcalc.domain.library.FormulaLibrary
import com.shohankhan.axiomcalc.domain.library.LibraryCategory
import com.shohankhan.axiomcalc.domain.library.LibraryFormula
import com.shohankhan.axiomcalc.domain.repository.FormulaFavoritesRepository
import com.shohankhan.axiomcalc.infra.di.axiomViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the Formula Library list. */
data class LibraryUiState(
    val results: List<LibraryFormula> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val recents: List<String> = emptyList(),
    val query: String = "",
    val category: LibraryCategory? = null,
    val filter: LibraryFilter = LibraryFilter.ALL,
)

enum class LibraryFilter { ALL, FAVORITES, RECENT }

/**
 * Formula Library ViewModel.
 *
 * The list is a pure function of (query, category, filter) combined with the
 * persisted favorites and recents from Room — no in-memory shadow state, so the
 * screen cannot disagree with the database.
 */
class LibraryViewModel(
    private val favoritesRepository: FormulaFavoritesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<LibraryCategory?>(null)
    private val filter = MutableStateFlow(LibraryFilter.ALL)

    val state: StateFlow<LibraryUiState> = combine(
        query,
        category,
        filter,
        favoritesRepository.observeFavoriteIds(),
        favoritesRepository.observeRecentIds(),
    ) { q, cat, f, favs, recents ->
        val matches = FormulaLibrary.search(q, cat)
        val filtered = when (f) {
            LibraryFilter.ALL -> matches
            LibraryFilter.FAVORITES -> matches.filter { it.id in favs }
            LibraryFilter.RECENT -> recents.mapNotNull { id -> matches.firstOrNull { it.id == id } }
        }
        LibraryUiState(filtered, favs, recents, q, cat, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setCategory(value: LibraryCategory?) { category.value = value }
    fun setFilter(value: LibraryFilter) { filter.value = value }

    fun toggleFavorite(formulaId: String, currentlyFavorite: Boolean) {
        viewModelScope.launch { favoritesRepository.setFavorite(formulaId, !currentlyFavorite) }
    }

    /** Records a view; called when a detail screen opens. */
    fun recordAccess(formulaId: String) {
        viewModelScope.launch { favoritesRepository.recordAccess(formulaId) }
    }
}

@Composable
fun rememberLibraryViewModel(): LibraryViewModel =
    axiomViewModel { container, _ -> LibraryViewModel(container.favoritesRepository) }

/**
 * Formula Library (primary destination 5).
 *
 * Searchable, filterable by category, with Favorites and Recents sections that
 * come from Room. Every entry can open the implementing calculator when one
 * exists; entries that are reference-only say so instead of linking nowhere.
 */
@Composable
fun LibraryScreen(
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = rememberLibraryViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = AxiomDimens.ScreenPadding)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text(stringResource(R.string.library_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.filter == LibraryFilter.ALL,
                    onClick = { viewModel.setFilter(LibraryFilter.ALL) },
                    label = { Text(stringResource(R.string.library_all)) },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == LibraryFilter.FAVORITES,
                    onClick = { viewModel.setFilter(LibraryFilter.FAVORITES) },
                    label = { Text(stringResource(R.string.library_favorites)) },
                )
            }
            item {
                FilterChip(
                    selected = state.filter == LibraryFilter.RECENT,
                    onClick = { viewModel.setFilter(LibraryFilter.RECENT) },
                    label = { Text(stringResource(R.string.library_recent)) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.category == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text(stringResource(R.string.formulas_category_all)) },
                )
            }
            items(LibraryCategory.entries.toList()) { entry ->
                FilterChip(
                    selected = state.category == entry,
                    onClick = { viewModel.setCategory(entry) },
                    label = { Text(entry.title) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (state.results.isEmpty()) {
            EmptyState(
                iconRes = R.drawable.ic_nav_library,
                title = stringResource(
                    when (state.filter) {
                        LibraryFilter.FAVORITES -> R.string.library_favorites_empty
                        LibraryFilter.RECENT -> R.string.library_recent_empty
                        LibraryFilter.ALL -> R.string.library_empty
                    },
                ),
                body = stringResource(R.string.library_empty_body),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(AxiomDimens.ListGap),
            ) {
                items(state.results, key = { it.id }) { formula ->
                    LibraryCard(
                        formula = formula,
                        favorite = formula.id in state.favorites,
                        onToggleFavorite = {
                            viewModel.toggleFavorite(formula.id, formula.id in state.favorites)
                        },
                        onClick = {
                            viewModel.recordAccess(formula.id)
                            onOpenDetail(formula.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCard(
    formula: LibraryFormula,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AxiomDimens.Radius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formula.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formula.expression,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formula.category.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(AxiomDimens.MinTouchTarget)
                    .semantics {
                        contentDescription = if (favorite) {
                            "Remove ${formula.title} from favorites"
                        } else {
                            "Add ${formula.title} to favorites"
                        }
                    },
            ) {
                Icon(
                    painter = painterResource(
                        if (favorite) R.drawable.ic_star else R.drawable.ic_star_border,
                    ),
                    contentDescription = null,
                    tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
