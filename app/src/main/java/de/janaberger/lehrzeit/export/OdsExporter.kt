// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val ODS_MIME = "application/vnd.oasis.opendocument.spreadsheet"

/**
 * An OpenDocument spreadsheet with two sheets: every entry, and a summary (total, by activity
 * with its classes, by class, per day). Written directly as the zip of XML files that ODF
 * consists of. Cells are typed (dates, times, durations, numbers), so the sheet can be
 * calculated with right away; their display follows the spreadsheet program's language.
 */
fun writeOds(data: ExportData, labels: ExportLabels, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
        // ODF requires "mimetype" to be the first entry, stored uncompressed.
        val mimetype = ODS_MIME.toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = mimetype.size.toLong()
                crc = CRC32().apply { update(mimetype) }.value
            },
        )
        zip.write(mimetype)
        zip.closeEntry()

        fun add(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        add("META-INF/manifest.xml", MANIFEST)
        add("meta.xml", META)
        add("styles.xml", STYLES)
        add("content.xml", content(data, labels))
    }
}

private fun content(data: ExportData, labels: ExportLabels): String = buildString {
    append(
        """<?xml version="1.0" encoding="UTF-8"?>
<office:document-content $NAMESPACES office:version="1.3">
<office:automatic-styles>
<number:date-style style:name="N_DATE"><number:year number:style="long"/><number:text>-</number:text><number:month number:style="long"/><number:text>-</number:text><number:day number:style="long"/></number:date-style>
<number:time-style style:name="N_TIME"><number:hours number:style="long"/><number:text>:</number:text><number:minutes number:style="long"/></number:time-style>
<number:time-style style:name="N_DURATION" number:truncate-on-overflow="false"><number:hours/><number:text>:</number:text><number:minutes number:style="long"/></number:time-style>
<number:number-style style:name="N_HOURS"><number:number number:decimal-places="2" number:min-decimal-places="2" number:min-integer-digits="1"/></number:number-style>
<style:style style:name="ce_date" style:family="table-cell" style:data-style-name="N_DATE"/>
<style:style style:name="ce_time" style:family="table-cell" style:data-style-name="N_TIME"/>
<style:style style:name="ce_duration" style:family="table-cell" style:data-style-name="N_DURATION"/>
<style:style style:name="ce_hours" style:family="table-cell" style:data-style-name="N_HOURS"/>
<style:style style:name="ce_bold" style:family="table-cell"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="ce_bold_duration" style:family="table-cell" style:data-style-name="N_DURATION"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="ce_bold_hours" style:family="table-cell" style:data-style-name="N_HOURS"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="co_s" style:family="table-column"><style:table-column-properties style:column-width="2.2cm"/></style:style>
<style:style style:name="co_m" style:family="table-column"><style:table-column-properties style:column-width="2.8cm"/></style:style>
<style:style style:name="co_l" style:family="table-column"><style:table-column-properties style:column-width="5cm"/></style:style>
<style:style style:name="co_xl" style:family="table-column"><style:table-column-properties style:column-width="9cm"/></style:style>
</office:automatic-styles>
<office:body>
<office:spreadsheet>
""",
    )

    // Sheet 1: one row per entry.
    append("""<table:table table:name="${xml(labels.entriesTitle)}">""")
    append("""<table:table-column table:style-name="co_m"/><table:table-column table:style-name="co_s" table:number-columns-repeated="4"/>""")
    append("""<table:table-column table:style-name="co_l" table:number-columns-repeated="2"/><table:table-column table:style-name="co_xl"/>""")
    row {
        listOf(labels.date, labels.start, labels.end, labels.duration, labels.hours, labels.activity, labels.schoolClass, labels.note)
            .forEach { textCell(it, "ce_bold") }
    }
    for (entry in data.entries) {
        row {
            dateCell(entry.date)
            timeCell(entry.start.hour * 60 + entry.start.minute, "ce_time")
            timeCell(entry.end.hour * 60 + entry.end.minute, "ce_time")
            durationCell(entry.durationMillis, "ce_duration")
            hoursCell(entry.durationMillis, "ce_hours")
            textCell(entry.activity)
            textCell(entry.schoolClass.orEmpty())
            textCell(entry.note)
        }
    }
    append("</table:table>")

    // Sheet 2: totals.
    append("""<table:table table:name="${xml(labels.summaryTitle)}">""")
    append("""<table:table-column table:style-name="co_l"/><table:table-column table:style-name="co_m" table:number-columns-repeated="2"/>""")
    row {
        textCell(labels.period, "ce_bold")
        textCell("${data.firstDay} – ${data.lastDay}")
    }
    row {
        textCell(labels.total, "ce_bold")
        durationCell(data.totalMillis, "ce_bold_duration")
        hoursCell(data.totalMillis, "ce_bold_hours")
    }
    fun section(title: String, totals: List<ExportTotal>) {
        row {}
        row {
            textCell(title, "ce_bold")
            textCell(labels.duration, "ce_bold")
            textCell(labels.hours, "ce_bold")
        }
        for (total in totals) {
            row {
                textCell(total.name)
                durationCell(total.millis, "ce_duration")
                hoursCell(total.millis, "ce_hours")
            }
            for (child in total.children) {
                row {
                    textCell(child.name, indent = 4)
                    durationCell(child.millis, "ce_duration")
                    hoursCell(child.millis, "ce_hours")
                }
            }
        }
    }
    section(labels.byActivity, data.byActivity)
    section(labels.byClass, data.byClass)
    row {}
    row {
        textCell(labels.perDay, "ce_bold")
        textCell(labels.duration, "ce_bold")
        textCell(labels.hours, "ce_bold")
    }
    for ((date, millis) in data.perDay) {
        row {
            dateCell(date)
            durationCell(millis, "ce_duration")
            hoursCell(millis, "ce_hours")
        }
    }
    append("</table:table>")

    append("</office:spreadsheet></office:body></office:document-content>")
}

