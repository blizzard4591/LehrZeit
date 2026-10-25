// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private fun zoned(utc: Long, zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(utc).atZone(zone)

fun localDateOf(utc: Long, zone: ZoneId): LocalDate = zoned(utc, zone).toLocalDate()

/** The same day as [utc] at [hour]:[minute]:00. */
fun withTimeOfDay(utc: Long, hour: Int, minute: Int, zone: ZoneId): Long =
    zoned(utc, zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli()

/** The first [hour]:[minute] after [afterUtc]: the same day, or the next one. Used for end times. */
fun firstOccurrenceAfter(afterUtc: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val sameDay = zoned(afterUtc, zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    return (if (sameDay.toInstant().toEpochMilli() > afterUtc) sameDay else sameDay.plusDays(1)).toInstant().toEpochMilli()
}

/** Moves [utc] by the number of days from [from] to [to], keeping the local time of day. */
fun moveByDays(utc: Long, from: LocalDate, to: LocalDate, zone: ZoneId): Long =
    zoned(utc, zone).plusDays(ChronoUnit.DAYS.between(from, to)).toInstant().toEpochMilli()

/** How many calendar days [endUtc] lies after [startUtc]'s day. */
fun daysBetween(startUtc: Long, endUtc: Long, zone: ZoneId): Long =
    ChronoUnit.DAYS.between(localDateOf(startUtc, zone), localDateOf(endUtc, zone))

fun floorToMinutes(utc: Long, minutes: Int): Long = utc - Math.floorMod(utc, minutes * 60_000L)
