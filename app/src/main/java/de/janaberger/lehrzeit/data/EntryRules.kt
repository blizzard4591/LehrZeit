// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import de.janaberger.lehrzeit.data.db.Entry

const val MAX_NOTE_LENGTH = 500

/** Moving the running entry's start may shorten the previous entry, but not below this. */
const val MIN_SHORTENED_MILLIS = 60_000L

enum class EntryError { IN_FUTURE, END_NOT_AFTER_START }

/**
 * Where the running entry's [start] lands when moved by [deltaMillis], or null if the full step
 * doesn't fit. The start can't pass [now]. [previous], the entry that ended last before [start],
 * is shortened if it's in the way, but must keep [MIN_SHORTENED_MILLIS].
 */
fun nudgedStart(start: Long, previous: Entry?, deltaMillis: Long, now: Long): Long? {
    val moved = start + deltaMillis
    if (moved > now) return null
    val previousEnd = previous?.endUtc ?: return moved
    return moved.takeIf { previousEnd <= moved || moved - previous.startUtc >= MIN_SHORTENED_MILLIS }
}

data class EntryChange(val original: Entry, val changed: Entry)

/** A moved start of the running entry, and the previous entry if it had to be shortened. */
data class StartNudge(val running: EntryChange, val previous: EntryChange?)

/** Checks an edited entry; [endUtc] is null for a running entry. */
fun validateEntry(startUtc: Long, endUtc: Long?, now: Long): EntryError? = when {
    startUtc > now || (endUtc != null && endUtc > now) -> EntryError.IN_FUTURE
    endUtc != null && endUtc <= startUtc -> EntryError.END_NOT_AFTER_START
    else -> null
}

/** What saving an entry does to the entries it would overlap. */
sealed interface OverlapPlan {
    data object None : OverlapPlan

    /** Each overlapped entry is either shortened or, if completely covered, deleted. */
    data class Adjust(val changes: List<OverlapChange>) : OverlapPlan

    /** The edited entry lies completely inside [by], which would have to be split. Not supported. */
    data class Blocked(val by: Entry) : OverlapPlan
}

/** [adjusted] is the shortened entry, or null if [original] gets deleted. */
data class OverlapChange(val original: Entry, val adjusted: Entry?)

/**
 * Decides how [others] must change so that [edited] fits without overlaps.
 * Running entries (no end) count as reaching into the future.
 */
fun planOverlaps(edited: Entry, others: List<Entry>): OverlapPlan {
    val start = edited.startUtc
    val end = edited.endUtc ?: Long.MAX_VALUE
    val changes = mutableListOf<OverlapChange>()
    for (other in others) {
        if (other.id == edited.id) continue
        val otherStart = other.startUtc
        val otherEnd = other.endUtc ?: Long.MAX_VALUE
        if (otherEnd <= start || otherStart >= end) continue
        changes += when {
            otherStart >= start && otherEnd <= end -> OverlapChange(other, adjusted = null)
            otherStart < start && otherEnd > end -> return OverlapPlan.Blocked(other)
            // The other entry overlaps the edited one's start, so it now ends there.
            otherStart < start -> OverlapChange(other, other.copy(endUtc = start))
            // The other entry overlaps the edited one's end, so it now starts there.
            else -> OverlapChange(other, other.copy(startUtc = end))
        }
    }
    return if (changes.isEmpty()) OverlapPlan.None else OverlapPlan.Adjust(changes)
}
