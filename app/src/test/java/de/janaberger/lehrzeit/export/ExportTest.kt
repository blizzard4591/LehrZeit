// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.Entry
import de.janaberger.lehrzeit.data.db.WorkActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val hour = 3_600_000L

    private val snapshot = CatalogSnapshot(
        accounts = listOf(
            Account(id = 1, name = "General", color = 0, isGeneral = true),
            Account(id = 2, name = "7b", color = 0),
        ),
        activities = listOf(WorkActivity(id = 10, name = "Correcting")),
        links = setOf(ComboKey(2, 10)),
    )

    private val labels = ExportLabels(
        date = "Date", start = "Start", end = "End", duration = "Duration", hours = "Hours", account = "Account",
        activity = "Activity", note = "Note", entriesTitle = "Entries", summaryTitle = "Summary", period = "Period",
        total = "Total", byAccount = "By account", byActivity = "By activity", perDay = "Per day", withoutActivity = "Without activity",
        reportTitle = "Working time", time = "Time", bookedOn = "Booked on", pageOf = "Page %1${'$'}d of %2${'$'}d", createdOn = "Created on %s",
    )

    private fun utc(dateTime: String): Long = LocalDateTime.parse(dateTime).atZone(berlin).toInstant().toEpochMilli()

    private fun entry(account: Long, activity: Long?, start: String, end: String?, note: String = "") = Entry(
        accountId = account, activityId = activity, startUtc = utc(start), endUtc = end?.let(::utc), note = note, createdAt = 0, updatedAt = 0,
    )

    private fun data() = buildExportData(
        entries = listOf(
            entry(2, 10, "2026-10-20T19:30", "2026-10-20T21:00", note = "Klausur; \"Teil 2\""),
            entry(1, null, "2026-10-21T13:00", "2026-10-21T13:45"),
            entry(2, null, "2026-10-21T22:00", "2026-10-22T01:00"), // over midnight: stays on its start day
            entry(2, null, "2026-10-19T23:00", "2026-10-20T00:30"), // starts before the range: left out
            entry(1, null, "2026-10-22T08:00", null), // running: left out
        ),
        snapshot = snapshot,
        firstDay = LocalDate.parse("2026-10-20"),
        lastDay = LocalDate.parse("2026-10-22"),
        zone = berlin,
        generalName = "Allgemein",
        withoutActivity = "Without activity",
    )

    @Test
    fun buildExportData_keepsFinishedEntriesStartingInRange() {
        val data = data()

        assertEquals(listOf("7b", "Allgemein", "7b"), data.entries.map { it.account })
        assertEquals(LocalDate.parse("2026-10-22"), data.entries[2].endDate)
        assertEquals(5 * hour + hour / 4, data.totalMillis)
        assertEquals(
            listOf(
                ExportTotal("7b", 4 * hour + hour / 2, listOf(ExportTotal("Without activity", 3 * hour), ExportTotal("Correcting", hour + hour / 2))),
                ExportTotal("Allgemein", hour * 3 / 4, listOf(ExportTotal("Without activity", hour * 3 / 4))),
            ),
            data.byAccount,
        )
        assertEquals(listOf(LocalDate.parse("2026-10-20") to hour + hour / 2, LocalDate.parse("2026-10-21") to 3 * hour + hour * 3 / 4), data.perDay)
        assertEquals("LehrZeit_2026-10-20_2026-10-22.ods", exportFileName(data.firstDay, data.lastDay, ExportFormat.ODS))
    }

    @Test
    fun csv_germanUsesSemicolonsAndDecimalComma_andQuotesWhereNeeded() {
        val csv = StringWriter().also { writeCsv(data(), labels, Locale.GERMANY, it) }.toString()
        val lines = csv.removePrefix("\uFEFF").split("\r\n")

        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals("Date;Start;End;Duration;Hours;Account;Activity;Note", lines[0])
        assertEquals("2026-10-20;19:30;21:00;1:30;1,50;7b;Correcting;\"Klausur; \"\"Teil 2\"\"\"", lines[1])
        assertEquals("2026-10-21;22:00;01:00;3:00;3,00;7b;;", lines[3])
    }

    @Test
    fun csv_englishUsesCommasAndDecimalPoint() {
        val csv = StringWriter().also { writeCsv(data(), labels, Locale.US, it) }.toString()
        assertEquals("2026-10-21,13:00,13:45,0:45,0.75,Allgemein,,", csv.removePrefix("\uFEFF").split("\r\n")[2])
    }

    @Test
    fun ods_isAValidZipWithTypedCells() {
        val bytes = ByteArrayOutputStream().also { writeOds(data(), labels, it) }.toByteArray()

        val files = linkedMapOf<String, Pair<ZipEntry, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { files[it.name] = it to zip.readBytes() }
        }
        val (mimeEntry, mimeBytes) = files.getValue("mimetype")
        assertEquals("mimetype", files.keys.first())
        assertEquals(ZipEntry.STORED, mimeEntry.method)
        assertEquals("application/vnd.oasis.opendocument.spreadsheet", mimeBytes.decodeToString())
        assertTrue(files.keys.containsAll(listOf("META-INF/manifest.xml", "meta.xml", "styles.xml", "content.xml")))

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        files.filterKeys { it.endsWith(".xml") }.forEach { (_, file) -> factory.newDocumentBuilder().parse(ByteArrayInputStream(file.second)) }

        val content = factory.newDocumentBuilder().parse(ByteArrayInputStream(files.getValue("content.xml").second))
        val tableNs = "urn:oasis:names:tc:opendocument:xmlns:table:1.0"
        val officeNs = "urn:oasis:names:tc:opendocument:xmlns:office:1.0"
        val tables = content.getElementsByTagNameNS(tableNs, "table")
        assertEquals(2, tables.length)
        assertEquals("Entries", (tables.item(0) as Element).getAttributeNS(tableNs, "name"))
        // Header plus three entries.
        assertEquals(4, (tables.item(0) as Element).getElementsByTagNameNS(tableNs, "table-row").length)

        val firstEntryCells = ((tables.item(0) as Element).getElementsByTagNameNS(tableNs, "table-row").item(1) as Element)
            .getElementsByTagNameNS(tableNs, "table-cell")
        fun cell(index: Int) = firstEntryCells.item(index) as Element
        assertEquals("2026-10-20", cell(0).getAttributeNS(officeNs, "date-value"))
        assertEquals("PT19H30M0S", cell(1).getAttributeNS(officeNs, "time-value"))
        assertEquals("PT1H30M0S", cell(3).getAttributeNS(officeNs, "time-value"))
        assertEquals("1.50", cell(4).getAttributeNS(officeNs, "value"))
        assertEquals("Klausur; \"Teil 2\"", cell(7).textContent)
    }
}
