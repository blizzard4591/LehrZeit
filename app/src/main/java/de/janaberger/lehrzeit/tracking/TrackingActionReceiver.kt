// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import de.janaberger.lehrzeit.LehrZeitApplication
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.ui.components.minLengthText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Handles the buttons of the notifications and the widget, dismissed notifications, and the reminder alarm, without opening the app. */
class TrackingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LehrZeitApplication).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                val controller = container.trackingController
                when (intent.action) {
                    // The widget sends this only while nothing runs. If a timer was started in the
                    // meantime, nothing happens; the updated widget then asks before switching.
                    TrackingNotifier.ACTION_START -> intent.getComboKey()?.let { controller.startIfIdle(it) }
                    TrackingNotifier.ACTION_STOP -> if (controller.stop()) showDiscardedToast(context, container.preferences)
                    TrackingNotifier.ACTION_PAUSE -> if (controller.pause()) showDiscardedToast(context, container.preferences)
                    TrackingNotifier.ACTION_RESUME -> controller.resume()
                    TrackingNotifier.ACTION_DISMISSED -> controller.onNotificationDismissed()
                    TrackingNotifier.ACTION_REMINDER_DUE -> controller.onReminderDue()
                    TrackingNotifier.ACTION_KEEP_GOING, TrackingNotifier.ACTION_REMINDER_DISMISSED -> controller.keepGoing()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores the notification and the widgets after a reboot or an app update; the running entry lives in the database. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as LehrZeitApplication).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                container.trackingController.refreshNotification()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val EXTRA_ACCOUNT_ID = "de.janaberger.lehrzeit.extra.ACCOUNT_ID"
private const val EXTRA_ACTIVITY_ID = "de.janaberger.lehrzeit.extra.ACTIVITY_ID"

/**
 * Adds [key] as extras. The data URI only makes each combination's intent distinct, so that their
 * PendingIntents don't replace each other.
 */
fun Intent.putComboKey(key: ComboKey): Intent = apply {
    data = "lehrzeit:combo/${key.accountId}/${key.activityId ?: "-"}".toUri()
    putExtra(EXTRA_ACCOUNT_ID, key.accountId)
    key.activityId?.let { putExtra(EXTRA_ACTIVITY_ID, it) }
}

fun Intent.getComboKey(): ComboKey? {
    if (!hasExtra(EXTRA_ACCOUNT_ID)) return null
    val activityId = if (hasExtra(EXTRA_ACTIVITY_ID)) getLongExtra(EXTRA_ACTIVITY_ID, 0) else null
    return ComboKey(getLongExtra(EXTRA_ACCOUNT_ID, 0), activityId)
}

/** Outside the app there is no snackbar, so a short toast says that a too-short entry was dropped. */
suspend fun showDiscardedToast(context: Context, preferences: AppPreferences) {
    val text = context.getString(R.string.discarded_short, minLengthText(context.resources, preferences.shortEntries.first().minSeconds))
    withContext(Dispatchers.Main) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
