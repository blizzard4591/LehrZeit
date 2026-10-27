// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.time.localDateOf
import de.janaberger.lehrzeit.timetable.TimetableProblem
import de.janaberger.lehrzeit.timetable.TimetableStatus
import de.janaberger.lehrzeit.timetable.timetableHost
import de.janaberger.lehrzeit.ui.components.timeOfDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The timetable link (only its host, since the rest may contain a token) and what was downloaded from it. */
@Composable
fun TimetableSection(status: TimetableStatus, onEditLink: () -> Unit, onUpdate: () -> Unit) {
    val url = status.url
    ListItem(
        headlineContent = { Text(stringResource(R.string.timetable_link)) },
        supportingContent = { Text(if (url == null) stringResource(R.string.timetable_link_none) else timetableHost(url)) },
        modifier = Modifier.clickable(onClick = onEditLink),
    )
    if (url == null) return
    val lessons = status.lessons
    val first = lessons.firstStart
    val last = lessons.lastStart
    ListItem(
        headlineContent = {
            Text(
                when {
                    status.isUpdating -> stringResource(R.string.timetable_updating)
                    first == null || last == null -> stringResource(R.string.timetable_no_lessons)
                    else -> pluralStringResource(R.plurals.timetable_lessons, lessons.count, lessons.count, date(first), date(last))
                },
            )
        },
        supportingContent = {
            Column {
                val problem = status.problem
                val problemAt = status.problemAt
                if (problem != null && problemAt != null) {
                    Text(
                        stringResource(R.string.timetable_failed, dateTime(problemAt), problemText(problem, status.httpCode)),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val lastSuccess = status.lastSuccessAt
                when {
                    lastSuccess != null -> Text(stringResource(R.string.timetable_last_update, dateTime(lastSuccess)))
                    problem == null -> Text(stringResource(R.string.timetable_pending))
                }
            }
        },
        trailingContent = {
            TextButton(onClick = onUpdate, enabled = !status.isUpdating) { Text(stringResource(R.string.timetable_update)) }
        },
    )
}

/**
 * Asks for a new link. The field starts empty, so a saved link (and its token) isn't shown.
 * [onSave] returns false if the input isn't an https link.
 */
@Composable
fun TimetableLinkDialog(hasLink: Boolean, onSave: (String) -> Boolean, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.timetable_dialog_text))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        invalid = false
                    },
                    placeholder = {
                        Text(stringResource(if (hasLink) R.string.timetable_link_replace else R.string.timetable_link_placeholder))
                    },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.timetable_link_invalid)) }
                    } else {
                        null
                    },
                    // Autocorrect would turn "WebUntis" into words while typing.
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasLink) {
                    TextButton(
                        onClick = onRemove,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.timetable_remove))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (onSave(input)) onDismiss() else invalid = true }, enabled = input.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun problemText(problem: TimetableProblem, httpCode: Int?): String = when (problem) {
    TimetableProblem.NETWORK -> stringResource(R.string.timetable_problem_network)
    TimetableProblem.HTTP -> stringResource(R.string.timetable_problem_http, httpCode ?: 0)
    TimetableProblem.NOT_CALENDAR -> stringResource(R.string.timetable_problem_not_calendar)
    TimetableProblem.TOO_LARGE -> stringResource(R.string.timetable_problem_too_large)
}

/** "2 Nov 2026" or "2. Nov. 2026", without the weekday. */
@Composable
private fun date(utc: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "MMMdy")
    return DateTimeFormatter.ofPattern(pattern, locale).format(localDateOf(utc, ZoneId.systemDefault()))
}

@Composable
private fun dateTime(utc: Long): String = "${date(utc)}, ${timeOfDay(utc)}"
