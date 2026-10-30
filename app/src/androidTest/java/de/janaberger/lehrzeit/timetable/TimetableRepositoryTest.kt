// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class TimetableRepositoryTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val link = "https://school.webuntis.com/WebUntis/ical_export?token=secret"
    private lateinit var db: LehrZeitDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var timetable: TimetableRepository

    /** What the next download returns. */
    private var download: () -> String = { error("not set") }

    private val scheduled = mutableListOf<String>()

    private val scheduler = object : TimetableScheduler {
        override fun updateSoon() { scheduled += "soon" }
        override fun planAt(atUtc: Long, mode: PlanMode) {
            scheduled += "plan ${Instant.ofEpochMilli(atUtc).atZone(berlin).toLocalDateTime()} $mode"
        }
        override fun cancelPlanned() { scheduled += "cancelPlanned" }
        override fun cancel() { scheduled += "cancel" }
    }

    /** The update times, as the settings would provide them. */
    private var schedules: Flow<TimetableSchedule> = flowOf(TimetableSchedule())

    private var now = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LehrZeitDatabase::class.java).build()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "timetable-test-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file })
        timetable = TimetableRepository(
            store,
            db,
            fetcher = { download() },
            scheduler = scheduler,
            schedule = flow { emitAll(schedules) },
            clock = { now },
            zone = { berlin },
        )
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        db.close()
    }

    private fun utc(dateTime: String) = LocalDateTime.parse(dateTime).atZone(berlin).toInstant().toEpochMilli()

    private fun ics(dateTime: LocalDateTime) = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))

    /** A 45-minute lesson starting at [start], local time. */
    private fun lesson(uid: String, start: String): String {
        val begin = LocalDateTime.parse(start)
        return """
            BEGIN:VEVENT
            UID:$uid
            DTSTART;TZID=Europe/Berlin:${ics(begin)}
            DTEND;TZID=Europe/Berlin:${ics(begin.plusMinutes(45))}
            SUMMARY:BI
            DESCRIPTION:07.6 XYZ
            LOCATION:U23
            END:VEVENT
        """.trimIndent()
    }

    private fun calendar(vararg lessons: String) = (listOf("BEGIN:VCALENDAR") + lessons + "END:VCALENDAR").joinToString("\n")

    private suspend fun stored() = db.lessonDao().getAll().map { it.uid to it.startUtc }

    @Test
    fun updates_keepPastLessons_andReplaceTodayAndLater() = runTest {
        now = utc("2026-11-02T06:00") // Monday
        timetable.setUrl(link)
        assertEquals(listOf("soon", "plan 2026-11-02T07:00 REPLACE"), scheduled)

        // Monday: Monday's and Tuesday's lessons.
        download = { calendar(lesson("mon", "2026-11-02T08:00"), lesson("tue", "2026-11-03T08:00")) }
        assertNull(timetable.update(now = utc("2026-11-02T06:00")))

        // Tuesday: Monday has dropped out, Tuesday's lesson moved, Wednesday's is new.
        download = { calendar(lesson("tue", "2026-11-03T09:40"), lesson("wed", "2026-11-04T08:00")) }
        assertNull(timetable.update(now = utc("2026-11-03T06:00")))
        assertEquals(
            listOf("mon" to utc("2026-11-02T08:00"), "tue" to utc("2026-11-03T09:40"), "wed" to utc("2026-11-04T08:00")),
            stored(),
        )

        // Later on Tuesday, Wednesday's lesson is gone, e.g. cancelled.
        download = { calendar(lesson("tue", "2026-11-03T09:40")) }
        timetable.update(now = utc("2026-11-03T12:00"))
        assertEquals(listOf("mon", "tue"), stored().map { it.first })

        val status = timetable.status.first()
        assertEquals(utc("2026-11-03T12:00"), status.lastSuccessAt)
        assertEquals(2, status.lessons.count)
        assertNull(status.problem)
    }

    @Test
    fun failedUpdates_keepTheLessons_andRecordTheProblem() = runTest {
        timetable.setUrl(link)
        download = { calendar(lesson("mon", "2026-11-02T08:00")) }
        timetable.update(now = utc("2026-11-02T06:00"))

        download = { throw IOException("offline") }
        assertEquals(TimetableProblem.NETWORK, timetable.update(now = utc("2026-11-03T06:00")))
        download = { "<html>Login</html>" }
        assertEquals(TimetableProblem.NOT_CALENDAR, timetable.update(now = utc("2026-11-03T07:00")))
        download = { throw TimetableDownloadException(TimetableProblem.HTTP, httpCode = 401) }
        assertEquals(TimetableProblem.HTTP, timetable.update(now = utc("2026-11-03T08:00")))

        val status = timetable.status.first()
        assertEquals(listOf("mon"), stored().map { it.first })
        assertEquals(utc("2026-11-02T06:00"), status.lastSuccessAt)
        assertEquals(TimetableProblem.HTTP, status.problem)
        assertEquals(401, status.httpCode)
        assertEquals(utc("2026-11-03T08:00"), status.problemAt)
    }

    @Test
    fun onlyTheNextUpdateIsPlanned_andEachPlansTheOneAfterIt() = runTest {
        now = utc("2026-11-02T07:30") // Monday
        timetable.setUrl(link)
        assertEquals("plan 2026-11-02T07:55 REPLACE", scheduled.last())

        // The 07:55 update, a bit late, plans the next one without cancelling itself.
        now = utc("2026-11-02T07:58")
        timetable.planNextAfterRun()
        assertEquals("plan 2026-11-02T08:30 AFTER_RUNNING", scheduled.last())
        // After Friday's last update comes Monday's first.
        now = utc("2026-11-06T17:01")
        timetable.planNextAfterRun()
        assertEquals("plan 2026-11-09T07:00 AFTER_RUNNING", scheduled.last())

        // At app start, the planned update stays; when the times change, it's replaced.
        now = utc("2026-11-09T06:00")
        schedules = flowOf(TimetableSchedule(), TimetableSchedule(times = listOf(LocalTime.of(9, 0))))
        timetable.keepPlanned()
        assertEquals(listOf("plan 2026-11-09T07:00 KEEP", "plan 2026-11-09T09:00 REPLACE"), scheduled.takeLast(2))

        // Without times, nothing is planned any more; a running update just ends.
        schedules = flowOf(TimetableSchedule(times = emptyList()))
        timetable.keepPlanned()
        assertEquals("cancelPlanned", scheduled.last())
        val before = scheduled.size
        timetable.planNextAfterRun()
        assertEquals(before, scheduled.size)
    }

    @Test
    fun removingTheLink_deletesTheLessons_andStopsTheUpdates() = runTest {
        timetable.setUrl(link)
        download = { calendar(lesson("mon", "2026-11-02T08:00")) }
        timetable.update(now = utc("2026-11-02T06:00"))

        timetable.removeUrl()

        assertEquals("cancel", scheduled.last())
        assertEquals(TimetableStatus(), timetable.status.first())
        assertNull("nothing to download", timetable.update(now = utc("2026-11-03T06:00")))
        assertEquals(emptyList<Pair<String, Long>>(), stored())
    }
}
