package com.shohankhan.axiomcalc.math

/**
 * Angle interpretation used by the trigonometric family of functions.
 *
 * The mode is explicit, always visible in the Scientific UI, and stored as a
 * user preference. There is deliberately no "gradians" mode: it would have to
 * appear everywhere the mode appears, and shipping a half-integrated mode would
 * violate the no-placeholder rule.
 */
enum class AngleMode(val label: String, val symbol: String) {
    DEGREES("Degrees", "DEG"),
    RADIANS("Radians", "RAD"),
    ;

    companion object {
        fun fromName(name: String?): AngleMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEGREES
    }
}
