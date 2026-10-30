// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import de.janaberger.lehrzeit.data.db.LehrZeitDatabase
import de.janaberger.lehrzeit.data.db.Lesson
import de.janaberger.lehrzeit.data.db.LessonSummary
import de.janaberger.lehrzeit.time.dayBounds
import de.janaberger.lehrzeit.time.localDateOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * Its own file, because the link contains a secret token: res/xml/backup_rules.xml and
 * data_extraction_rules.xml keep it out of Android's backup, and LehrZeit's backup file leaves it out too.
 */
val Context.timetableDataStore: DataStore<Preferences> by preferencesDataStore(name = "timetable")

data class TimetableStatus(
    /** Null while no link is set. */
    val url: String? = null,
    val lastSuccessAt: Long? = null,
    /** Set while the last attempt failed, with the time of that attempt. */
    val problem: TimetableProblem? = null,
    val httpCode: Int? = null,
    val problemAt: Long? = null,
    val lessons: LessonSummary = LessonSummary(count = 0, firstStart = null, lastStart = null),
    val isUpdating: Boolean = false,
)

/**
 * Runs the timetable download: soon after a link was entered or "Update" was tapped, and at the
 * times of the [TimetableSchedule]. Only the next automatic update is planned; when it has run, it
 * plans the one after it.
 */
interface TimetableScheduler {
    fun updateSoon()

    /** Plans the next automatic update for [atUtc]. */
    fun planAt(atUtc: Long, mode: PlanMode)

    fun cancelPlanned()

    /** Both the soon and the planned update. */
    fun cancel()
}

enum class PlanMode {
    /** Instead of the planned update, e.g. after the times changed. */
    REPLACE,

    /** Only if none is planned. At app start, the planned update may be the one that started the app. */
    KEEP,

    /** From within the planned update while it runs, which [REPLACE] would cancel. */
    AFTER_RUNNING,
}

/**
 * The optional timetable link (iCal, e.g. from WebUntis) and the lessons downloaded from it.
 *
 * WebUntis only provides the coming weeks, so each update replaces the stored lessons from today
 * on (or from the file's first day, if earlier) and keeps the older ones.
 */
class TimetableRepository(
    private val store: DataStore<Preferences>,
    private val db: LehrZeitDatabase,
    private val fetcher: TimetableFetcher,
    private val scheduler: TimetableScheduler,
    private val schedule: Flow<TimetableSchedule>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val lessons = db.lessonDao()
    private val mutex = Mutex()
    private val updating = MutableStateFlow(false)

    private val urlKey = stringPreferencesKey("url")
    private val lastSuccessKey = longPreferencesKey("last_success_at")
    private val problemKey = stringPreferencesKey("problem")
    private val httpCodeKey = intPreferencesKey("problem_http_code")
    private val problemAtKey = longPreferencesKey("problem_at")

    val status: Flow<TimetableStatus> = combine(store.data, lessons.observeSummary(), updating) { prefs, summary, isUpdating ->
        TimetableStatus(
            url = prefs[urlKey],
            lastSuccessAt = prefs[lastSuccessKey],
            problem = prefs[problemKey]?.let { name -> TimetableProblem.entries.firstOrNull { it.name == name } },
            httpCode = prefs[httpCodeKey],
            problemAt = prefs[problemAtKey],
            lessons = summary,
            isUpdating = isUpdating,
        )
    }

    suspend fun getUrl(): String? = store.data.first()[urlKey]

    /** Stores [url] (see [normalizeTimetableUrl]) and downloads it soon, then at the set times. */
    suspend fun setUrl(url: String) {
        store.edit { prefs ->
            prefs.clear()
            prefs[urlKey] = url
        }
        scheduler.updateSoon()
        planNext(schedule.first(), PlanMode.REPLACE)
    }

    /** Forgets the link and deletes the stored lessons, even if the caller is cancelled meanwhile. */
    suspend fun removeUrl() = withContext(NonCancellable) {
        scheduler.cancel()
        // Waits for a running update, so it can't store lessons afterwards.
        mutex.withLock {
            store.edit { it.clear() }
            lessons.deleteAll()
        }
    }

    fun updateSoon() = scheduler.updateSoon()

    /**
     * Runs while the app does: at the start, makes sure an update is planned, and plans anew
     * whenever the times change, in Settings or by restoring a backup.
     */
    suspend fun keepPlanned() {
        var first = true
        schedule.distinctUntilChanged().collect { current ->
            planNext(current, if (first) PlanMode.KEEP else PlanMode.REPLACE)
            first = false
        }
    }

    /** Called by the planned update when it's done. */
    suspend fun planNextAfterRun() = planNext(schedule.first(), PlanMode.AFTER_RUNNING)

    private suspend fun planNext(current: TimetableSchedule, mode: PlanMode) {
        val next = if (getUrl() == null) null else current.nextAfter(Instant.ofEpochMilli(clock()), zone())
        when {
            next != null -> scheduler.planAt(next.toEpochMilli(), mode)
            // The running update is the only one planned, and cancelling would cancel it.
            mode != PlanMode.AFTER_RUNNING -> scheduler.cancelPlanned()
        }
    }

    /**
     * Downloads the link and stores its lessons. Returns the problem if that failed; the stored
     * lessons then stay as they were. Does nothing without a link.
     */
    suspend fun update(now: Long): TimetableProblem? = mutex.withLock {
        val url = getUrl() ?: return@withLock null
        updating.value = true
        try {
            val failure = try {
                val events = parseIcs(fetcher.fetch(url), zone()) ?: throw TimetableDownloadException(TimetableProblem.NOT_CALENDAR)
                if (getUrl() != url) return@withLock null // changed in the meantime
                storeLessons(events, now)
                null
            } catch (e: TimetableDownloadException) {
                e
            } catch (e: IOException) {
                TimetableDownloadException(TimetableProblem.NETWORK)
            }
            store.edit { prefs ->
                if (prefs[urlKey] != url) return@edit
                if (failure == null) {
                    prefs[lastSuccessKey] = now
                    prefs.remove(problemKey)
                    prefs.remove(httpCodeKey)
                    prefs.remove(problemAtKey)
                } else {
                    prefs[problemKey] = failure.problem.name
                    val code = failure.httpCode
                    if (code != null) prefs[httpCodeKey] = code else prefs.remove(httpCodeKey)
                    prefs[problemAtKey] = now
                }
            }
            failure?.problem
        } finally {
            updating.value = false
        }
    }

    private suspend fun storeLessons(events: List<IcsEvent>, now: Long) {
        val zone = zone()
        fun dayStart(utc: Long) = dayBounds(localDateOf(utc, zone), zone).startUtc
        val from = minOf(dayStart(now), events.minOfOrNull { dayStart(it.startUtc) } ?: Long.MAX_VALUE)
        db.withTransaction {
            lessons.deleteFrom(from)
            lessons.insertAll(events.map { Lesson(it.uid, it.startUtc, it.endUtc, it.summary, it.description, it.location) })
        }
    }
}
