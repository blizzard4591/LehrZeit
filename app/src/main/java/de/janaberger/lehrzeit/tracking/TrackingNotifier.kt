// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.janaberger.lehrzeit.MainActivity
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.time.formatTimeOfDay

/**
 * The single tracking notification. While running it shows a chronometer that the system
 * counts up from the start time, so the app doesn't need to run in the background.
 */
class TrackingNotifier(private val context: Context) : TrackingNotifications {
    private val manager = NotificationManagerCompat.from(context)

    /**
     * Creates or updates the channels; called at every app start so their names follow the language.
     * Default importance (with sound and vibration off) instead of low: low-importance notifications
     * get no status-bar icon on current Android versions.
     */
    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_TRACKING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_tracking_name))
                .setDescription(context.getString(R.string.channel_tracking_description))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .setShowBadge(false)
                .build(),
        )
        // Reminders may make a sound: they are meant to be noticed.
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.channel_reminders_name))
                .setDescription(context.getString(R.string.channel_reminders_description))
                .build(),
        )
    }

    override fun showRunning(label: String, startUtc: Long) {
        post(
            baseBuilder()
                .setContentTitle(label)
                .setContentText(context.getString(R.string.since_time, formatTimeOfDay(startUtc, locale())))
                .setWhen(startUtc)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .addAction(R.drawable.ic_pause, context.getString(R.string.action_pause), broadcast(ACTION_PAUSE))
                .addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), broadcast(ACTION_STOP))
                .build(),
        )
    }

    override fun showPaused(label: String, pausedAt: Long) {
        post(
            baseBuilder()
                .setContentTitle(label)
                .setContentText(context.getString(R.string.paused_at, formatTimeOfDay(pausedAt, locale())))
                .setShowWhen(false)
                .addAction(R.drawable.ic_play, context.getString(R.string.action_resume), broadcast(ACTION_RESUME))
                .build(),
        )
    }

    override fun cancel() {
        manager.cancel(NOTIFICATION_ID)
    }

    /** "Still on 7b › Correcting?" with Stop and Keep going; swiping it away counts as Keep going. */
    override fun showReminder(label: String, startUtc: Long, now: Long) {
        post(
            REMINDER_ID,
            NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                // Own group, so the system doesn't bundle it with the timer and hide its buttons.
                .setGroup(GROUP_REMINDER)
                .setContentTitle(context.getString(R.string.reminder_title, label))
                .setContentText(
                    context.getString(R.string.reminder_text, formatTimeOfDay(startUtc, locale()), formatHoursMinutes(now - startUtc)),
                )
                .setContentIntent(openAppIntent())
                .setDeleteIntent(broadcast(ACTION_REMINDER_DISMISSED))
                .setAutoCancel(true)
                .addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), broadcast(ACTION_STOP))
                .addAction(R.drawable.ic_play, context.getString(R.string.action_keep_going), broadcast(ACTION_KEEP_GOING))
                .build(),
        )
    }

    override fun cancelReminder() {
        manager.cancel(REMINDER_ID)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setGroup(GROUP_TRACKING)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(broadcast(ACTION_DISMISSED))

    private fun post(notification: Notification) = post(NOTIFICATION_ID, notification)

    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        manager.notify(id, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, TrackingActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun locale() = context.resources.configuration.locales[0]

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_REMINDERS = "reminders"
        private const val NOTIFICATION_ID = 1
        private const val REMINDER_ID = 2
        private const val GROUP_TRACKING = "de.janaberger.lehrzeit.TRACKING"
        private const val GROUP_REMINDER = "de.janaberger.lehrzeit.REMINDER"

        const val ACTION_STOP = "de.janaberger.lehrzeit.action.STOP"
        const val ACTION_PAUSE = "de.janaberger.lehrzeit.action.PAUSE"
        const val ACTION_RESUME = "de.janaberger.lehrzeit.action.RESUME"
        const val ACTION_DISMISSED = "de.janaberger.lehrzeit.action.DISMISSED"
        const val ACTION_REMINDER_DUE = "de.janaberger.lehrzeit.action.REMINDER_DUE"
        const val ACTION_KEEP_GOING = "de.janaberger.lehrzeit.action.KEEP_GOING"
        const val ACTION_REMINDER_DISMISSED = "de.janaberger.lehrzeit.action.REMINDER_DISMISSED"

        /** False if the user turned off notifications for the app or for the tracking channel. */
        fun areEnabled(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            val channel = manager.getNotificationChannelCompat(CHANNEL_TRACKING)
            return manager.areNotificationsEnabled() && channel?.importance != NotificationManagerCompat.IMPORTANCE_NONE
        }
    }
}
