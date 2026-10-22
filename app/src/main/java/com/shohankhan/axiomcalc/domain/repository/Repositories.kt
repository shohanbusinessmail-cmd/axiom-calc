package com.shohankhan.axiomcalc.domain.repository

import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.SavedCalculation
import kotlinx.coroutines.flow.Flow

/**
 * Persistence contracts. The UI and ViewModels depend only on these interfaces;
 * the Room/DataStore implementations live in `data/`.
 *
 * Retention rules encoded by the implementations (and tested):
 *  * expired history rows are filtered from every read, even before a cleanup
 *    pass has run;
 *  * [deleteExpired] never touches saved calculations or favorites;
 *  * [RetentionPolicy.NEVER] disables expiration entirely.
 */
interface HistoryRepository {
    fun observeAll(): Flow<List<HistoryEntry>>
    fun observeSearch(query: String): Flow<List<HistoryEntry>>
    suspend fun add(entry: HistoryEntry): Long
    suspend fun delete(ids: List<Long>)
    suspend fun deleteAll()
    suspend fun deleteExpired(nowMillis: Long, policy: RetentionPolicy): Int
    suspend fun count(): Int
}

interface SavedCalculationRepository {
    fun observeAll(): Flow<List<SavedCalculation>>
    suspend fun add(item: SavedCalculation): Long
    suspend fun delete(ids: List<Long>)
}

interface FormulaFavoritesRepository {
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeRecentIds(): Flow<List<String>>
    suspend fun setFavorite(formulaId: String, favorite: Boolean)
    suspend fun recordAccess(formulaId: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