private fun StringBuilder.row(cells: StringBuilder.() -> Unit) {
    append("<table:table-row>")
    cells()
    append("</table:table-row>")
}

private fun StringBuilder.textCell(text: String, style: String? = null, indent: Int = 0) {
    append("<table:table-cell")
    style?.let { append(""" table:style-name="$it"""") }
    append(""" office:value-type="string">""")
    // Each line of the text becomes its own paragraph; leading spaces need <text:s>.
    text.split('\n').forEachIndexed { index, line ->
        append("<text:p>")
        if (index == 0 && indent > 0) append("""<text:s text:c="$indent"/>""")
        append(xml(line))
        append("</text:p>")
    }
    append("</table:table-cell>")
}

private fun StringBuilder.dateCell(date: LocalDate) {
    append("""<table:table-cell table:style-name="ce_date" office:value-type="date" office:date-value="$date"><text:p>$date</text:p></table:table-cell>""")
}

private fun StringBuilder.timeCell(minutesOfDay: Int, style: String) {
    val hours = minutesOfDay / 60
    val minutes = minutesOfDay % 60
    val shown = String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    append("""<table:table-cell table:style-name="$style" office:value-type="time" office:time-value="PT${hours}H${minutes}M0S"><text:p>$shown</text:p></table:table-cell>""")
}

private fun StringBuilder.durationCell(millis: Long, style: String) {
    val totalMinutes = millis / 60_000
    val shown = String.format(Locale.ROOT, "%d:%02d", totalMinutes / 60, totalMinutes % 60)
    append("""<table:table-cell table:style-name="$style" office:value-type="time" office:time-value="PT${totalMinutes / 60}H${totalMinutes % 60}M0S"><text:p>$shown</text:p></table:table-cell>""")
}

private fun StringBuilder.hoursCell(millis: Long, style: String) {
    val hours = String.format(Locale.ROOT, "%.2f", millis / 3_600_000.0)
    append("""<table:table-cell table:style-name="$style" office:value-type="float" office:value="$hours"><text:p>$hours</text:p></table:table-cell>""")
}

/** Escapes text for XML and drops control characters XML can't contain. */
private fun xml(text: String): String = buildString {
    for (c in text) {
        when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c == '\t' -> append(' ')
            c < ' ' -> Unit
            else -> append(c)
        }
    }
}

private const val NAMESPACES =
    """xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" """ +
        """xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" """ +
        """xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" """ +
        """xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" """ +
        """xmlns:number="urn:oasis:names:tc:opendocument:xmlns:datastyle:1.0" """ +
        """xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" """ +
        """xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0""""

private const val MANIFEST = """<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.3">
<manifest:file-entry manifest:full-path="/" manifest:version="1.3" manifest:media-type="$ODS_MIME"/>
<manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
<manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
<manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
</manifest:manifest>
"""

private const val META = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta $NAMESPACES office:version="1.3"><office:meta><meta:generator>LehrZeit</meta:generator></office:meta></office:document-meta>
"""

private const val STYLES = """<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles $NAMESPACES office:version="1.3"/>
"""
