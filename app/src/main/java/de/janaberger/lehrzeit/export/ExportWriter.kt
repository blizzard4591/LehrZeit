// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.CatalogRepository
import de.janaberger.lehrzeit.data.EntryRepository
import de.janaberger.lehrzeit.time.dayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

/** Writes exports to a file the user picked, or to a cache file that can be shared. */
class ExportWriter(
    private val context: Context,
    private val entries: EntryRepository,
    private val catalog: CatalogRepository,
) {
    suspend fun saveTo(uri: Uri, format: ExportFormat, firstDay: LocalDate, lastDay: LocalDate) {
        val data = load(firstDay, lastDay)
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't open $uri")
            stream.use { write(format, data, it) }
        }
    }

    /** Writes to the app's cache, replacing earlier exports there, and returns a content URI for sharing. */
    suspend fun writeForSharing(format: ExportFormat, firstDay: LocalDate, lastDay: LocalDate): Uri {
        val data = load(firstDay, lastDay)
        return withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "exports").apply { mkdirs() }
            directory.listFiles()?.forEach { it.delete() }
            val file = File(directory, exportFileName(firstDay, lastDay, format))
            file.outputStream().use { write(format, data, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private suspend fun load(firstDay: LocalDate, lastDay: LocalDate): ExportData {
        val zone = ZoneId.systemDefault()
        val list = entries.observeFinishedStartingIn(dayBounds(firstDay, zone).startUtc, dayBounds(lastDay, zone).endUtc).first()
        return buildExportData(
            entries = list,
            snapshot = catalog.observeSnapshot().first(),
            firstDay = firstDay,
            lastDay = lastDay,
            zone = zone,
            generalName = context.getString(R.string.activity_general),
            withoutClass = context.getString(R.string.stats_without_class),
        )
    }

    private fun write(format: ExportFormat, data: ExportData, out: OutputStream) {
        val labels = labels()
        val locale = context.resources.configuration.locales[0]
        when (format) {
            ExportFormat.CSV -> out.bufferedWriter(Charsets.UTF_8).let { writeCsv(data, labels, locale, it) }
            ExportFormat.ODS -> writeOds(data, labels, out)
            ExportFormat.PDF -> writePdf(data, labels, locale, LocalDate.now(), out)
        }
    }

    private fun labels() = with(context) {
        ExportLabels(
            date = getString(R.string.export_col_date),
            start = getString(R.string.export_col_start),
            end = getString(R.string.export_col_end),
            duration = getString(R.string.export_col_duration),
            hours = getString(R.string.export_col_hours),
            activity = getString(R.string.export_col_activity),
            schoolClass = getString(R.string.export_col_class),
            note = getString(R.string.export_col_note),
            entriesTitle = getString(R.string.export_entries),
            summaryTitle = getString(R.string.export_summary),
            period = getString(R.string.export_period),
            total = getString(R.string.stats_total),
            byActivity = getString(R.string.stats_by_activity),
            byClass = getString(R.string.stats_by_class),
            perDay = getString(R.string.export_per_day),
            withoutClass = getString(R.string.stats_without_class),
            reportTitle = getString(R.string.export_report_title),
            time = getString(R.string.export_col_time),
            bookedOn = getString(R.string.entry_booked_on),
            pageOf = getString(R.string.export_page_of),
            createdOn = getString(R.string.export_created_on),
        )
    }
}
