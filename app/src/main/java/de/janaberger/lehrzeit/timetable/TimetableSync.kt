// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.janaberger.lehrzeit.LehrZeitApplication
import java.util.concurrent.TimeUnit

/** Downloads the timetable link; see [TimetableRepository.update]. */
class TimetableSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val timetable = (applicationContext as LehrZeitApplication).container.timetable
        val problem = timetable.update(System.currentTimeMillis())
        // A lost connection is often brief; any other problem would be the same a minute later.
        if (problem == TimetableProblem.NETWORK && runAttemptCount < MAX_RETRIES) return Result.retry()
        if (inputData.getBoolean(KEY_PLANNED, false)) timetable.planNextAfterRun()
        return Result.success()
    }

    companion object {
        /** Set for the updates at the chosen times, as opposed to "Update". */
        const val KEY_PLANNED = "planned"
        private const val MAX_RETRIES = 3
    }
}

/**
 * Both kinds of update wait for a network connection. A planned one never starts before its time,
 * but Android may start it later, e.g. while the phone has been lying unused for a while.
 */
class WorkManagerTimetableScheduler(private val context: Context) : TimetableScheduler {
    private val workManager get() = WorkManager.getInstance(context)
    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun updateSoon() {
        val request = OneTimeWorkRequestBuilder<TimetableSyncWorker>().setConstraints(needsNetwork).build()
        workManager.enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    override fun planAt(atUtc: Long, mode: PlanMode) {
        val request = OneTimeWorkRequestBuilder<TimetableSyncWorker>()
            .setConstraints(needsNetwork)
            .setInitialDelay((atUtc - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(TimetableSyncWorker.KEY_PLANNED to true))
            .build()
        val policy = when (mode) {
            PlanMode.REPLACE -> ExistingWorkPolicy.REPLACE
            PlanMode.KEEP -> ExistingWorkPolicy.KEEP
            // Starts counting the delay once the running update has finished, a moment later.
            PlanMode.AFTER_RUNNING -> ExistingWorkPolicy.APPEND_OR_REPLACE
        }
        workManager.enqueueUniqueWork(WORK_PLANNED, policy, request)
        workManager.cancelUniqueWork(WORK_DAILY_UNTIL_0_2)
    }

    override fun cancelPlanned() {
        workManager.cancelUniqueWork(WORK_PLANNED)
        workManager.cancelUniqueWork(WORK_DAILY_UNTIL_0_2)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NOW)
        cancelPlanned()
    }

    private companion object {
        const val WORK_NOW = "timetable-now"
        const val WORK_PLANNED = "timetable-planned"

        /** Up to version 0.2.0, a periodic job downloaded the timetable once a day. */
        const val WORK_DAILY_UNTIL_0_2 = "timetable-daily"
    }
}
