package com.shohankhan.axiomcalc.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shohankhan.axiomcalc.domain.model.AppSettings
import com.shohankhan.axiomcalc.domain.model.RetentionPolicy
import com.shohankhan.axiomcalc.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// internal so the instrumented suite can write a deliberately corrupt value
// through the SAME DataStore instance (two active stores on one file throw).
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "axiom_calc_settings",
)

/**
 * DataStore-backed implementation of [SettingsRepository].
 *
 * Rules:
 *  * only primitives and enum *names* are persisted — never display text, so a
 *    locale or formatting change can never corrupt stored preferences;
 *  * every read falls back to a documented default when a value is absent or
 *    unparsable, so an unknown/legacy value degrades instead of crashing;
 *  * a read failure of the whole store yields defaults; losing a preference is
 *    far less harmful than blocking the calculator.
 */
class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val store: DataStore<Preferences> = context.settingsDataStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e ->
            // Cancellation is control flow, not a corrupt store. Swallowing it
            // would turn a cancelled read into a silent set of defaults and
            // break structured concurrency for whoever cancelled.
            if (e is CancellationException) throw e
            emit(emptyPreferences())
        }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = try {
        store.data.map { it.toSettings() }.first()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A genuinely unreadable or corrupt store degrades to the documented
        // defaults rather than blocking the calculator. This is the only
        // failure that may be absorbed, and it is why the branch above exists.
        AppSettings()
    }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = current()
        val next = transform(current).sanitize()
        store.edit { prefs -> prefs.write(next) }
    }

    /** Clamps user-adjustable numbers to their documented ranges. */
    private fun AppSettings.sanitize(): AppSettings = copy(
        decimalPlaces = decimalPlaces.coerceIn(0, 16),
        angleModeName = if (angleModeName in ANGLE_MODES) angleModeName else "DEGREES",
        notationName = if (notationName in NOTATIONS) notationName else "DECIMAL",
        theme = theme,
        retention = retention,
    )

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(KEY_THEME, d.theme),
            hapticsEnabled = this[KEY_HAPTICS] ?: d.hapticsEnabled,
            reduceMotion = this[KEY_REDUCE_MOTION] ?: d.reduceMotion,
            angleModeName = this[KEY_ANGLE_MODE].takeIf { it in ANGLE_MODES } ?: d.angleModeName,
            autoPrecision = this[KEY_AUTO_PRECISION] ?: d.autoPrecision,
            decimalPlaces = (this[KEY_DECIMAL_PLACES] ?: d.decimalPlaces).coerceIn(0, 16),
            grouping = this[KEY_GROUPING] ?: d.grouping,
            notationName = this[KEY_NOTATION].takeIf { it in NOTATIONS } ?: d.notationName,
            retention = RetentionPolicy.fromName(this[KEY_RETENTION]),
        )
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[KEY_THEME] = s.theme.name
        this[KEY_HAPTICS] = s.hapticsEnabled
        this[KEY_REDUCE_MOTION] = s.reduceMotion
        this[KEY_ANGLE_MODE] = s.angleModeName
        this[KEY_AUTO_PRECISION] = s.autoPrecision
        this[KEY_DECIMAL_PLACES] = s.decimalPlaces
        this[KEY_GROUPING] = s.grouping
        this[KEY_NOTATION] = s.notationName
        this[KEY_RETENTION] = s.retention.name
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOr(
        key: Preferences.Key<String>,
        default: T,
    ): T {
        val raw = this[key] ?: return default
        return enumValues<T>().firstOrNull { it.name == raw } ?: default
    }

    private companion object Keys {
        val KEY_THEME: Preferences.Key<String> = stringPreferencesKey("theme")
        val KEY_HAPTICS: Preferences.Key<Boolean> = booleanPreferencesKey("haptics_enabled")
        val KEY_REDUCE_MOTION: Preferences.Key<Boolean> = booleanPreferencesKey("reduce_motion")
        val KEY_ANGLE_MODE: Preferences.Key<String> = stringPreferencesKey("angle_mode")
        val KEY_AUTO_PRECISION: Preferences.Key<Boolean> = booleanPreferencesKey("auto_precision")
        val KEY_DECIMAL_PLACES: Preferences.Key<Int> = intPreferencesKey("decimal_places")
        val KEY_GROUPING: Preferences.Key<Boolean> = booleanPreferencesKey("grouping")
        val KEY_NOTATION: Preferences.Key<String> = stringPreferencesKey("notation")
        val KEY_RETENTION: Preferences.Key<String> = stringPreferencesKey("retention")

        val ANGLE_MODES = setOf("DEGREES", "RADIANS")
        val NOTATIONS = setOf("DECIMAL", "SCIENTIFIC", "ENGINEERING")
    }
}
