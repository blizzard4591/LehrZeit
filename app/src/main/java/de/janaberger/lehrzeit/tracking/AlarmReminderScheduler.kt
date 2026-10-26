// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Schedules the reminder with AlarmManager, needing no special permission.
 *
 * A plain inexact alarm may be delivered as late as 75 % of the waiting time (over two hours for a
 * three-hour reminder), so a fixed window of [WINDOW_MILLIS] is used instead. While the phone sleeps
 * deeply (Doze), Android still holds the alarm back, but it fires as soon as the phone is used again.
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(atUtc: Long) {
        alarms.setWindow(AlarmManager.RTC_WAKEUP, atUtc, WINDOW_MILLIS, pendingIntent())
    }

    override fun cancel() {
        alarms.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, TrackingActionReceiver::class.java).setAction(TrackingNotifier.ACTION_REMINDER_DUE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val REQUEST_CODE = 100

        /** The shortest window Android allows without the exact-alarm permission. */
        const val WINDOW_MILLIS = 10 * 60_000L
    }
}
