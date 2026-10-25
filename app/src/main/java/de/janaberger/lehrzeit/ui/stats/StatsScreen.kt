// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.stats

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.stats.PeriodType
import de.janaberger.lehrzeit.stats.Stats
import de.janaberger.lehrzeit.stats.StatsPeriod
import de.janaberger.lehrzeit.stats.computeStats
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.dayLabel
import de.janaberger.lehrzeit.ui.components.displayName
import de.janaberger.lehrzeit.ui.components.formatDate
import de.janaberger.lehrzeit.ui.components.rememberNow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberNow(ticking = state.entries.any { it.endUtc == null })
    var expandedAccounts by rememberSaveable { mutableStateOf(emptyList<Long>()) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_stats)) }) }) { padding ->
        val period = state.period
        val today = state.today
        val snapshot = state.snapshot
        if (state.isLoading || period == null || today == null || snapshot == null) return@Scaffold
        // Recomputed once a minute while a timer runs; totals are shown in minutes anyway.
        val minute = now / 60_000
        val stats = remember(state.entries, period, minute) { computeStats(state.entries, period, ZoneId.systemDefault(), now) }

        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "type") {
                OptionRow(
                    options = listOf(
                        PeriodType.DAY to stringResource(R.string.stats_day),
                        PeriodType.WEEK to stringResource(R.string.stats_week),
                        PeriodType.MONTH to stringResource(R.string.stats_month),
                    ),
                    selected = period.type,
                    onSelect = viewModel::setType,
                )
            }
            item(key = "navigator") {
                PeriodNavigator(period, today, state.canGoNext, viewModel::previous, viewModel::next)
            }
            item(key = "total") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.stats_total),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.hours_value, formatHoursMinutes(stats.totalMillis)), style = MaterialTheme.typography.displayLarge)
                }
            }
            if (period.type != PeriodType.DAY) {
                item(key = "chart") {
                    ChartSection(stats, period, state.selectedDay, today, viewModel::selectDay, viewModel::openDay)
                }
            }
            item(key = "breakdown-mode") {
                OptionRow(
                    options = listOf(
                        BreakdownMode.ACCOUNT to stringResource(R.string.stats_by_account),
                        BreakdownMode.ACTIVITY to stringResource(R.string.stats_by_activity),
                    ),
                    selected = state.breakdown,
                    onSelect = viewModel::setBreakdown,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (stats.totalMillis == 0L) {
                item(key = "empty") { NoteText(stringResource(R.string.stats_empty), Modifier.padding(top = 8.dp)) }
            } else {
                breakdownItems(stats, snapshot, state.breakdown, expandedAccounts) { accountId ->
                    expandedAccounts = if (accountId in expandedAccounts) expandedAccounts - accountId else expandedAccounts + accountId
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun PeriodNavigator(period: StatsPeriod, today: LocalDate, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val title: String
    val subtitle: String?
    when (period.type) {
        PeriodType.DAY -> {
            title = dayLabel(period.start, today)
            // "Today" and "Yesterday" get the date underneath.
            subtitle = if (period.start >= today.minusDays(1)) formatDate(period.start) else null
        }
        PeriodType.WEEK -> {
            val last = period.endExclusive.minusDays(1)
            val pattern = DateFormat.getBestDateTimePattern(locale, if (last.year != today.year) "MMMdy" else "MMMd")
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            title = "${formatter.format(period.start)} – ${formatter.format(last)}"
            subtitle = stringResource(R.string.stats_week_number, period.start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        }
        PeriodType.MONTH -> {
            title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMy"), locale).format(period.start)
            subtitle = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.stats_previous))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.stats_next))
        }
    }
}

@Composable
private fun ChartSection(
    stats: Stats,
    period: StatsPeriod,
    selectedDay: LocalDate?,
    today: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val xLabels = stats.perDay.map { day ->
        when (period.type) {
            PeriodType.WEEK -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            // Every fifth day plus the first, so the labels never collide.
            else -> day.date.dayOfMonth.takeIf { it == 1 || it % 5 == 0 }?.toString()
        }
    }
    val descriptions = stats.perDay.map { day ->
        stringResource(R.string.stats_day_value, formatDate(day.date), formatHoursMinutes(day.millis))
    }
    DayBarChart(
        days = stats.perDay,
        xLabels = xLabels,
        descriptions = descriptions,
        selected = selectedDay,
        onSelect = onSelectDay,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
    ) {
        val selected = stats.perDay.firstOrNull { it.date == selectedDay }
        if (selected == null) {
            Text(
                stringResource(R.string.stats_tap_bar),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(
                    R.string.stats_day_value,
                    dayLabel(selected.date, today),
                    formatHoursMinutes(selected.millis),
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onOpenDay(selected.date) }) { Text(stringResource(R.string.stats_open_day)) }
        }
    }
}

private fun LazyListScope.breakdownItems(
    stats: Stats,
    snapshot: CatalogSnapshot,
    mode: BreakdownMode,
    expandedAccounts: List<Long>,
    onToggleAccount: (Long) -> Unit,
) {
    when (mode) {
        BreakdownMode.ACCOUNT -> stats.byAccount.forEach { account ->
            val combo = snapshot.combo(ComboKey(account.accountId, null))
            val isExpanded = account.accountId in expandedAccounts
            val expandable = account.activities.any { it.activityId != null }
            item(key = "account-${account.accountId}") {
                BreakdownRow(
                    label = combo?.account?.displayName().orEmpty(),
                    millis = account.millis,
                    totalMillis = stats.totalMillis,
                    color = combo?.account?.color,
                    onClick = if (expandable) ({ onToggleAccount(account.accountId) }) else null,
                    trailing = if (expandable) {
                        {
                            Icon(
                                painterResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                contentDescription = stringResource(if (isExpanded) R.string.picker_collapse else R.string.picker_expand),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            if (isExpanded) {
                items(account.activities, key = { "account-${account.accountId}-${it.activityId}" }) { activity ->
                    BreakdownRow(
                        label = activityName(snapshot, activity.activityId),
                        millis = activity.millis,
                        totalMillis = stats.totalMillis,
                        color = null,
                        indent = 28.dp,
                    )
                }
            }
        }
        BreakdownMode.ACTIVITY -> items(stats.byActivity, key = { "activity-${it.activityId}" }) { activity ->
            BreakdownRow(
                label = activityName(snapshot, activity.activityId),
                millis = activity.millis,
                totalMillis = stats.totalMillis,
                color = null,
            )
        }
    }
}

@Composable
private fun activityName(snapshot: CatalogSnapshot, activityId: Long?): String =
    activityId?.let { id -> snapshot.activities.firstOrNull { it.id == id }?.name }
        ?: stringResource(R.string.stats_without_activity)

/** Label, value and share, over a thin meter of the share in one color on a lighter track. */
@Composable
private fun BreakdownRow(
    label: String,
    millis: Long,
    totalMillis: Long,
    color: Int?,
    indent: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val share = if (totalMillis > 0) millis.toFloat() / totalMillis else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp + indent, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (color != null) {
                ColorDot(color, size = 12.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.hours_value, formatHoursMinutes(millis)), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.stats_percent, Math.round(share * 100)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(48.dp),
            )
            // Reserved on every row so the hour column lines up whether or not a row can expand.
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(24.dp),
            ) { trailing?.invoke() }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
