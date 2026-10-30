// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** "1:23:45" */
fun formatElapsed(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

/** "3:05", meaning hours and minutes. */
fun formatHoursMinutes(millis: Long): String {
    val minutes = millis.coerceAtLeast(0) / 60_000
    return String.format(Locale.ROOT, "%d:%02d", minutes / 60, minutes % 60)
}

/** Time of day in the device's time zone, e.g. "14:05" or "2:05 PM". */
fun formatTimeOfDay(utc: Long, locale: Locale): String = formatTimeOfDay(Instant.ofEpochMilli(utc).atZone(ZoneId.systemDefault()).toLocalTime(), locale)

/** "14:05" or "2:05 PM" */
fun formatTimeOfDay(time: LocalTime, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time)

/**
 * Weekdays, Monday first, with three or more in a row as a range: "Mon–Fri", "Mon, Wed",
 * "Mon–Wed, Fri". All seven are [everyDay].
 */
fun formatWeekdays(days: Set<DayOfWeek>, locale: Locale, everyDay: String): String {
    if (days.size == 7) return everyDay
    fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    DayOfWeek.entries.filter { it in days }.forEach { day ->
        val run = runs.lastOrNull()
        if (run != null && run.last().value + 1 == day.value) run += day else runs += mutableListOf(day)
    }
    return runs.flatMap { run -> if (run.size >= 3) listOf("${name(run.first())}–${name(run.last())}") else run.map(::name) }
        .joinToString(", ")
}
