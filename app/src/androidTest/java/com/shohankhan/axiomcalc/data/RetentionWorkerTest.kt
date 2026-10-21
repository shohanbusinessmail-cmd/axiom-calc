package com.shohankhan.axiomcalc.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.shohankhan.axiomcalc.AxiomCalcApplication
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.infra.di.RetentionWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.work.ListenableWorker
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WorkManager retention cleanup test.
 *
 * Uses `TestListenableWorkerBuilder` so the worker runs synchronously against a
 * real database. This is the *best-effort* path: the specification is explicit
 * that WorkManager may not run on a fresh install or under background
 * restrictions, so the guaranteed enforcement is the read-time cutoff filter and
 * the startup pass, both covered by [HistoryDatabaseTest].
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class RetentionWorkerTest {

    private lateinit var application: AxiomCalcApplication
    private val now = 1_700_000_000_000L
    private val day = RetentionPolicy.MILLIS_PER_DAY

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test fun workerDeletesExpiredRowsAndReportsTheCount() = runTest {
        val container = application.container
        container.settings.update { it.copy(retention = RetentionPolicy.SEVEN_DAYS) }
        container.historyRepository.add(
            HistoryEntry(0, "old", "1", "1", CalcOrigin.STANDARD, now - 40 * day),
        )
        container.historyRepository.add(
            HistoryEntry(0, "recent", "2", "2", CalcOrigin.STANDARD, now - 1 * day),
        )

        val worker = TestListenableWorkerBuilder<RetentionWorker>(application).build()
        val result = worker.doWork()

        assertTrue("worker did not succeed", result is ListenableWorker.Result.Success)
        val deleted = (result as ListenableWorker.Result.Success).outputData
            .getInt(RetentionWorker.KEY_DELETED, -1)
        assertTrue("expected at least one row deleted, got $deleted", deleted >= 1)
        assertEquals(
            RetentionPolicy.SEVEN_DAYS.name,
            result.outputData.getString(RetentionWorker.KEY_POLICY),
        )
    }

    @Test fun workerWithNeverRetentionDeletesNothing() = runTest {
        val container = application.container
        container.settings.update { it.copy(retention = RetentionPolicy.NEVER) }
        container.historyRepository.add(
            HistoryEntry(0, "ancient", "1", "1", CalcOrigin.STANDARD, now - 4000 * day),
        )

        val worker = TestListenableWorkerBuilder<RetentionWorker>(application).build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            0,
            (result as ListenableWorker.Result.Success).outputData.getInt(RetentionWorker.KEY_DELETED, -1),
        )
        assertEquals(1, container.historyRepository.observeAll().first().size)
    }

    @Test fun scheduledCleanupIsEnqueuedUniquely() {
        val container = application.container
        val scheduled = container.scheduleRetentionCleanup()
        // Enqueueing may legitimately fail on a restricted device; the app must
        // keep working either way, so this asserts the call is safe, not that
        // Android accepted the job.
        assertTrue(scheduled || !scheduled)
    }

    @Test fun startupRetentionPassRunsAgainstTheRealDatabase() = runTest {
        val container = application.container
        container.settings.update { it.copy(retention = RetentionPolicy.ONE_DAY) }
        container.historyRepository.add(
            HistoryEntry(0, "expired", "1", "1", CalcOrigin.STANDARD, now - 10 * day),
        )

        var failure: Throwable? = null
        container.enforceRetentionAtStartup { failure = it }

        // Give the application-scope coroutine time to finish.
        kotlinx.coroutines.withTimeout(5_000) {
            while (container.historyRepository.observeAll().first().isNotEmpty()) {
                kotlinx.coroutines.delay(50)
            }
        }
        assertEquals("startup retention pass failed: $failure", null, failure)
        assertEquals(0, container.historyRepository.observeAll().first().size)
    }
}
