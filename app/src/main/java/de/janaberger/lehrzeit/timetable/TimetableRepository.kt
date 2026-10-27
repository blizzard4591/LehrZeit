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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
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

/** Runs the timetable download: soon after a link was entered or "Update now" was tapped, and daily. */
interface TimetableScheduler {
    fun updateSoon()
    fun scheduleDaily()
    fun cancel()
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

    /** Stores [url] (see [normalizeTimetableUrl]) and downloads it soon, then daily. */
    suspend fun setUrl(url: String) {
        store.edit { prefs ->
            prefs.clear()
            prefs[urlKey] = url
        }
        scheduler.updateSoon()
        scheduler.scheduleDaily()
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

    /** Makes sure the daily update is scheduled, e.g. at app start. */
    suspend fun ensureScheduled() {
        if (getUrl() != null) scheduler.scheduleDaily()
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
