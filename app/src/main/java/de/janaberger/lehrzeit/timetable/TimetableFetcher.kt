// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.timetable

import de.janaberger.lehrzeit.data.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/** Far more than a school year's timetable; protects against huge downloads. */
const val MAX_TIMETABLE_BYTES = 5 * 1024 * 1024

enum class TimetableProblem { NETWORK, HTTP, NOT_CALENDAR, TOO_LARGE }

class TimetableDownloadException(val problem: TimetableProblem, val httpCode: Int? = null) : Exception(problem.name)

/** Downloads the text behind a timetable link. */
fun interface TimetableFetcher {
    /** Throws [TimetableDownloadException], or an IOException if there is no connection. */
    suspend fun fetch(url: String): String
}

/** Plain [HttpURLConnection], so the app needs no networking library. */
class HttpTimetableFetcher : TimetableFetcher {
    override suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "LehrZeit")
            connection.setRequestProperty("Accept", "text/calendar, */*;q=0.5")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw TimetableDownloadException(TimetableProblem.HTTP, code)
            val bytes = connection.inputStream.use { it.readAtMost(MAX_TIMETABLE_BYTES) }
            if (bytes.size > MAX_TIMETABLE_BYTES) throw TimetableDownloadException(TimetableProblem.TOO_LARGE)
            // iCalendar files are UTF-8.
            bytes.decodeToString()
        } finally {
            connection.disconnect()
        }
    }
}
