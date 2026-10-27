// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.janaberger.lehrzeit.LehrZeitApplication
import java.util.concurrent.TimeUnit

/** Downloads the timetable link; see [TimetableRepository.update]. */
class TimetableSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val timetable = (applicationContext as LehrZeitApplication).container.timetable
        val problem = timetable.update(System.currentTimeMillis())
        // A lost connection is often brief; any other problem would be the same a minute later.
        return if (problem == TimetableProblem.NETWORK && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}

/** Both runs wait for a network connection. */
class WorkManagerTimetableScheduler(private val context: Context) : TimetableScheduler {
    private val workManager get() = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun updateSoon() {
        val request = OneTimeWorkRequestBuilder<TimetableSyncWorker>().setConstraints(needsNetwork).build()
        workManager.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /** Keeps an existing schedule; the first daily run is a day after the link was entered. */
    override fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<TimetableSyncWorker>(1, TimeUnit.DAYS)
            .setConstraints(needsNetwork)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NOW)
        workManager.cancelUniqueWork(WORK_DAILY)
    }

    private companion object {
        const val WORK_NOW = "timetable-now"
        const val WORK_DAILY = "timetable-daily"
    }
}
