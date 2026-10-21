package com.shohankhan.axiomcalc.data.repository

import com.shohankhan.axiomcalc.data.local.FavoriteEntity
import com.shohankhan.axiomcalc.data.local.FavoritesDao
import com.shohankhan.axiomcalc.data.local.HistoryDao
import com.shohankhan.axiomcalc.data.local.HistoryEntity
import com.shohankhan.axiomcalc.data.local.RecentFormulaEntity
import com.shohankhan.axiomcalc.data.local.SavedCalculationDao
import com.shohankhan.axiomcalc.data.local.SavedCalculationEntity
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.SavedCalculation
import com.shohankhan.axiomcalc.domain.repository.FormulaFavoritesRepository
import com.shohankhan.axiomcalc.domain.repository.HistoryRepository
import com.shohankhan.axiomcalc.domain.repository.SavedCalculationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed history.
 *
 * Retention is enforced in two independent ways, as the specification requires:
 *  * every read filters expired rows by cutoff, so the screen can never show an
 *    expired entry even if no cleanup pass has run yet;
 *  * [deleteExpired] removes them physically (called at startup, when history
 *    opens, and best-effort from WorkManager).
 * Saved calculations live in a different table and are never touched here.
 */
class RoomHistoryRepository(
    private val dao: HistoryDao,
    private val settings: SettingsRepository,
) : HistoryRepository {

    override fun observeAll(): Flow<List<HistoryEntry>> =
        dao.observeAll().map { rows -> rows.filterNotExpired().map { it.toDomain() } }

    override fun observeSearch(query: String): Flow<List<HistoryEntry>> =
        dao.observeSearch(query).map { rows -> rows.filterNotExpired().map { it.toDomain() } }

    override suspend fun add(entry: HistoryEntry): Long =
        dao.insert(
            HistoryEntity(
                expression = entry.expression,
                resultValue = entry.resultValue,
                resultDisplay = entry.resultDisplay,
                origin = entry.origin,
                timestampMillis = entry.timestampMillis,
            ),
        )

    override suspend fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun deleteExpired(nowMillis: Long, policy: RetentionPolicy): Int {
        val cutoff = policy.cutoffMillis(nowMillis) ?: return 0
        return dao.deleteOlderThan(cutoff)
    }

    override suspend fun count(): Int = dao.count()

    private suspend fun List<HistoryEntity>.filterNotExpired(): List<HistoryEntity> {
        val cutoff = settings.current().retention.cutoffMillis(System.currentTimeMillis())
            ?: return this
        return filter { it.timestampMillis >= cutoff }
    }
}

private fun HistoryEntity.toDomain(): HistoryEntry =
    HistoryEntry(id, expression, resultValue, resultDisplay, origin, timestampMillis)

class RoomSavedCalculationRepository(private val dao: SavedCalculationDao) : SavedCalculationRepository {

    override fun observeAll(): Flow<List<SavedCalculation>> =
        dao.observeAll().map { rows ->
            rows.map {
                SavedCalculation(
                    it.id, it.name, it.expression, it.resultValue,
                    it.resultDisplay, it.origin, it.savedAtMillis,
                )
            }
        }

    override suspend fun add(item: SavedCalculation): Long =
        dao.insert(
            SavedCalculationEntity(
                name = item.name,
                expression = item.expression,
                resultValue = item.resultValue,
                resultDisplay = item.resultDisplay,
                origin = item.origin,
                savedAtMillis = item.savedAtMillis,
            ),
        )

    override suspend fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteByIds(ids)
    }
}

class RoomFavoritesRepository(private val dao: FavoritesDao) : FormulaFavoritesRepository {

    override fun observeFavoriteIds(): Flow<Set<String>> = dao.observeIds().map { it.toSet() }

    override fun observeRecentIds(): Flow<List<String>> = dao.observeRecent()

    override suspend fun setFavorite(formulaId: String, favorite: Boolean) {
        if (favorite) dao.add(FavoriteEntity(formulaId)) else dao.remove(FavoriteEntity(formulaId))
    }

    override suspend fun recordAccess(formulaId: String) {
        dao.dropRecent(formulaId)
        dao.touchRecent(RecentFormulaEntity(formulaId, System.currentTimeMillis()))
        dao.trimRecent()
    }
}
