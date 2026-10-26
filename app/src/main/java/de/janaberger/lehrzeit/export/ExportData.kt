// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.comboKey
import de.janaberger.lehrzeit.data.db.Entry
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class ExportFormat(val mimeType: String, val extension: String) {
    CSV("text/csv", "csv"),
    ODS("application/vnd.oasis.opendocument.spreadsheet", "ods"),
    PDF("application/pdf", "pdf"),
}

/** One finished entry as it appears in an export; [endDate] differs from [date] for entries over midnight. */
data class ExportEntry(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val endDate: LocalDate,
    val durationMillis: Long,
    val account: String,
    /** Null when booked on the account alone. */
    val activity: String?,
    val note: String,
)

/** A labelled total; accounts carry their activities as [children]. */
data class ExportTotal(val name: String, val millis: Long, val children: List<ExportTotal> = emptyList())

/**
 * Everything an exporter needs, already resolved to display names. Entries belong to the day
 * they start on and are never split, so the rows always add up to [totalMillis].
 */
data class ExportData(
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val entries: List<ExportEntry>,
    val totalMillis: Long,
    /** Largest first. */
    val byAccount: List<ExportTotal>,
    /** Largest first; entries without an activity are listed as "without activity". */
    val byActivity: List<ExportTotal>,
    /** Only days with time on them, in order. */
    val perDay: List<Pair<LocalDate, Long>>,
)

/** Column headings and titles in the user's language. */
data class ExportLabels(
    val date: String,
    val start: String,
    val end: String,
    val duration: String,
    val hours: String,
    val account: String,
    val activity: String,
    val note: String,
    val entriesTitle: String,
    val summaryTitle: String,
    val period: String,
    val total: String,
    val byAccount: String,
    val byActivity: String,
    val perDay: String,
    val withoutActivity: String,
    /** PDF only. */
    val reportTitle: String,
    val time: String,
    val bookedOn: String,
    /** Format string with the page number and the page count. */
    val pageOf: String,
    /** Format string with the date. */
    val createdOn: String,
)

/** Builds the export for [firstDay]..[lastDay] from finished [entries] that start in that range. */
fun buildExportData(
    entries: List<Entry>,
    snapshot: CatalogSnapshot,
    firstDay: LocalDate,
    lastDay: LocalDate,
    zone: ZoneId,
    generalName: String,
    withoutActivity: String,
): ExportData {
    val rows = entries
        .filter { it.endUtc != null }
        .sortedBy { it.startUtc }
        .map { entry ->
            val start = Instant.ofEpochMilli(entry.startUtc).atZone(zone)
            val end = Instant.ofEpochMilli(entry.endUtc!!).atZone(zone)
            val combo = snapshot.combo(entry.comboKey)
            Triple(
                entry,
                ExportEntry(
                    date = start.toLocalDate(),
                    start = start.toLocalTime(),
                    end = end.toLocalTime(),
                    endDate = end.toLocalDate(),
                    durationMillis = entry.endUtc - entry.startUtc,
                    account = combo?.account?.let { if (it.isGeneral) generalName else it.name }.orEmpty(),
                    activity = combo?.activity?.name,
                    note = entry.note,
                ),
                combo,
            )
        }
        .filter { (_, row, _) -> row.date in firstDay..lastDay }
    val exportRows = rows.map { it.second }

    val byAccount = exportRows.groupBy { it.account }
        .map { (account, accountRows) ->
            ExportTotal(
                name = account,
                millis = accountRows.sumOf { it.durationMillis },
                children = accountRows.groupBy { it.activity ?: withoutActivity }
                    .map { (activity, activityRows) -> ExportTotal(activity, activityRows.sumOf { it.durationMillis }) }
                    .sortedByDescending { it.millis },
            )
        }
        .sortedByDescending { it.millis }
    val byActivity = exportRows.groupBy { it.activity ?: withoutActivity }
        .map { (activity, activityRows) -> ExportTotal(activity, activityRows.sumOf { it.durationMillis }) }
        .sortedByDescending { it.millis }
    val perDay = exportRows.groupBy { it.date }
        .map { (date, dayRows) -> date to dayRows.sumOf { it.durationMillis } }
        .sortedBy { it.first }

    return ExportData(
        firstDay = firstDay,
        lastDay = lastDay,
        entries = exportRows,
        totalMillis = exportRows.sumOf { it.durationMillis },
        byAccount = byAccount,
        byActivity = byActivity,
        perDay = perDay,
    )
}

/** "LehrZeit_2026-10-01_2026-10-31.csv", or with one date for a single day. */
fun exportFileName(firstDay: LocalDate, lastDay: LocalDate, format: ExportFormat): String {
    val range = if (firstDay == lastDay) "$firstDay" else "${firstDay}_$lastDay"
    return "LehrZeit_$range.${format.extension}"
}
