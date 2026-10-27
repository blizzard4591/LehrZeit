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
import de.janaberger.lehrzeit.data.db.WorkActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Debug builds only. Fills the three weeks before today, and today up to now, with a plausible
 * teacher's week, for trying out the worklog and stats and for screenshots:
 *
 *     adb shell am broadcast -n de.janaberger.lehrzeit.debug/de.janaberger.lehrzeit.debug.DemoDataReceiver
 *
 * Uses the first three activities of the catalog (with the starter catalog: teaching, lesson prep
 * and correcting), General and the first two classes.
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
        val activities = snapshot.activeActivities.filter { !it.isGeneral }
        val teaching = activities.getOrElse(0) { general }
        val prep = activities.getOrElse(1) { general }
        val correcting = activities.getOrElse(2) { general }
        val classes = snapshot.activeClasses
        fun classFor(activity: WorkActivity, index: Int): Long? = if (activity.perClass) classes.getOrNull(index)?.id else null

        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var count = 0
        suspend fun add(activity: WorkActivity, classIndex: Int?, day: LocalDate, start: String, end: String, note: String = "") {
            fun utc(time: String) = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()
            if (utc(end) > now) return
            val classId = classIndex?.let { classFor(activity, it) }
            container.entries.save(
                Entry(activityId = activity.id, classId = classId, startUtc = utc(start), endUtc = utc(end), note = note, createdAt = now, updatedAt = now),
                now,
            )
            count++
        }

        val today = LocalDate.now(zone)
        // Screenshots are taken in both languages.
        val examNote = if (Locale.getDefault().language == "de") "Klassenarbeit" else "Class test"
        for (offset in 0L..21L) {
            val day = today.minusDays(offset)
            val (morning, late) = if (offset % 2 == 1L) 0 to 1 else 1 to 0
            when (day.dayOfWeek) {
                DayOfWeek.SUNDAY -> if (offset % 2 == 1L) add(correcting, 0, day, "19:00", "21:15", examNote)
                DayOfWeek.SATURDAY -> add(prep, 1, day, "10:00", "12:30")
                else -> {
                    add(teaching, morning, day, "08:00", "11:15")
                    add(teaching, late, day, "11:30", "13:00")
                    add(general, null, day, "13:00", "13:45")
                    // Lesson prep without a class, the way the first tester books it.
                    add(prep, null, day, "15:00", if (offset % 3 == 0L) "17:30" else "16:45")
                    if (day.dayOfWeek == DayOfWeek.TUESDAY || day.dayOfWeek == DayOfWeek.THURSDAY) {
                        add(correcting, 1, day, "19:30", "21:00")
                    }
                }
            }
        }
        return count
    }
}
