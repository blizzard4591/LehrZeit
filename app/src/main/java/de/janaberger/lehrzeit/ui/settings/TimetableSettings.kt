// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.time.formatTimeOfDay
import de.janaberger.lehrzeit.time.formatWeekdays
import de.janaberger.lehrzeit.time.localDateOf
import de.janaberger.lehrzeit.timetable.TimetableProblem
import de.janaberger.lehrzeit.timetable.TimetableSchedule
import de.janaberger.lehrzeit.timetable.TimetableStatus
import de.janaberger.lehrzeit.timetable.timetableHost
import de.janaberger.lehrzeit.ui.components.TimePickerDialog
import de.janaberger.lehrzeit.ui.components.timeOfDay
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * The timetable link (only its host, since the rest may contain a token), what was downloaded from
 * it, and when it's downloaded on its own.
 */
@Composable
fun TimetableSection(
    status: TimetableStatus,
    schedule: TimetableSchedule,
    onEditLink: () -> Unit,
    onUpdate: () -> Unit,
    onEditSchedule: () -> Unit,
) {
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
    ListItem(
        headlineContent = { Text(stringResource(R.string.timetable_schedule)) },
        supportingContent = { Text(scheduleText(schedule)) },
        modifier = Modifier.clickable(onClick = onEditSchedule),
    )
}

/** "Mon–Fri at 07:00, 07:55, 08:30, 17:00", or that there are no automatic updates. */
@Composable
private fun scheduleText(schedule: TimetableSchedule): String {
    if (schedule.isOff) return stringResource(R.string.timetable_schedule_off)
    val locale = LocalConfiguration.current.locales[0]
    val days = formatWeekdays(schedule.days, locale, stringResource(R.string.timetable_schedule_every_day))
    val times = schedule.times.sorted().joinToString(", ") { formatTimeOfDay(it, locale) }
    return stringResource(R.string.timetable_schedule_summary, days, times)
}

/**
 * The days and times of the automatic updates; nothing changes until Save. Times are kept as
 * minutes of the day and days as ISO numbers, which rememberSaveable can store.
 */
@Composable
fun TimetableScheduleDialog(schedule: TimetableSchedule, onSave: (TimetableSchedule) -> Unit, onDismiss: () -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(schedule.times.map { it.toSecondOfDay() / 60 }.distinct().sorted()) }
    var days by rememberSaveable { mutableStateOf(schedule.dayNumbers()) }
    // The time being changed, or NEW_TIME while one is added.
    var picking by rememberSaveable { mutableStateOf<Int?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    fun label(minute: Int) = formatTimeOfDay(LocalTime.ofSecondOfDay(minute * 60L), locale)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_schedule)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.timetable_schedule_text))
                DialogLabel(stringResource(R.string.timetable_schedule_days))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day.value in days
                        FilterChip(
                            selected = selected,
                            onClick = { days = if (selected) days - day.value else (days + day.value).sorted() },
                            label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
                        )
                    }
                }
                DialogLabel(stringResource(R.string.timetable_schedule_times))
                minutes.forEach { minute ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label(minute),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClickLabel = stringResource(R.string.timetable_schedule_change)) { picking = minute }
                                .padding(vertical = 12.dp),
                        )
                        IconButton(onClick = { minutes = minutes - minute }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.timetable_schedule_remove, label(minute)))
                        }
                    }
                }
                TextButton(onClick = { picking = NEW_TIME }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.timetable_schedule_add))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(TimetableSchedule(minutes.map { LocalTime.ofSecondOfDay(it * 60L) }, days.map(DayOfWeek::of).toSet()))
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    picking?.let { changing ->
        val initial = if (changing == NEW_TIME) NEW_TIME_START else changing
        TimePickerDialog(
            initialHour = initial / 60,
            initialMinute = initial % 60,
            onConfirm = { hour, minute ->
                minutes = (minutes - changing + (hour * 60 + minute)).distinct().sorted()
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private const val NEW_TIME = -1

/** Where the picker starts for a new time: noon. */
private const val NEW_TIME_START = 12 * 60

@Composable
private fun DialogLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
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
