// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import de.janaberger.lehrzeit.time.formatHoursMinutes
import java.io.Writer
import java.text.DecimalFormatSymbols
import java.time.LocalTime
import java.util.Locale

/**
 * One row per entry, as spreadsheets in [locale] expect it: where the decimal mark is a comma
 * (e.g. German), fields are separated by semicolons, otherwise by commas. UTF-8 with a byte
 * order mark so Excel shows umlauts correctly; CRLF line ends. Dates are ISO (2026-10-25) and
 * times 24 h, which every spreadsheet recognizes.
 */
fun writeCsv(data: ExportData, labels: ExportLabels, locale: Locale, out: Writer) {
    val decimal = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val separator = if (decimal == ',') ';' else ','

    fun field(value: String): String =
        if (value.any { it == separator || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    fun row(values: List<String>) {
        out.write(values.joinToString(separator.toString(), transform = ::field))
        out.write("\r\n")
    }

    out.write("\uFEFF")
    row(listOf(labels.date, labels.start, labels.end, labels.duration, labels.hours, labels.activity, labels.schoolClass, labels.note))
    for (entry in data.entries) {
        row(
            listOf(
                entry.date.toString(),
                entry.start.toHhMm(),
                entry.end.toHhMm(),
                formatHoursMinutes(entry.durationMillis),
                decimalHours(entry.durationMillis, decimal),
                entry.activity,
                entry.schoolClass.orEmpty(),
                entry.note,
            ),
        )
    }
    out.flush()
}

/** "1,50" or "1.50" */
internal fun decimalHours(millis: Long, decimal: Char): String =
    String.format(Locale.ROOT, "%.2f", millis / 3_600_000.0).replace('.', decimal)

internal fun LocalTime.toHhMm(): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
