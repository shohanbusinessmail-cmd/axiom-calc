package com.shohankhan.axiomcalc.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.repository.HistoryRepository
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** History filters the user can select. */
enum class HistoryFilter { ALL, TODAY, WEEK, MONTH }

/** One entry plus the selection flag, and the origin filter state. */
data class HistoryUiState(
    val groups: List<HistoryGroup> = emptyList(),
    val query: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val origins: Set<CalcOrigin> = CalcOrigin.entries.toSet(),
    val selectedIds: Set<Long> = emptySet(),
    val totalRows: Int = 0,
    val retention: RetentionPolicy = RetentionPolicy.DEFAULT,
    /** Non-fatal notice, e.g. a retention cleanup that could not run. */
    val notice: String? = null,
)

data class HistoryGroup(val label: String, val entries: List<HistoryEntry>)

/** Snapshot of the four user-controlled history filters, for `combine`. */
private data class HistoryFilters(
    val query: String,
    val filter: HistoryFilter,
    val origins: Set<CalcOrigin>,
    val selectedIds: Set<Long>,
)

/**
 * History ViewModel.
 *
 * The list is derived from the repository Flow, so it reflects the database
 * exactly. Filtering by date, search text and origin happens here on the
 * already-retention-filtered rows, and grouping is computed with the device's
 * calendar so "Today"/"Yesterday" match what the user expects.
 */
class HistoryViewModel(
    private val repository: HistoryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val origins = MutableStateFlow(CalcOrigin.entries.toSet())
    private val selected = MutableStateFlow<Set<Long>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows = query.flatMapLatest { q ->
        if (q.isBlank()) repository.observeAll() else repository.observeSearch(q)
    }

    /**
     * The five independent inputs of this screen combined in two steps.
     *
     * `kotlinx.coroutines.flow.combine` provides typed overloads for two to
     * five flows and only an untyped `Array` overload beyond that, so the four
     * user-controlled filters are folded into one value first. The result is a
     * strongly-typed six-way combination instead of an `Array<Any?>`.
     */
    val state: StateFlow<HistoryUiState> = combine(
        rows,
        combine(query, filter, origins, selected) { q, f, org, sel -> HistoryFilters(q, f, org, sel) },
        settingsRepository.settings,
    ) { entries, filters, settings ->
        val window = filters.filter.windowStart(System.currentTimeMillis())
        val visible = entries
            .filter { it.origin in filters.origins }
            .filter { window == null || it.timestampMillis >= window }
        HistoryUiState(
            groups = groupByDay(visible),
            query = filters.query,
            filter = filters.filter,
            origins = filters.origins,
            selectedIds = filters.selectedIds,
            totalRows = entries.size,
            retention = settings.retention,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: HistoryFilter) { filter.value = value }

    fun toggleOrigin(origin: CalcOrigin) {
        val current = origins.value
        val next = if (origin in current) current - origin else current + origin
        // An empty filter would show nothing at all, which reads as data loss.
        origins.value = if (next.isEmpty()) CalcOrigin.entries.toSet() else next
    }

    fun toggleSelection(id: Long) {
        selected.value = if (id in selected.value) selected.value - id else selected.value + id
    }

    fun selectAll(ids: List<Long>) {
        selected.value = ids.toSet()
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun deleteSelected(onDone: (Int) -> Unit) {
        val ids = selected.value.toList()
        viewModelScope.launch {
            repository.delete(ids)
            selected.value = emptySet()
            onDone(ids.size)
        }
    }

    fun deleteEntry(id: Long, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            repository.delete(listOf(id))
            onDone(1)
        }
    }

    fun deleteAll(onDone: (Int) -> Unit) {
        val count = state.value.groups.sumOf { it.entries.size }
        viewModelScope.launch {
            repository.deleteAll()
            selected.value = emptySet()
            onDone(count)
        }
    }

    /**
     * Runs retention now and reports how many rows were removed. A failure is
     * surfaced as a notice rather than swallowed, because silently failing to
     * clean up would leave data the user asked to expire.
     */
    fun runRetentionNow(onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val outcome = runCatching {
                repository.deleteExpired(System.currentTimeMillis(), state.value.retention)
            }
            onResult(outcome)
        }
    }

    companion object {
        /** Groups entries into Today / Yesterday / explicit dates. */
        fun groupByDay(entries: List<HistoryEntry>, nowMillis: Long = System.currentTimeMillis()): List<HistoryGroup> {
            val calendar = Calendar.getInstance(TimeZone.getDefault())
            val todayStart = startOfDay(calendar, nowMillis)
            val yesterdayStart = todayStart - 86_400_000L
            val dayFormat = java.text.SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())
            return entries
                .sortedByDescending { it.timestampMillis }
                .groupBy { entry ->
                    when {
                        entry.timestampMillis >= todayStart -> 0L
                        entry.timestampMillis >= yesterdayStart -> 1L
                        else -> startOfDay(calendar, entry.timestampMillis)
                    }
                }
                .map { (key, items) ->
                    val label = when (key) {
                        0L -> "Today"
                        1L -> "Yesterday"
                        else -> dayFormat.format(java.util.Date(key))
                    }
                    HistoryGroup(label, items)
                }
                .sortedByDescending { group -> group.entries.maxOf { it.timestampMillis } }
        }

        private fun startOfDay(calendar: Calendar, millis: Long): Long {
            calendar.timeInMillis = millis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}

/** Start of the selected time window, or null for "all". */
private fun HistoryFilter.windowStart(nowMillis: Long): Long? = when (this) {
    HistoryFilter.ALL -> null
    HistoryFilter.TODAY -> {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }

    HistoryFilter.WEEK -> nowMillis - 7 * RetentionPolicy.MILLIS_PER_DAY
    HistoryFilter.MONTH -> nowMillis - 30 * RetentionPolicy.MILLIS_PER_DAY
}
