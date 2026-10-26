// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private val labels = ExportLabels(
        date = "Datum", start = "Beginn", end = "Ende", duration = "Dauer", hours = "Stunden", account = "Konto",
        activity = "Tätigkeit", note = "Notiz", entriesTitle = "Einträge", summaryTitle = "Übersicht", period = "Zeitraum",
        total = "Gesamt", byAccount = "Nach Konto", byActivity = "Nach Tätigkeit", perDay = "Pro Tag", withoutActivity = "Ohne Tätigkeit",
        reportTitle = "Arbeitszeit", time = "Zeit", bookedOn = "Gebucht auf", pageOf = "Seite %1\$d von %2\$d", createdOn = "erstellt am %s",
    )

    @Test
    fun manyEntries_produceAReadableMultiPagePdf() {
        val minute = 60_000L
        val entries = (0 until 120).map { i ->
            val start = LocalTime.of(8 + (i % 4) * 3, 0)
            ExportEntry(
                date = LocalDate.of(2026, 10, 1).plusDays(i / 4L),
                start = start,
                end = start.plusMinutes(90),
                endDate = LocalDate.of(2026, 10, 1).plusDays(i / 4L),
                durationMillis = 90 * minute,
                account = if (i % 2 == 0) "7b" else "9a Mathe",
                activity = if (i % 3 == 0) null else "Korrekturen",
                note = if (i % 5 == 0) "Klausur 2, Teil A und B korrigiert, Notenliste aktualisiert und an die Schulleitung geschickt" else "",
            )
        }
        val data = ExportData(
            firstDay = LocalDate.of(2026, 10, 1),
            lastDay = LocalDate.of(2026, 10, 31),
            entries = entries,
            totalMillis = entries.sumOf { it.durationMillis },
            byAccount = listOf(ExportTotal("7b", 90 * 60 * minute, listOf(ExportTotal("Korrekturen", 60 * 60 * minute))), ExportTotal("9a Mathe", 90 * 60 * minute)),
            byActivity = listOf(ExportTotal("Korrekturen", 120 * 60 * minute), ExportTotal("Ohne Tätigkeit", 60 * 60 * minute)),
            perDay = entries.groupBy { it.date }.map { (date, day) -> date to day.sumOf { it.durationMillis } },
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "report-test.pdf")

        file.outputStream().use { writePdf(data, labels, Locale.GERMANY, LocalDate.of(2026, 10, 25), it) }

        assertEquals("%PDF", file.readBytes().copyOfRange(0, 4).decodeToString())
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue("expected several pages, got ${renderer.pageCount}", renderer.pageCount >= 3)
                renderer.openPage(renderer.pageCount - 1).use { page ->
                    assertEquals(595, page.width)
                    assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}
