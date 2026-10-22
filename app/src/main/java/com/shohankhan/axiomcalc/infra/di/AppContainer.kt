package com.shohankhan.axiomcalc.infra.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.shohankhan.axiomcalc.AxiomCalcApplication
import com.shohankhan.axiomcalc.data.local.AxiomDatabase
import com.shohankhan.axiomcalc.data.preferences.DataStoreSettingsRepository
import com.shohankhan.axiomcalc.data.repository.RoomFavoritesRepository
import com.shohankhan.axiomcalc.data.repository.RoomHistoryRepository
import com.shohankhan.axiomcalc.data.repository.RoomSavedCalculationRepository
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.repository.FormulaFavoritesRepository
import com.shohankhan.axiomcalc.domain.repository.HistoryRepository
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import com.shohankhan.axiomcalc.domain.repository.SavedCalculationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency container.
 *
 * A hand-written container keeps the dependency graph explicit and avoids
 * adding a code-generation dependency that is not needed for an app of this
 * size. All singletons are created lazily on first use.
 */
class AppContainer(private val context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsRepository by lazy { DataStoreSettingsRepository(context) }

    private val database: AxiomDatabase by lazy {
        Room.databaseBuilder(context, AxiomDatabase::class.java, AxiomDatabase.NAME).build()
    }

    val historyRepository: HistoryRepository by lazy {
        RoomHistoryRepository(database.historyDao(), settings)
    }

    val savedRepository: SavedCalculationRepository by lazy {
        RoomSavedCalculationRepository(database.savedDao())
    }

    val favoritesRepository: FormulaFavoritesRepository by lazy {
        RoomFavoritesRepository(database.favoritesDao())
    }

    /**
     * Applies retention at startup. This is the guaranteed enforcement path;
     * the WorkManager job is a best-effort periodic cleanup on top of it.
     * Failures are reported through [onFailure] instead of being swallowed,
     * because silently skipping retention would let expired data persist.
     */
    fun enforceRetentionAtStartup(onFailure: (Throwable) -> Unit = {}) {
        applicationScope.launch {
            try {
                val policy = settings.current().retention
                historyRepository.deleteExpired(System.currentTimeMillis(), policy)
            } catch (e: Throwable) {
                onFailure(e)
            }
        }
    }

    /** Best-effort periodic cleanup, enqueued only if WorkManager is available. */
    fun scheduleRetentionCleanup(): Boolean = try {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RETENTION_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            androidx.work.PeriodicWorkRequestBuilder<RetentionWorker>(1, java.util.concurrent.TimeUnit.DAYS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build(),
        )
        true
    } catch (e: Throwable) {
        false
    }

    companion object {
        const val RETENTION_WORK_NAME = "axiom.retention.cleanup"
    }
}

/**
 * Periodic retention cleanup.
 *
 * The specification is explicit that WorkManager may not run on a fresh install
 * or when background execution is restricted, so this worker is best-effort and
 * its failure is never fatal: read-time filtering and startup enforcement are
 * the guaranteed paths. Results are returned through the standard worker result
 * so they are visible in WorkManager diagnostics.
 */
class RetentionWorker(
    context: Context,
    params: androidx.work.WorkerParameters,
) : androidx.work.CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AxiomCalcApplication)?.container
            ?: return Result.failure()
        return try {
            val policy: RetentionPolicy = container.settings.current().retention
            val deleted = container.historyRepository.deleteExpired(System.currentTimeMillis(), policy)
            Result.success(
                androidx.work.workDataOf(KEY_DELETED to deleted, KEY_POLICY to policy.name),
            )
        } catch (e: Exception) {
            Result.failure(androidx.work.workDataOf(KEY_ERROR to e.message.orEmpty()))
        }
    }

    companion object {
        const val KEY_DELETED = "deleted"
        const val KEY_POLICY = "policy"
        const val KEY_ERROR = "error"
    }
}
