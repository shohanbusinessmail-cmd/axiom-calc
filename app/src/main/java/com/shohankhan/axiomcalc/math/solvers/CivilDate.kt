package com.shohankhan.axiomcalc.math.solvers

import com.shohankhan.axiomcalc.core.errors.CalcError
import com.shohankhan.axiomcalc.core.errors.CalcException

/**
 * Proleptic Gregorian calendar date with exact day arithmetic.
 *
 * Implemented with the days-from-civil / civil-from-days bijection (a pure
 * integer algorithm), so date mathematics is exact, independent of time zones,
 * clocks and the platform date library, and therefore identical on the JVM and
 * on Android at minSdk 24 without desugaring.
 *
 * Documented semantics (MATHEMATICS_SPEC.md §Date arithmetic):
 *  * [daysBetweenExclusive] counts elapsed whole days (b - a).
 *  * [daysBetweenInclusive] counts calendar days touched, i.e. elapsed + 1.
 *  * Elapsed duration and calendar-day difference are never conflated: both are
 *    offered and labelled separately in the UI.
 */
data class CivilDate(val year: Int, val month: Int, val day: Int) : Comparable<CivilDate> {

    init {
        if (month !in 1..12) throw CalcException(CalcError.InvalidArgument("month", "month $month outside 1..12"))
        if (day !in 1..daysInMonth(year, month)) {
            throw CalcException(
                CalcError.InvalidArgument("day", "day $day does not exist in ${monthName(month)} $year"),
            )
        }
    }

    /** Days since 1970-01-01 (the epoch), exact. */
    fun toEpochDay(): Long {
        var y = year.toLong()
        val m = month.toLong()
        val d = day.toLong()
        y -= if (m <= 2) 1 else 0
        val era = Math.floorDiv(y, 400)
        val yoe = y - era * 400
        val doy = (153 * (m + if (m > 2) -3 else 9) + 2) / 5 + d - 1
        val doe = yoe * 365 + Math.floorDiv(yoe, 4) - Math.floorDiv(yoe, 100) + doy
        return era * 146097 + doe - 719468
    }

    operator fun plus(days: Long): CivilDate = fromEpochDay(toEpochDay() + days)
    operator fun minus(days: Long): CivilDate = fromEpochDay(toEpochDay() - days)
    operator fun minus(other: CivilDate): Long = toEpochDay() - other.toEpochDay()

    fun plusMonths(months: Int): CivilDate {
        val total = (year.toLong() * 12 + (month - 1)) + months
        val y = Math.floorDiv(total, 12).toInt()
        val m = (Math.floorMod(total, 12) + 1).toInt()
        val d = minOf(day, daysInMonth(y, m))
        return CivilDate(y, m, d)
    }

    fun isLeapYear(): Boolean = isLeap(year)

    override fun compareTo(other: CivilDate): Int = toEpochDay().compareTo(other.toEpochDay())

    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    fun toLongString(): String = "${monthName(month)} $day, $year"

    companion object {
        val EPOCH = CivilDate(1970, 1, 1)

        fun isLeap(year: Int): Boolean =
            (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeap(year)) 29 else 28
            else -> throw CalcException(CalcError.InvalidArgument("month", "month $month outside 1..12"))
        }

        fun monthName(month: Int): String = when (month) {
            1 -> "January"; 2 -> "February"; 3 -> "March"; 4 -> "April"
            5 -> "May"; 6 -> "June"; 7 -> "July"; 8 -> "August"
            9 -> "September"; 10 -> "October"; 11 -> "November"; 12 -> "December"
            else -> "Month $month"
        }

        fun fromEpochDay(epochDay: Long): CivilDate {
            var z = epochDay + 719468
            val era = Math.floorDiv(z, 146097)
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + if (mp < 10) 3 else -9
            return CivilDate((y + if (m <= 2) 1 else 0).toInt(), m.toInt(), d.toInt())
        }

