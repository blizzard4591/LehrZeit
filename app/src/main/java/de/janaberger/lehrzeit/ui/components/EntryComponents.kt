// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.components

import android.text.format.DateFormat
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.DeletedEntry
import de.janaberger.lehrzeit.time.daysBetween
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Sun, Oct 25" or "So., 25. Okt."; with the year if [withYear]. */
@Composable
fun formatDate(date: LocalDate, withYear: Boolean = false): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (withYear) "EEEMMMdy" else "EEEMMMd")
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** "Today", "Yesterday", or the date; the year only if it isn't this year's. */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.worklog_today)
    today.minusDays(1) -> stringResource(R.string.worklog_yesterday)
    else -> formatDate(date, withYear = date.year != today.year)
}

/** "14:00 – 15:30", "22:00 – 01:30 (+1 day)", or "14:00 – now" for a running entry. */
@Composable
fun timeRange(startUtc: Long, endUtc: Long?): String {
    val start = timeOfDay(startUtc)
    if (endUtc == null) return "$start – ${stringResource(R.string.time_now)}"
    val days = daysBetween(startUtc, endUtc, ZoneId.systemDefault()).toInt()
    val end = timeOfDay(endUtc)
    return if (days > 0) "$start – $end (${pluralStringResource(R.plurals.days_later, days, days)})" else "$start – $end"
}

/** Offers Undo for a just-deleted entry once, then forgets it. */
@Composable
fun DeletedEntryUndoEffect(
    deleted: DeletedEntry?,
    snackbarHostState: SnackbarHostState,
    onUndo: (DeletedEntry) -> Unit,
    onForget: (DeletedEntry) -> Unit,
) {
    val message = stringResource(R.string.entry_deleted)
    val undo = stringResource(R.string.action_undo)
    LaunchedEffect(deleted) {
        val entry = deleted ?: return@LaunchedEffect
        try {
            val result = snackbarHostState.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onUndo(entry)
        } finally {
            onForget(entry)
        }
    }
}
