// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingRepositoryTest {
    private lateinit var db: LehrZeitDatabase
    private lateinit var tracking: TrackingRepository
    private lateinit var a: ComboKey
    private lateinit var b: ComboKey

    private val minute = 60_000L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LehrZeitDatabase::class.java)
            .build()
        val catalog = CatalogRepository(db, starterActivityNames = { listOf("Correcting") }, generalDisplayName = { "General" })
        catalog.seedIfNeeded()
        val correcting = catalog.activeActivities().single().id
        val accountId = (catalog.saveAccount(null, "7b", color = 0, activityIds = setOf(correcting)) as SaveResult.Saved).id
        a = ComboKey(accountId, null)
        b = ComboKey(accountId, correcting)
        tracking = TrackingRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun allEntries(): List<Entry> = tracking.observeEntriesOverlapping(Long.MIN_VALUE, Long.MAX_VALUE).first()

    @Test
    fun start_thenStartingTheSameAgain_doesNothing() = runTest {
        assertTrue(tracking.start(a, now = 1_000).started)
        assertFalse(tracking.start(a, now = 2_000).started)

        val running = tracking.getRunning()!!
        assertEquals(a, running.entry.comboKey)
        assertEquals(1_000L, running.entry.startUtc)
        assertEquals(1, allEntries().size)
    }

    @Test
    fun switching_endsTheRunningEntryAtTheSameInstant() = runTest {
        tracking.start(a, now = 1_000)
        tracking.start(b, now = 5_000)

        val (first, second) = allEntries()
        assertEquals(5_000L, first.endUtc)
        assertEquals(5_000L, second.startUtc)
        assertNull(second.endUtc)
        assertEquals(b, tracking.getRunning()!!.entry.comboKey)
    }

    @Test
    fun stop_finishesTheRunningEntry_andDoesNothingWhenIdle() = runTest {
        tracking.start(a, now = 1_000)

        assertEquals(9_000L, tracking.stop(now = 9_000)!!.entry.endUtc)
        assertNull(tracking.getRunning())
        assertNull(tracking.stop(now = 10_000))
    }

    @Test
    fun stoppingInTheSameMillisecond_leavesNoEmptyEntry() = runTest {
        tracking.start(a, now = 1_000)
        tracking.stop(now = 1_000)

        assertTrue(allEntries().isEmpty())
    }

    @Test
    fun entriesShorterThanTheMinimum_areDiscardedOnStopAndSwitch() = runTest {
        tracking.start(a, now = 0)
        val switched = tracking.start(b, now = 30_000, minDurationMillis = 60_000)
        assertTrue(switched.started)
        assertTrue(switched.previous!!.discarded)

        val stopped = tracking.stop(now = 30_000 + 60_000, minDurationMillis = 60_000)!!
        assertFalse(stopped.discarded) // exactly the minimum is kept
        assertEquals(listOf(b), allEntries().map { it.comboKey })
    }

    @Test
    fun adjustRunningStart_staysBetweenPreviousEndAndNow() = runTest {
        tracking.start(a, now = 0)
        tracking.stop(now = 20 * minute)
        tracking.start(b, now = 30 * minute)

        assertTrue(tracking.adjustRunningStart(-5 * minute, now = 40 * minute))
        assertEquals(25 * minute, tracking.getRunning()!!.entry.startUtc)
        assertTrue(tracking.adjustRunningStart(-15 * minute, now = 40 * minute))
        assertEquals(20 * minute, tracking.getRunning()!!.entry.startUtc) // clamped to a's end
        assertFalse(tracking.adjustRunningStart(-5 * minute, now = 40 * minute))
        assertTrue(tracking.adjustRunningStart(30 * minute, now = 40 * minute))
        assertEquals(40 * minute, tracking.getRunning()!!.entry.startUtc) // clamped to now
        assertFalse(tracking.adjustRunningStart(5 * minute, now = 40 * minute))
    }

    @Test
    fun mostUsed_ordersByCountThenRecency() = runTest {
        tracking.start(a, now = 1 * minute)
        tracking.start(b, now = 2 * minute)
        tracking.start(a, now = 3 * minute)
        tracking.stop(now = 4 * minute)

        assertEquals(listOf(a, b), tracking.observeMostUsed(sinceUtc = 0).first())
        assertEquals(listOf(a), tracking.observeMostUsed(sinceUtc = 3 * minute).first())
    }

    @Test
    fun toggleFavorite_addsAndRemovesIncludingAccountOnlyCombos() = runTest {
        tracking.toggleFavorite(b)
        tracking.toggleFavorite(a)
        assertEquals(listOf(b, a), tracking.observeFavorites().first())

        tracking.toggleFavorite(a)
        assertEquals(listOf(b), tracking.observeFavorites().first())
    }

    @Test
    fun overlappingQuery_includesRunningAndCrossingEntries() = runTest {
        tracking.start(a, now = 0)
        tracking.start(b, now = 100)
        tracking.stop(now = 200)
        tracking.start(a, now = 300)

        assertEquals(listOf(0L, 100L, 300L), tracking.observeEntriesOverlapping(50, 1_000).first().map { it.startUtc })
        assertEquals(listOf(100L), tracking.observeEntriesOverlapping(150, 250).first().map { it.startUtc })
    }
}
