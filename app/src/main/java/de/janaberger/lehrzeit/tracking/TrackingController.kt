// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.tracking

import de.janaberger.lehrzeit.data.AppPreferences
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.PausedState
import de.janaberger.lehrzeit.data.TrackingRepository
import de.janaberger.lehrzeit.data.comboKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one place that changes the tracking state, used by the UI and by the notification buttons.
 * Every change updates the notification. Changes run to completion even if the caller is cancelled,
 * so the database and the notification can't drift apart.
 */
class TrackingController(
    private val tracking: TrackingRepository,
    private val catalog: CatalogRepository,
    private val preferences: AppPreferences,
    private val notifier: TrackingNotifier,
    private val generalName: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** Starts [key]; a running entry for another combination ends at the same instant. */
    suspend fun start(key: ComboKey) = change {
        preferences.setPaused(null)
        tracking.start(key, now())
    }

    suspend fun stop() = change {
        preferences.setPaused(null)
        tracking.stop(now())
    }

    suspend fun pause() = change {
        val finished = tracking.stop(now()) ?: return@change
        preferences.setPaused(PausedState(finished.comboKey, now()))
    }

    suspend fun resume() = change {
        val paused = preferences.getPaused() ?: return@change
        preferences.setPaused(null)
        tracking.start(paused.key, now())
    }

    /** Forgets the paused combination. */
    suspend fun dismissPaused() = change {
        preferences.setPaused(null)
    }

    suspend fun adjustRunningStart(deltaMillis: Long) = change {
        tracking.adjustRunningStart(deltaMillis, now())
    }

    /**
     * The user swiped the notification away. A running timer's notification comes straight back;
     * a paused one stays away, and the pause is forgotten.
     */
    suspend fun onNotificationDismissed() = change {
        if (tracking.getRunning() == null) preferences.setPaused(null)
    }

    /** Makes the notification match the stored state, e.g. after a reboot or an app update. */
    suspend fun refreshNotification() = change {}

    private suspend fun change(block: suspend () -> Unit) = withContext(NonCancellable) {
        mutex.withLock {
            block()
            updateNotification()
        }
    }

    private suspend fun updateNotification() {
        val running = tracking.getRunning()
        if (running != null) {
            notifier.showRunning(Combo(running.account, running.activity).label(generalName()), running.entry.startUtc)
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
