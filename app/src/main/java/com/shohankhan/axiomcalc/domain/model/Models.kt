package com.shohankhan.axiomcalc.domain.model

/** Application theme selection, persisted in DataStore. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Automatic history retention window.
 *
 * [NEVER] genuinely disables automatic deletion: no cutoff is ever computed and
 * no cleanup work is scheduled. Manual deletion always remains available.
 * Saved calculations and formula favorites are *never* affected by retention.
 */
enum class RetentionPolicy(val days: Int?) {
    ONE_DAY(1),
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    NEVER(null),
    ;

    /** Epoch millis before which a history entry is expired; null = never. */
    fun cutoffMillis(nowMillis: Long): Long? =
        days?.let { nowMillis - it * MILLIS_PER_DAY }

    fun isExpired(timestampMillis: Long, nowMillis: Long): Boolean {
        val cutoff = cutoffMillis(nowMillis) ?: return false
        return timestampMillis < cutoff
    }

    val label: String get() = when (this) {
        ONE_DAY -> "After 1 day"
        SEVEN_DAYS -> "After 7 days"
        THIRTY_DAYS -> "After 30 days"
        NINETY_DAYS -> "After 90 days"
        NEVER -> "Never (keep everything)"
    }

    companion object {
        const val MILLIS_PER_DAY: Long = 86_400_000L
        val DEFAULT: RetentionPolicy = THIRTY_DAYS

        fun fromDays(days: Int?): RetentionPolicy =
            entries.firstOrNull { it.days == days } ?: DEFAULT

        fun fromName(name: String?): RetentionPolicy =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Injectable clock so retention and expiry logic is deterministically testable. */
fun interface TimeSource {
    fun nowMillis(): Long

    companion object {
        val SYSTEM: TimeSource = TimeSource { System.currentTimeMillis() }
        fun fixed(millis: Long): TimeSource = TimeSource { millis }
    }
}

/** Where a calculation originated; stored with every history entry. */
enum class CalcOrigin(val label: String) {
    STANDARD("Standard"),
    SCIENTIFIC("Scientific"),
    FORMULA("Formula"),
    QUICK_SOLVE("Quick Solve"),
    MATH_LAB("Math Lab"),
}

/**
 * One row of global calculation history.
 *
 * [resultValue] stores the *normalized* plain-decimal result so that "reuse"
 * restores an exact value; [resultDisplay] stores the formatted string the user
 * saw. The display string is never parsed back into the engine.
 */
data class HistoryEntry(
    val id: Long,
    val expression: String,
    val resultValue: String,
    val resultDisplay: String,
    val origin: CalcOrigin,
    val timestampMillis: Long,
)

/** A user-named, permanently kept calculation. Never touched by retention. */
data class SavedCalculation(
    val id: Long,
    val name: String,
    val expression: String,
    val resultValue: String,
    val resultDisplay: String,
    val origin: CalcOrigin,
    val savedAtMillis: Long,
)

/** The complete persisted preference set. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val angleModeName: String = "DEGREES",
    val autoPrecision: Boolean = true,
    val decimalPlaces: Int = 6,
    val grouping: Boolean = true,
    val notationName: String = "DECIMAL",
    val retention: RetentionPolicy = RetentionPolicy.DEFAULT,
)
