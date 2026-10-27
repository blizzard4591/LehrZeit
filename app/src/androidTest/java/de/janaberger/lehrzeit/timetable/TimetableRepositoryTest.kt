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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
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
        override fun scheduleDaily() { scheduled += "daily" }
        override fun cancel() { scheduled += "cancel" }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LehrZeitDatabase::class.java).build()
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(context.cacheDir, "timetable-test-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { file })
        timetable = TimetableRepository(store, db, fetcher = { download() }, scheduler = scheduler, zone = { berlin })
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
        timetable.setUrl(link)
        assertEquals(listOf("soon", "daily"), scheduled)

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
