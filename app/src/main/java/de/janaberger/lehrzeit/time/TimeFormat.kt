// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
fun formatTimeOfDay(utc: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .format(Instant.ofEpochMilli(utc).atZone(ZoneId.systemDefault()))