        /** Parses ISO-8601 `yyyy-mm-dd`. */
        fun parse(text: String): CivilDate {
            val parts = text.trim().split('-')
            if (parts.size != 3) throw CalcException(CalcError.InvalidArgument("date", "'$text' is not yyyy-mm-dd"))
            val y = parts[0].toIntOrNull() ?: throw CalcException(CalcError.InvalidArgument("date", "bad year in '$text'"))
            val m = parts[1].toIntOrNull() ?: throw CalcException(CalcError.InvalidArgument("date", "bad month in '$text'"))
            val d = parts[2].toIntOrNull() ?: throw CalcException(CalcError.InvalidArgument("date", "bad day in '$text'"))
            return CivilDate(y, m, d)
        }

        fun daysBetweenExclusive(a: CivilDate, b: CivilDate): Long = b.toEpochDay() - a.toEpochDay()

        /** Calendar days touched, inclusive of both endpoints. */
        fun daysBetweenInclusive(a: CivilDate, b: CivilDate): Long {
            val elapsed = daysBetweenExclusive(a, b)
            return if (elapsed >= 0) elapsed + 1 else elapsed - 1
        }
    }
}

/**
 * Clock-free duration arithmetic in hours/minutes/seconds.
 *
 * Durations are *not* clock times: they may exceed 24 hours by design and are
 * always normalized to a sign plus (h, m, s) with m, s in 0..59. This is stated
 * explicitly in the Time arithmetic category.
 */
data class Duration(val totalSeconds: Long) : Comparable<Duration> {

    val sign: Int get() = java.lang.Long.signum(totalSeconds)
    val hours: Long get() = kotlin.math.abs(totalSeconds) / 3600
    val minutes: Long get() = (kotlin.math.abs(totalSeconds) % 3600) / 60
    val seconds: Long get() = kotlin.math.abs(totalSeconds) % 60

    operator fun plus(o: Duration) = Duration(totalSeconds + o.totalSeconds)
    operator fun minus(o: Duration) = Duration(totalSeconds - o.totalSeconds)
    operator fun unaryMinus() = Duration(-totalSeconds)
    operator fun times(k: Long) = Duration(totalSeconds * k)

    override fun compareTo(other: Duration): Int = totalSeconds.compareTo(other.totalSeconds)

    /** `1h 02m 03s`, prefixed with `-` when negative; hours unbounded. */
    fun format(): String {
        val prefix = if (totalSeconds < 0) "-" else ""
        return "${prefix}${hours}h ${minutes.toString().padStart(2, '0')}m ${seconds.toString().padStart(2, '0')}s"
    }

    fun toDecimalHours(): Double = totalSeconds / 3600.0
    fun toDecimalMinutes(): Double = totalSeconds / 60.0

    companion object {
        /** Builds a duration from components without range validation (a duration, not a clock time). */
        fun of(hours: Long, minutes: Long, seconds: Long): Duration =
            Duration(hours * 3600 + minutes * 60 + seconds)

        /** Normalizes arbitrary (possibly overflowing) h/m/s components. */
        fun normalize(hours: Long, minutes: Long, seconds: Long): Duration =
            Duration(hours * 3600 + minutes * 60 + seconds)

        fun ofSeconds(seconds: Long) = Duration(seconds)

        /** Elapsed duration between two clock times on the same day, in seconds. */
        fun betweenClockTimes(h1: Int, m1: Int, s1: Int, h2: Int, m2: Int, s2: Int): Duration {
            validateClock(h1, m1, s1); validateClock(h2, m2, s2)
            val a = h1 * 3600L + m1 * 60L + s1
            val b = h2 * 3600L + m2 * 60L + s2
            return Duration(b - a)
        }

        private fun validateClock(h: Int, m: Int, s: Int) {
            if (h !in 0..23) throw CalcException(CalcError.InvalidArgument("hour", "clock hour $h outside 0..23"))
            if (m !in 0..59) throw CalcException(CalcError.InvalidArgument("minute", "clock minute $m outside 0..59"))
            if (s !in 0..59) throw CalcException(CalcError.InvalidArgument("second", "clock second $s outside 0..59"))
        }
    }
}
