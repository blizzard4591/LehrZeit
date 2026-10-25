// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.janaberger.lehrzeit.AppContainer
import de.janaberger.lehrzeit.LehrZeitApplication
import de.janaberger.lehrzeit.data.db.Entry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Debug builds only. Fills the three weeks before today with a plausible teacher's week, for
 * trying out the worklog and stats and for screenshots:
 *
 *     adb shell am broadcast -n de.janaberger.lehrzeit/.debug.DemoDataReceiver
 *
 * Uses the first two active accounts and the first three activities of the catalog.
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LehrZeitApplication).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                val count = addDemoWeeks(container)
                Log.i("LehrZeit", "Demo data: added $count entries")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun addDemoWeeks(container: AppContainer): Int {
        val snapshot = container.catalog.observeSnapshot().first()
        val general = snapshot.general ?: return 0
        val accounts = snapshot.activeAccounts.filter { !it.isGeneral }.ifEmpty { listOf(general) }
        val first = accounts.first()
        val second = accounts.getOrElse(1) { first }
        val activities = snapshot.activities.filter { !it.archived }
        // General offers no activities, so without other accounts everything goes there plain.
        fun activity(index: Int): Long? = if (first.isGeneral) null else activities.getOrNull(index)?.id

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var count = 0
        suspend fun add(accountId: Long, activityId: Long?, day: LocalDate, start: String, end: String, note: String = "") {
            fun utc(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
            container.entries.save(
                Entry(accountId = accountId, activityId = activityId, startUtc = utc(start), endUtc = utc(end), note = note, createdAt = now, updatedAt = now),
                now,
            )
            count++
        }

        val today = LocalDate.now(zone)
        for (offset in 1L..21L) {
            val day = today.minusDays(offset)
            val (morning, late) = if (offset % 2 == 1L) first to second else second to first
            when (day.dayOfWeek) {
                DayOfWeek.SUNDAY -> if (offset % 2 == 1L) add(first.id, activity(2), day, "19:00", "21:15", "Klausur")
                DayOfWeek.SATURDAY -> add(second.id, activity(1), day, "10:00", "12:30")
                else -> {
                    add(morning.id, activity(0), day, "08:00", "11:15")
                    add(late.id, activity(0), day, "11:30", "13:00")
                    add(general.id, null, day, "13:00", "13:45")
                    add(second.id, activity(1), day, "15:00", if (offset % 3 == 0L) "17:30" else "16:45")
                    if (day.dayOfWeek == DayOfWeek.TUESDAY || day.dayOfWeek == DayOfWeek.THURSDAY) {
                        add(first.id, activity(2), day, "19:30", "21:00")
                    }
                }
            }
        }
        return count
    }
}
