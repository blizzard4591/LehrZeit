// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import de.janaberger.lehrzeit.data.db.Entry

enum class EntryError { IN_FUTURE, END_NOT_AFTER_START }

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
