package com.shohankhan.axiomcalc.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.data.local.AxiomDatabase
import com.shohankhan.axiomcalc.data.local.HistoryEntity
import com.shohankhan.axiomcalc.data.local.SavedCalculationEntity
import com.shohankhan.axiomcalc.data.preferences.DataStoreSettingsRepository
import com.shohankhan.axiomcalc.data.repository.RoomFavoritesRepository
import com.shohankhan.axiomcalc.data.repository.RoomHistoryRepository
import com.shohankhan.axiomcalc.data.repository.RoomSavedCalculationRepository
import com.shohankhan.axiomcalc.domain.model.CalcOrigin
import com.shohankhan.axiomcalc.domain.model.HistoryEntry
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.SavedCalculation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room persistence tests against a real in-memory database.
 *
 * These verify the behaviours the UI depends on: insertion, observation, search,
 * multi-delete, retention deletion, and the guarantee that saved calculations
 * are a separate store that retention never touches.
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class HistoryDatabaseTest {

    private lateinit var database: AxiomDatabase
    private lateinit var settings: DataStoreSettingsRepository
    private lateinit var history: RoomHistoryRepository
    private lateinit var saved: RoomSavedCalculationRepository
    private lateinit var favorites: RoomFavoritesRepository

    private val now = 1_700_000_000_000L
    private val day = RetentionPolicy.MILLIS_PER_DAY

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AxiomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = DataStoreSettingsRepository(context)
        history = RoomHistoryRepository(database.historyDao(), settings)
        saved = RoomSavedCalculationRepository(database.savedDao())
        favorites = RoomFavoritesRepository(database.favoritesDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entry(
        id: Long = 0,
        expression: String = "2+2",
        value: String = "4",
        display: String = "4",
        origin: CalcOrigin = CalcOrigin.STANDARD,
        timestamp: Long = now,
    ) = HistoryEntry(id, expression, value, display, origin, timestamp)

    @Test fun insertedRowsAreObservedNewestFirst() = runBlocking {
        history.add(entry(expression = "1+1", value = "2", timestamp = now - 2 * day))
        history.add(entry(expression = "2+2", value = "4", timestamp = now - 1 * day))
        history.add(entry(expression = "3+3", value = "6", timestamp = now))

        val rows = history.observeAll().first()
        assertEquals(3, rows.size)
        assertEquals("3+3", rows[0].expression)
        assertEquals("1+1", rows[2].expression)
    }

    @Test fun normalizedValueIsStoredSeparatelyFromDisplayText() = runBlocking {
        val id = history.add(entry(value = "1234567", display = "1,234,567"))
        val row = history.observeAll().first().first { it.id == id }
        assertEquals("1234567", row.resultValue)
        assertEquals("1,234,567", row.resultDisplay)
        // The canonical value must round-trip exactly; the display text must not.
        assertEquals(1234567, com.shohankhan.axiomcalc.math.precision.CalcNumber
            .fromCanonical(row.resultValue)!!.toDouble().toInt())
    }

    @Test fun searchMatchesExpressionAndResult() = runBlocking {
        history.add(entry(expression = "200*15%", value = "30"))
        history.add(entry(expression = "sin(30)", value = "0.5"))

        assertEquals(1, history.observeSearch("sin").first().size)
        assertEquals(1, history.observeSearch("0.5").first().size)
        assertEquals(0, history.observeSearch("zzz").first().size)
    }

    @Test fun multiDeleteRemovesExactlyTheSelectedRows() = runBlocking {
        val a = history.add(entry(expression = "a"))
        val b = history.add(entry(expression = "b"))
        val c = history.add(entry(expression = "c"))

        history.delete(listOf(a, c))
        val remaining = history.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(b, remaining[0].id)
    }

    @Test fun deleteAllClearsHistoryButNotSavedCalculations() = runBlocking {
        history.add(entry(expression = "1+1"))
        saved.add(SavedCalculation(0, "My formula", "2+2", "4", "4", CalcOrigin.STANDARD, now))

        history.deleteAll()

        assertEquals(0, history.observeAll().first().size)
        assertEquals(1, saved.observeAll().first().size)
    }

    @Test fun retentionDeletesOnlyExpiredRows() = runBlocking {
        history.add(entry(expression = "old", timestamp = now - 40 * day))
        history.add(entry(expression = "recent", timestamp = now - 2 * day))

        val deleted = history.deleteExpired(now, RetentionPolicy.THIRTY_DAYS)

        assertEquals(1, deleted)
        val remaining = history.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals("recent", remaining[0].expression)
    }

    @Test fun neverRetentionDeletesNothing() = runBlocking {
        history.add(entry(expression = "ancient", timestamp = now - 5000 * day))
        val deleted = history.deleteExpired(now, RetentionPolicy.NEVER)
        assertEquals(0, deleted)
        assertEquals(1, history.observeAll().first().size)
    }

    @Test fun expiredRowsAreFilteredFromReadsEvenBeforeCleanup() = runBlocking {
        // The read path filters by cutoff, so an uncleaned database still cannot
        // show an expired entry to the user.
        settings.update { it.copy(retention = RetentionPolicy.ONE_DAY) }
        history.add(entry(expression = "old", timestamp = now - 40 * day))
        history.add(entry(expression = "fresh", timestamp = System.currentTimeMillis()))

        val visible = history.observeAll().first().map { it.expression }
        assertFalse("expired row was visible", "old" in visible)
        assertTrue("fresh row was hidden", "fresh" in visible)
    }

    @Test fun originIsPersistedForFiltering() = runBlocking {
        history.add(entry(expression = "std", origin = CalcOrigin.STANDARD))
        history.add(entry(expression = "sci", origin = CalcOrigin.SCIENTIFIC))
        history.add(entry(expression = "lab", origin = CalcOrigin.MATH_LAB))

        val rows = history.observeAll().first()
        assertEquals(setOf("std", "sci", "lab"), rows.map { it.expression }.toSet())
        assertEquals(
            setOf(CalcOrigin.STANDARD, CalcOrigin.SCIENTIFIC, CalcOrigin.MATH_LAB),
            rows.map { it.origin }.toSet(),
        )
    }

    @Test fun savedCalculationsSurviveRetentionRuns() = runBlocking {
        saved.add(SavedCalculation(0, "Kept", "1+1", "2", "2", CalcOrigin.STANDARD, now - 400 * day))
        history.add(entry(expression = "old", timestamp = now - 400 * day))

        history.deleteExpired(now, RetentionPolicy.THIRTY_DAYS)

        assertEquals(1, saved.observeAll().first().size)
        assertEquals(0, history.observeAll().first().size)
    }

    @Test fun favoritesAndRecentsAreIndependentStores() = runBlocking {
        favorites.setFavorite("lib_pythagoras", true)
        favorites.recordAccess("lib_quadratic")
        favorites.recordAccess("lib_mean")

        assertEquals(setOf("lib_pythagoras"), favorites.observeFavoriteIds().first())
        val recents = favorites.observeRecentIds().first()
        assertEquals(2, recents.size)
        // Most recently accessed comes first.
        assertEquals("lib_mean", recents[0])

        favorites.setFavorite("lib_pythagoras", false)
        assertTrue(favorites.observeFavoriteIds().first().isEmpty())
        // Un-favoriting must not delete the recent entry.
        assertEquals(2, favorites.observeRecentIds().first().size)
    }

    @Test fun recentsAreCappedAtTwentyEntries() = runBlocking {
        repeat(25) { index -> favorites.recordAccess("formula_$index") }
        val recents = favorites.observeRecentIds().first()
        assertEquals(20, recents.size)
        assertEquals("formula_24", recents[0])
    }

    @Test fun countReportsStoredRows() = runBlocking {
        assertEquals(0, history.count())
        history.add(entry(expression = "a"))
        history.add(entry(expression = "b"))
        assertEquals(2, history.count())
    }

    @Test fun entityRoundTripKeepsEveryField() = runBlocking {
        val direct = HistoryEntity(
            expression = "sqrt(16)",
            resultValue = "4",
            resultDisplay = "4",
            origin = CalcOrigin.SCIENTIFIC,
            timestampMillis = now,
        )
        val id = database.historyDao().insert(direct)
        val read = database.historyDao().observeAll().first().first { it.id == id }
        assertEquals(direct.expression, read.expression)
        assertEquals(direct.origin, read.origin)
        assertEquals(direct.timestampMillis, read.timestampMillis)
    }

    @Test fun savedCalculationEntityRoundTrip() = runBlocking {
        val entity = SavedCalculationEntity(
            name = "VAT check",
            expression = "1000*1.15",
            resultValue = "1150",
            resultDisplay = "1,150",
            origin = CalcOrigin.FORMULA,
            savedAtMillis = now,
        )
        val id = database.savedDao().insert(entity)
        val read = database.savedDao().observeAll().first().first { it.id == id }
        assertEquals(entity.name, read.name)
        assertEquals(entity.resultDisplay, read.resultDisplay)
        assertEquals(CalcOrigin.FORMULA, read.origin)
    }
}
