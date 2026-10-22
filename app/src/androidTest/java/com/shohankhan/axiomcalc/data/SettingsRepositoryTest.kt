package com.shohankhan.axiomcalc.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shohankhan.axiomcalc.data.preferences.settingsDataStore
import kotlinx.coroutines.launch
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shohankhan.axiomcalc.data.preferences.DataStoreSettingsRepository
import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DataStore settings tests against a real preferences store.
 *
 * They verify the documented defaults, that only enum names and primitives are
 * persisted, that a written value survives a fresh repository instance (i.e. it
 * really hit disk), and that a corrupt or unknown value degrades to a default
 * instead of crashing the app.
 *
 * Run with `./gradlew connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private lateinit var repository: DataStoreSettingsRepository
    private lateinit var context: android.content.Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = DataStoreSettingsRepository(context)
        // The preferencesDataStore delegate is a per-process singleton over one
        // file, so every test in this class shares state. Clearing all keys
        // rather than writing defaults matters: it makes each test start from an
        // EMPTY store, which is the fresh-install path, so a read has to derive
        // every value from the documented default instead of finding one that a
        // previous test left behind. JUnit4 orders methods by a hash of their
        // names, not by source order, so without this the defaults test reads
        // whatever ran before it.
        runBlocking { context.settingsDataStore.edit { it.clear() } }
    }

    @Test fun defaultsMatchTheSpecification() = runBlocking {
        val settings = repository.current()
        assertEquals(ThemeMode.SYSTEM, settings.theme)
        assertTrue(settings.hapticsEnabled)
        assertEquals("DEGREES", settings.angleModeName)
        assertTrue(settings.autoPrecision)
        assertEquals(6, settings.decimalPlaces)
        assertTrue(settings.grouping)
        assertEquals("DECIMAL", settings.notationName)
        assertEquals(RetentionPolicy.THIRTY_DAYS, settings.retention)
    }

    @Test fun writtenValuesSurviveANewRepositoryInstance() = runBlocking {
        repository.update {
            it.copy(
                theme = ThemeMode.DARK,
                hapticsEnabled = false,
                angleModeName = "RADIANS",
                autoPrecision = false,
                decimalPlaces = 10,
                grouping = false,
                notationName = "ENGINEERING",
                retention = RetentionPolicy.NEVER,
            )
        }

        // A brand-new instance reads the same file, proving persistence.
        val reloaded = DataStoreSettingsRepository(context).current()
        assertEquals(ThemeMode.DARK, reloaded.theme)
        assertFalse(reloaded.hapticsEnabled)
        assertEquals("RADIANS", reloaded.angleModeName)
        assertFalse(reloaded.autoPrecision)
        assertEquals(10, reloaded.decimalPlaces)
        assertFalse(reloaded.grouping)
        assertEquals("ENGINEERING", reloaded.notationName)
        assertEquals(RetentionPolicy.NEVER, reloaded.retention)
    }

    @Test fun updatesArePartialAndKeepOtherValues() = runBlocking {
        repository.update { it.copy(theme = ThemeMode.LIGHT) }
        repository.update { it.copy(decimalPlaces = 3) }

        val settings = repository.current()
        assertEquals(ThemeMode.LIGHT, settings.theme)
        assertEquals(3, settings.decimalPlaces)
        // Untouched keys keep their defaults.
        assertEquals(RetentionPolicy.THIRTY_DAYS, settings.retention)
        assertTrue(settings.grouping)
    }

    @Test fun decimalPlacesAreClampedToTheDocumentedRange() = runBlocking {
        repository.update { it.copy(decimalPlaces = 99) }
        assertEquals(16, repository.current().decimalPlaces)

        repository.update { it.copy(decimalPlaces = -5) }
        assertEquals(0, repository.current().decimalPlaces)
    }

    @Test fun unknownEnumValuesFallBackToDefaults() = runBlocking {
        // Simulates a value written by a future or corrupt build, through the
        // same DataStore instance the repository uses.
        context.settingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey("theme")] = "NOT_A_THEME"
            prefs[stringPreferencesKey("angle_mode")] = "GRADIANS"
            prefs[stringPreferencesKey("notation")] = "HEXADECIMAL"
            prefs[stringPreferencesKey("retention")] = "FOREVER_AND_EVER"
        }

        val recovered = DataStoreSettingsRepository(context).current()
        assertEquals(ThemeMode.SYSTEM, recovered.theme)
        assertEquals("DEGREES", recovered.angleModeName)
        assertEquals("DECIMAL", recovered.notationName)
        assertEquals(RetentionPolicy.THIRTY_DAYS, recovered.retention)
    }

    @Test fun theFlowEmitsOnEveryChange() = runBlocking {
        val emissions = mutableListOf<ThemeMode>()
        // backgroundScope belongs to TestScope and does not exist under
        // runBlocking. A plain child job works here because runBlocking drives a
        // single-threaded event loop, so the collector runs concurrently with the
        // test body and the list needs no synchronisation - but it must be
        // cancelled, or runBlocking waits for it forever.
        val collector = launch {
            repository.settings.collect { emissions += it.theme }
        }
        repository.update { it.copy(theme = ThemeMode.DARK) }
        repository.update { it.copy(theme = ThemeMode.LIGHT) }
        // Wait until the collector has observed the final write.
        kotlinx.coroutines.withTimeout(5_000) {
            while (repository.settings.first().theme != ThemeMode.LIGHT || emissions.size < 2) {
                kotlinx.coroutines.delay(50)
            }
        }
        collector.cancel()
        assertTrue("expected the DARK write to be emitted", ThemeMode.DARK in emissions)
        assertEquals(ThemeMode.LIGHT, repository.settings.first().theme)
    }

    @Test fun noSettingStoresFormattedDisplayText() = runBlocking {
        // Guards the rule that display strings never enter persistence: only
        // names, booleans and integers are written.
        repository.update { it.copy(notationName = "SCIENTIFIC", decimalPlaces = 12) }
        val settings: AppSettings = repository.current()
        listOf(settings.notationName, settings.angleModeName, settings.theme.name, settings.retention.name)
            .forEach { stored ->
                assertFalse("stored value looks like display text: $stored", stored.contains(","))
                assertFalse("stored value looks like display text: $stored", stored.contains(" "))
            }
    }
}
