// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.janaberger.lehrzeit.LehrZeitApplication
import kotlinx.coroutines.launch

/** Handles the notification's buttons and its dismissal, without opening the app. */
class TrackingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LehrZeitApplication).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                val controller = container.trackingController
                when (intent.action) {
                    TrackingNotifier.ACTION_STOP -> controller.stop()
                    TrackingNotifier.ACTION_PAUSE -> controller.pause()
                    TrackingNotifier.ACTION_RESUME -> controller.resume()
                    TrackingNotifier.ACTION_DISMISSED -> controller.onNotificationDismissed()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/** Restores the notification after a reboot or an app update; the running entry lives in the database. */
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
