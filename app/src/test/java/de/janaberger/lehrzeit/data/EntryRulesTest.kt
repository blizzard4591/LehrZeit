// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import de.janaberger.lehrzeit.data.db.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryRulesTest {
    private fun entry(id: Long, start: Long, end: Long?) =
        Entry(id = id, accountId = 1, activityId = null, startUtc = start, endUtc = end, createdAt = 0, updatedAt = 0)

    @Test
    fun validate() {
        assertNull(validateEntry(startUtc = 10, endUtc = 20, now = 30))
        assertNull(validateEntry(startUtc = 10, endUtc = null, now = 30))
        assertEquals(EntryError.END_NOT_AFTER_START, validateEntry(startUtc = 20, endUtc = 20, now = 30))
        assertEquals(EntryError.IN_FUTURE, validateEntry(startUtc = 10, endUtc = 40, now = 30))
        assertEquals(EntryError.IN_FUTURE, validateEntry(startUtc = 40, endUtc = null, now = 30))
    }

    @Test
    fun noOverlap_touchingEntriesAreFine() {
        val edited = entry(1, 100, 200)
        assertEquals(OverlapPlan.None, planOverlaps(edited, listOf(entry(2, 0, 100), entry(3, 200, 300), edited)))
    }

    @Test
    fun overlaps_areShortenedOrDeleted() {
        val edited = entry(1, 100, 200)
        val before = entry(2, 50, 120) // overlaps the start
        val inside = entry(3, 130, 160) // completely covered
        val after = entry(4, 190, 250) // overlaps the end

        val plan = planOverlaps(edited, listOf(before, inside, after))

        assertEquals(
            OverlapPlan.Adjust(
                listOf(
                    OverlapChange(before, before.copy(endUtc = 100)),
                    OverlapChange(inside, null),
                    OverlapChange(after, after.copy(startUtc = 200)),
                ),
            ),
            plan,
        )
    }

    @Test
    fun editedInsideAnother_isBlocked() {
        val outer = entry(2, 0, 500)
        assertEquals(OverlapPlan.Blocked(outer), planOverlaps(entry(1, 100, 200), listOf(outer)))
    }

    @Test
    fun runningEntries_reachIntoTheFuture() {
        // Editing a finished entry so it ends after the running entry's start moves that start.
        val running = entry(2, 150, null)
        assertEquals(
            OverlapPlan.Adjust(listOf(OverlapChange(running, running.copy(startUtc = 200)))),
            planOverlaps(entry(1, 100, 200), listOf(running)),
        )
        // A finished entry starting after the running one began lies inside it.
        assertEquals(OverlapPlan.Blocked(running), planOverlaps(entry(1, 160, 200), listOf(running)))

        // Moving the running entry's start back cuts earlier entries short or covers them completely.
        val crossing = entry(3, 80, 120)
        val covered = entry(4, 110, 115)
        assertEquals(
            OverlapPlan.Adjust(listOf(OverlapChange(crossing, crossing.copy(endUtc = 100)), OverlapChange(covered, null))),
            planOverlaps(entry(2, 100, null), listOf(entry(5, 20, 60), crossing, covered)),
        )
    }

    @Test
    fun nudgedStart_onlyMovesTheFullStep() {
        val minute = 60_000L
        // With nothing before, earlier always fits, and later up to now.
        assertEquals(25 * minute, nudgedStart(30 * minute, previous = null, deltaMillis = -5 * minute, now = 32 * minute))
        assertNull(nudgedStart(30 * minute, previous = null, deltaMillis = 5 * minute, now = 34 * minute))
        assertEquals(35 * minute, nudgedStart(30 * minute, previous = null, deltaMillis = 5 * minute, now = 35 * minute))

        // A previous entry in the way is shortened, but keeps a minute.
        assertEquals(25 * minute, nudgedStart(30 * minute, entry(1, 20 * minute, 30 * minute), -5 * minute, now = 40 * minute))
        assertEquals(21 * minute, nudgedStart(26 * minute, entry(1, 20 * minute, 26 * minute), -5 * minute, now = 40 * minute))
        assertNull(nudgedStart(25 * minute, entry(1, 20 * minute, 25 * minute), -5 * minute, now = 40 * minute))

        // One that ended early enough doesn't matter, however short it is.
        assertEquals(25 * minute, nudgedStart(30 * minute, entry(1, 24 * minute, 25 * minute), -5 * minute, now = 40 * minute))
    }
}
