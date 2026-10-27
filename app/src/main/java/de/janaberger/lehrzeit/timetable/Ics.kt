// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import java.time.DateTimeException
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A timed event from an iCalendar file, with the fields LehrZeit keeps. */
data class IcsEvent(
    val uid: String,
    val startUtc: Long,
    val endUtc: Long,
    val summary: String,
    val description: String,
    val location: String,
)

/**
 * Reads the timed events of an iCalendar file (RFC 5545), or returns null if [text] isn't one.
 * Times with a TZID use that zone; unknown zones and floating times use [defaultZone].
 * Skipped: all-day events, cancelled events, and events without a valid end. Repeat rules
 * aren't expanded, so only the first occurrence of a repeating event is read.
 */
fun parseIcs(text: String, defaultZone: ZoneId): List<IcsEvent>? {
    val lines = text.removePrefix("\uFEFF")
        .replace(FOLDED_LINE, "")
        .lines()
        .filter { it.isNotBlank() }
    if (!lines.firstOrNull().orEmpty().trim().equals("BEGIN:VCALENDAR", ignoreCase = true)) return null

    val events = mutableListOf<IcsEvent>()
    val components = ArrayDeque<String>()
    var event: MutableMap<String, IcsProperty>? = null
    for (line in lines) {
        val property = parseProperty(line) ?: continue
        when (property.name) {
            "BEGIN" -> {
                components.addLast(property.value.trim().uppercase())
                if (components.size == 2 && components.last() == "VEVENT") event = mutableMapOf()
            }
            "END" -> {
                if (components.removeLastOrNull() == "VEVENT" && components.size == 1) {
                    event?.let { toEvent(it, defaultZone) }?.let(events::add)
                    event = null
                }
            }
            // Properties of nested components, e.g. an alarm inside the event, are ignored.
            else -> if (components.size == 2) event?.putIfAbsent(property.name, property)
        }
    }
    return events
}

/** A long line continues on the next one, which starts with a space or tab. */
private val FOLDED_LINE = Regex("\r?\n[ \t]")

private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

private data class IcsProperty(val name: String, val params: Map<String, String>, val value: String)

/** "DTSTART;TZID=Europe/Berlin:20261102T080000". Colons and semicolons inside quotes don't count. */
private fun parseProperty(line: String): IcsProperty? {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for ((index, char) in line.withIndex()) {
        when {
            char == '"' -> inQuotes = !inQuotes
            char == ';' && !inQuotes -> parts += current.toString().also { current.clear() }
            char == ':' && !inQuotes -> {
                parts += current.toString()
                val params = parts.drop(1).mapNotNull { param ->
                    val (key, value) = param.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                    key.trim().uppercase() to value.trim()
                }.toMap()
                return IcsProperty(parts.first().trim().uppercase(), params, line.substring(index + 1))
            }
            else -> current.append(char)
        }
    }
    return null
}

private fun toEvent(properties: Map<String, IcsProperty>, defaultZone: ZoneId): IcsEvent? {
    if (properties["STATUS"]?.value?.trim().equals("CANCELLED", ignoreCase = true)) return null
    val start = properties["DTSTART"]?.let { epochMillis(it, defaultZone) } ?: return null
    val end = properties["DTEND"]?.let { epochMillis(it, defaultZone) }
        ?: properties["DURATION"]?.let { durationMillis(it.value) }?.let { start + it }
        ?: return null
    if (end <= start) return null
    val summary = properties["SUMMARY"]?.value?.let(::unescape).orEmpty()
    return IcsEvent(
        // Every event should have one; without it, an update couldn't tell it apart from a new one.
        uid = properties["UID"]?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "generated-$start-$end-$summary",
        startUtc = start,
        endUtc = end,
        summary = summary,
        description = properties["DESCRIPTION"]?.value?.let(::unescape).orEmpty(),
        location = properties["LOCATION"]?.value?.let(::unescape).orEmpty(),
    )
}

/** A DATE-TIME as epoch milliseconds; null for a DATE (all-day) or an unreadable value. */
private fun epochMillis(property: IcsProperty, defaultZone: ZoneId): Long? {
    val value = property.value.trim()
    if (property.params["VALUE"].equals("DATE", ignoreCase = true) || 'T' !in value) return null
    val zone = when {
        value.endsWith("Z", ignoreCase = true) -> ZoneOffset.UTC
        else -> property.params["TZID"]?.let(::zoneOrNull) ?: defaultZone
    }
    return try {
        LocalDateTime.parse(value.trimEnd('Z', 'z').take(15), DATE_TIME).atZone(zone).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        null
    }
}

private fun zoneOrNull(id: String): ZoneId? = try {
    ZoneId.of(id.trim('"'))
} catch (e: DateTimeException) {
    null
}

private fun durationMillis(value: String): Long? = try {
    Duration.parse(value.trim()).toMillis().takeIf { it > 0 }
} catch (e: DateTimeParseException) {
    null
}

/** "07.3\; V-DoBe" becomes "07.3; V-DoBe"; "\n" becomes a line break. */
private fun unescape(value: String): String {
    val out = StringBuilder()
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '\\' && index + 1 < value.length) {
            val next = value[index + 1]
            out.append(if (next == 'n' || next == 'N') '\n' else next)
            index += 2
        } else {
            out.append(char)
            index++
        }
    }
    return out.toString().trim()
}
