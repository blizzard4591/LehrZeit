// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.data.PausedState
import de.janaberger.lehrzeit.data.ReminderState
import de.janaberger.lehrzeit.data.StartNudge
import de.janaberger.lehrzeit.data.comboKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place that changes the tracking state, used by the UI, the notification buttons and the
 * widget. Every change updates the notification, the "still working?" reminder and the widgets.
 * Changes run to completion even if the caller is cancelled, so the database, the notification and
 * the reminder alarm can't drift apart.
 *
 * Methods that end an entry return true if it was discarded for being too short.
 */
class TrackingController(
    private val entries: EntryRepository,
    private val catalog: CatalogRepository,
    private val preferences: AppPreferences,
    private val notifier: TrackingNotifications,
    private val reminders: ReminderScheduler,
    private val widgets: WidgetUpdater,
    private val generalName: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** Starts [key]; a running entry for another combination ends at the same instant. */
    suspend fun start(key: ComboKey): Boolean = change {
        preferences.setPaused(null)
        entries.start(key, now(), minDurationMillis()).previous?.discarded == true
    }

    /**
     * Starts [key] only if no other timer runs. Returns false if one does, so the caller can ask
     * before switching; true if [key] was started or was already running.
     */
    suspend fun startIfIdle(key: ComboKey): Boolean = change {
        val running = entries.getRunning() ?: run {
            preferences.setPaused(null)
            entries.start(key, now())
            return@change true
        }
        running.entry.comboKey == key
    }

    suspend fun stop(): Boolean = change {
        preferences.setPaused(null)
        entries.stop(now(), minDurationMillis())?.discarded == true
    }

    /** Ends the running entry and remembers its combination for [resume]. */
    suspend fun pause(): Boolean = change {
        val finished = entries.stop(now(), minDurationMillis()) ?: return@change false
        preferences.setPaused(PausedState(finished.entry.comboKey, now()))
        finished.discarded
    }

    suspend fun resume() = change {
        val paused = preferences.getPaused() ?: return@change
        preferences.setPaused(null)
        entries.start(paused.key, now())
    }

    /** Forgets the paused combination. */
    suspend fun dismissPaused() = change {
        preferences.setPaused(null)
    }

    /** See [EntryRepository.nudgeRunningStart]. */
    suspend fun nudgeRunningStart(deltaMillis: Long): StartNudge? = change {
        entries.nudgeRunningStart(deltaMillis, now())
    }

    suspend fun undoNudge(nudge: StartNudge): Boolean = change {
        entries.undoNudge(nudge, now())
    }

    /**
     * The user swiped the notification away. A running timer's notification comes straight back;
     * a paused one stays away, and the pause is forgotten.
     */
    suspend fun onNotificationDismissed() = change {
        if (entries.getRunning() == null) preferences.setPaused(null)
    }

    /**
     * The reminder alarm went off. Shows the reminder if it is still due; if the start was moved
     * in the meantime, the alarm is just rescheduled.
     */
    suspend fun onReminderDue() = change {
        val running = entries.getRunning() ?: return@change
        val state = preferences.getReminderState()?.takeIf { it.entryId == running.entry.id } ?: return@change
        val due = nextReminderAt(running.entry.startUtc, state, preferences.reminder.first()) ?: return@change
        if (now() < due) return@change
        preferences.setReminderState(state.copy(fired = true))
        notifier.showReminder(Combo(running.activity, running.schoolClass).label(generalName()), running.entry.startUtc, now())
    }

    /** "Keep going" (or swiping the reminder away): ask again after the reminder time from now. */
    suspend fun keepGoing() = change {
        val running = entries.getRunning() ?: return@change
        preferences.setReminderState(ReminderState(running.entry.id, anchorUtc = now()))
        notifier.cancelReminder()
    }

    /**
     * Makes the notification, the reminder and the widgets match the stored state, e.g. after a
     * reboot, an app update, an edited entry or changed settings.
     */
    suspend fun refreshNotification() = change {}

    private suspend fun minDurationMillis(): Long = preferences.shortEntries.first().minMillis

    private suspend fun <T> change(block: suspend () -> T): T = withContext(NonCancellable) {
        mutex.withLock {
            val result = block()
            updateNotification()
            syncReminder()
            widgets.update()
            result
        }
    }

    /** Schedules the reminder for the running entry, or cancels it when nothing runs or it's off. */
    private suspend fun syncReminder() {
        val running = entries.getRunning()?.entry
        if (running == null) {
            preferences.setReminderState(null)
            reminders.cancel()
            notifier.cancelReminder()
            return
        }
        val settings = preferences.reminder.first()
        val stored = preferences.getReminderState()
        val state = if (stored?.entryId == running.id) {
            stored
        } else {
            // A new entry runs: start counting afresh, and drop a reminder about the previous one.
            notifier.cancelReminder()
            ReminderState(running.id).also { preferences.setReminderState(it) }
        }
        val due = nextReminderAt(running.startUtc, state, settings)
        if (due != null) {
            reminders.schedule(due)
        } else {
            reminders.cancel()
            if (!settings.enabled) notifier.cancelReminder()
        }
    }

    private suspend fun updateNotification() {
        val running = entries.getRunning()
        if (running != null) {
            notifier.showRunning(Combo(running.activity, running.schoolClass).label(generalName()), running.entry.startUtc)
            return
        }
        val paused = preferences.getPaused()
        val combo = paused?.let { catalog.getCombo(it.key) }
        if (paused != null && combo != null) {
            notifier.showPaused(combo.label(generalName()), paused.pausedAt)
        } else {
            notifier.cancel()
        }
    }
}

/** The home-screen widgets, which show the same state as the notification. */
fun interface WidgetUpdater {
    suspend fun update()
}
