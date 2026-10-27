// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.stats

import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.time.dayBounds
import de.janaberger.lehrzeit.time.totalWithin
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil

enum class PeriodType { DAY, WEEK, MONTH }

/** A calendar day, an ISO week (Monday to Sunday) or a calendar month. */
data class StatsPeriod(val type: PeriodType, val start: LocalDate) {
    val endExclusive: LocalDate
        get() = when (type) {
            PeriodType.DAY -> start.plusDays(1)
            PeriodType.WEEK -> start.plusWeeks(1)
            PeriodType.MONTH -> start.plusMonths(1)
        }

    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { it < endExclusive }.toList()

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    fun shift(steps: Long): StatsPeriod = when (type) {
        PeriodType.DAY -> copy(start = start.plusDays(steps))
        PeriodType.WEEK -> copy(start = start.plusWeeks(steps))
        PeriodType.MONTH -> copy(start = start.plusMonths(steps))
    }

    companion object {
        fun containing(date: LocalDate, type: PeriodType) = StatsPeriod(
            type,
            when (type) {
                PeriodType.DAY -> date
                PeriodType.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                PeriodType.MONTH -> date.withDayOfMonth(1)
            },
        )
    }
}

data class DayTotal(val date: LocalDate, val millis: Long)

/** [classId] null means booked on the activity without a class. */
data class ClassTotal(val classId: Long?, val millis: Long)

data class ActivityTotal(val activityId: Long, val millis: Long, val classes: List<ClassTotal>)

data class Stats(
    val totalMillis: Long,
    /** One value per day of the period, in order. */
    val perDay: List<DayTotal>,
    /** Largest first; each activity's classes likewise. */
    val byActivity: List<ActivityTotal>,
    /** Largest first, summed over all activities. */
    val byClass: List<ClassTotal>,
)

/**
 * Totals for [period]. Only the part of an entry inside the period (and inside each day)
 * counts, so entries over midnight are split. Running entries count up to [now].
 */
fun computeStats(entries: List<Entry>, period: StatsPeriod, zone: ZoneId, now: Long): Stats {
    val days = period.days.map { dayBounds(it, zone) }
    val from = days.first().startUtc
    val to = days.last().endUtc
    val perCombo = entries.groupBy { it.comboKey }
        .mapValues { (_, comboEntries) -> totalWithin(comboEntries, from, to, now) }
        .filterValues { it > 0 }
    val byActivity = perCombo.entries.groupBy { it.key.activityId }
        .map { (activityId, combos) ->
            ActivityTotal(
                activityId = activityId,
                millis = combos.sumOf { it.value },
                classes = combos.map { ClassTotal(it.key.classId, it.value) }.sortedByDescending { it.millis },
            )
        }
        .sortedByDescending { it.millis }
    val byClass = perCombo.entries.groupBy { it.key.classId }
        .map { (classId, combos) -> ClassTotal(classId, combos.sumOf { it.value }) }
        .sortedByDescending { it.millis }
    return Stats(
        totalMillis = perCombo.values.sum(),
        perDay = days.map { DayTotal(it.date, totalWithin(entries, it.startUtc, it.endUtc, now)) },
        byActivity = byActivity,
        byClass = byClass,
    )
}

/** Gridlines every [stepHours], up to [topHours] (at least one step). */
data class HourScale(val stepHours: Int, val topHours: Int)

/** A clean hour scale with at most four steps that fits [maxMillis]. */
fun hourScale(maxMillis: Long): HourScale {
    val maxHours = maxMillis / 3_600_000.0
    val step = listOf(1, 2, 3, 4, 6, 8, 12).firstOrNull { it * 4 >= maxHours } ?: 6
    val steps = maxOf(1, ceil(maxHours / step).toInt())
    return HourScale(step, step * steps)
}
