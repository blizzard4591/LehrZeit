// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingControllerTest {
    private val hour = 3_600_000L

    private class FakeNotifications : TrackingNotifications {
        var shown: String? = null
        var reminder: String? = null
        override fun showRunning(label: String, startUtc: Long) { shown = "running $label" }
        override fun showPaused(label: String, pausedAt: Long) { shown = "paused $label" }
        override fun cancel() { shown = null }
        override fun showReminder(label: String, startUtc: Long, now: Long) { reminder = label }
        override fun cancelReminder() { reminder = null }
    }

    private class FakeScheduler : ReminderScheduler {
        var scheduledAt: Long? = null
        override fun schedule(atUtc: Long) { scheduledAt = atUtc }
        override fun cancel() { scheduledAt = null }
    }

    private lateinit var db: LehrZeitDatabase
    private lateinit var entries: EntryRepository
    private lateinit var preferences: AppPreferences
    private val notifications = FakeNotifications()
    private val scheduler = FakeScheduler()
    private var now = 0L
    private lateinit var controller: TrackingController
    private lateinit var general: ComboKey
    private lateinit var other: ComboKey

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LehrZeitDatabase::class.java).build()
        val catalog = CatalogRepository(db, starterActivityNames = { emptyList() }, generalDisplayName = { "General" })
        catalog.createInitialAccounts(listOf("7b"))
        general = ComboKey(catalog.observeSnapshot().first().general!!.id, null)
        other = ComboKey(catalog.activeRegularAccounts().single().id, null)
        entries = EntryRepository(db)
        preferences = AppPreferences(context)
        preferences.setReminder(ReminderSettings(enabled = true, hours = 3))
        preferences.setReminderState(null)
        preferences.setPaused(null)
        controller = TrackingController(entries, catalog, preferences, notifications, scheduler, generalName = { "General" }, now = { now })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun reminder_isScheduledFromTheStart_shownWhenDue_andRepeatsOnlyAfterKeepGoing() = runTest {
        now = 10 * hour
        controller.start(general)
        assertEquals("running General", notifications.shown)
        assertEquals(13 * hour, scheduler.scheduledAt)

        now = 13 * hour
        controller.onReminderDue()
        assertEquals("General", notifications.reminder)
        assertNull("no further alarm until Keep going", scheduler.scheduledAt)

        now = 13 * hour + 5 * 60_000
        controller.keepGoing()
        assertNull(notifications.reminder)
        assertEquals(16 * hour + 5 * 60_000, scheduler.scheduledAt)
    }

    @Test
    fun anEarlyAlarm_afterTheStartMoved_onlyReschedules() = runTest {
        now = 10 * hour
        controller.start(general)
        now = 11 * hour
        controller.adjustRunningStart(30 * 60_000) // the start moves later, so does the reminder
        assertEquals(13 * hour + 30 * 60_000, scheduler.scheduledAt)

        now = 13 * hour
        controller.onReminderDue()
        assertNull(notifications.reminder)
        assertEquals(13 * hour + 30 * 60_000, scheduler.scheduledAt)
    }

    @Test
    fun stoppingOrSwitching_clearsTheReminder_andANewEntryCountsAfresh() = runTest {
        now = 10 * hour
        controller.start(general)
        now = 13 * hour
        controller.onReminderDue()

        now = 14 * hour
        controller.start(other) // switch
        assertNull(notifications.reminder)
        assertEquals(17 * hour, scheduler.scheduledAt)

        controller.stop()
        assertNull(scheduler.scheduledAt)
        assertNull(notifications.shown)
    }

    @Test
    fun turningRemindersOff_cancelsTheAlarm() = runTest {
        now = 10 * hour
        controller.start(general)
        preferences.setReminder(ReminderSettings(enabled = false, hours = 3))
        controller.refreshNotification()

        assertNull(scheduler.scheduledAt)
    }
}
