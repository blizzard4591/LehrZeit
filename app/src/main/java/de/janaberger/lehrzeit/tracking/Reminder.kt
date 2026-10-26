// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.ReminderSettings
import de.janaberger.lehrzeit.data.ReminderState

/**
 * When the "still working?" reminder for the running entry is due, or null if none is pending:
 * reminders are off, or this one was already shown and nobody tapped "Keep going" since.
 */
fun nextReminderAt(entryStartUtc: Long, state: ReminderState, settings: ReminderSettings): Long? {
    if (!settings.enabled || state.fired) return null
    return (state.anchorUtc ?: entryStartUtc) + settings.hours * 3_600_000L
}

/** What the tracking controller shows. [TrackingNotifier] implements it; tests use a fake. */
interface TrackingNotifications {
    fun showRunning(label: String, startUtc: Long)
    fun showPaused(label: String, pausedAt: Long)
    fun cancel()
    fun showReminder(label: String, startUtc: Long, now: Long)
    fun cancelReminder()
}

/** Wakes the app when a reminder is due. [AlarmReminderScheduler] implements it; tests use a fake. */
interface ReminderScheduler {
    /** Replaces any earlier schedule. */
    fun schedule(atUtc: Long)
    fun cancel()
}
