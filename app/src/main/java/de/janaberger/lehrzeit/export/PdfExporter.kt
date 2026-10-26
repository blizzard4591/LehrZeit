// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.format.DateFormat
import androidx.core.graphics.withTranslation
import de.janaberger.lehrzeit.time.formatHoursMinutes
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// A4 in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48f
private const val FOOTER_HEIGHT = 24f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

private val INK = Color.rgb(0x20, 0x21, 0x24)
private val MUTED = Color.rgb(0x5F, 0x63, 0x68)
private val RULE = Color.rgb(0xDA, 0xDC, 0xE0)
private val BAR = Color.rgb(0x2B, 0x6A, 0x57)
private val TRACK = Color.rgb(0xD5, 0xEC, 0xE2)

/**
 * A printable A4 report: period and total, totals by account (with activities), by activity and
 * per day, then every entry with its note. Laid out twice: once to count the pages for
 * "page x of y", once to draw.
 */
fun writePdf(data: ExportData, labels: ExportLabels, locale: Locale, createdOn: LocalDate, out: OutputStream) {
    val pageCount = PdfReport(data, labels, locale, createdOn, document = null, pageCount = 0).render()
    val document = PdfDocument()
    try {
        PdfReport(data, labels, locale, createdOn, document, pageCount).render()
        document.writeTo(out)
    } finally {
        document.close()
    }
}

/** With [document] null, nothing is drawn and [render] only counts pages. */
private class PdfReport(
    private val data: ExportData,
    private val labels: ExportLabels,
    private val locale: Locale,
    private val createdOn: LocalDate,
    private val document: PdfDocument?,
    private val pageCount: Int,
) {
    private val title = paint(18f, bold = true)
    private val heading = paint(13f, bold = true)
    private val body = paint(9.5f)
    private val bodyBold = paint(9.5f, bold = true)
    private val muted = paint(9.5f, color = MUTED)
    private val big = paint(24f, bold = true)
    private val small = paint(8f, color = MUTED)
    private val rule = Paint().apply { color = RULE; strokeWidth = 0.5f }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BAR }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK }

    private val shortDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
    private val longDate = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMdy"), locale)

    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNumber = 0
    private var y = MARGIN

    fun render(): Int {
        newPage()
        drawText(labels.reportTitle, MARGIN, title)
        y += 4f
        drawText("${longDate.format(data.firstDay)} – ${longDate.format(data.lastDay)}", MARGIN, muted)
        y += 16f
        drawText(labels.total, MARGIN, muted)
        drawText(hours(data.totalMillis), MARGIN, big)

        totalsTable(labels.byAccount, data.byAccount)
        totalsTable(labels.byActivity, data.byActivity)
        // Days are compared with the busiest day; a share of the whole period says little for one day.
        totalsTable(
            labels.perDay,
            data.perDay.map { (date, millis) -> ExportTotal(shortDate.format(date), millis) },
            scaleMillis = data.perDay.maxOfOrNull { it.second } ?: 0L,
            showShare = false,
        )
        entriesTable()

        finishPage()
        return pageNumber
    }

    /** Name, hours, share of the total, and a bar of [scaleMillis] (by default the total). */
    private fun totalsTable(heading: String, totals: List<ExportTotal>, scaleMillis: Long = data.totalMillis, showShare: Boolean = true) {
        if (totals.isEmpty()) return
        sectionHeading(heading)
        val hoursRight = MARGIN + 330f
        val shareRight = MARGIN + 380f
        val barLeft = MARGIN + 395f
        fun line(total: ExportTotal, indent: Float, paint: TextPaint) {
            ensureSpace(16f)
            val baseline = y + 11f
            val share = if (data.totalMillis > 0) total.millis.toFloat() / data.totalMillis else 0f
            val fill = if (scaleMillis > 0) total.millis.toFloat() / scaleMillis else 0f
            text(ellipsize(total.name, paint, hoursRight - 60f - MARGIN - indent), MARGIN + indent, baseline, paint)
            textRight(hours(total.millis), hoursRight, baseline, paint)
            if (showShare) textRight("${Math.round(share * 100)} %", shareRight, baseline, muted)
            canvas?.let {
                val top = baseline - 6f
                it.drawRoundRect(RectF(barLeft, top, MARGIN + CONTENT_WIDTH, top + 5f), 2.5f, 2.5f, track)
                if (fill > 0f) it.drawRoundRect(RectF(barLeft, top, barLeft + (MARGIN + CONTENT_WIDTH - barLeft) * fill, top + 5f), 2.5f, 2.5f, bar)
            }
            y += 16f
        }
        for (total in totals) {
            line(total, indent = 0f, paint = if (total.children.isEmpty()) body else bodyBold)
            total.children.forEach { line(it, indent = 14f, paint = body) }
        }
    }

    private fun entriesTable() {
        if (data.entries.isEmpty()) return
        sectionHeading(labels.entriesTitle)
        val dateX = MARGIN
        val timeX = MARGIN + 70f
        val durationRight = MARGIN + 190f
        val bookedX = MARGIN + 205f
        val noteX = MARGIN + 345f
        val noteWidth = (MARGIN + CONTENT_WIDTH - noteX).toInt()

        fun header() {
            ensureSpace(18f)
            val baseline = y + 11f
            text(labels.date, dateX, baseline, bodyBold)
            text(labels.time, timeX, baseline, bodyBold)
            textRight(labels.duration, durationRight, baseline, bodyBold)
            text(labels.bookedOn, bookedX, baseline, bodyBold)
            text(labels.note, noteX, baseline, bodyBold)
            y += 16f
            canvas?.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rule)
            y += 2f
        }

        header()
        for (entry in data.entries) {
            val note = if (entry.note.isBlank()) {
                null
            } else {
                StaticLayout.Builder.obtain(entry.note, 0, entry.note.length, body, noteWidth).setIncludePad(false).build()
            }
            val rowHeight = maxOf(16f, (note?.height?.toFloat() ?: 0f) + 6f)
            if (y + rowHeight > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) {
                newPage()
                header()
            }
            val baseline = y + 11f
            val days = ChronoUnit.DAYS.between(entry.date, entry.endDate)
            val time = "${entry.start.toHhMm()}–${entry.end.toHhMm()}" + if (days > 0) " (+$days)" else ""
            val booked = listOfNotNull(entry.account, entry.activity).joinToString(" › ")
            text(shortDate.format(entry.date), dateX, baseline, body)
            text(time, timeX, baseline, body)
            textRight(formatHoursMinutes(entry.durationMillis), durationRight, baseline, body)
            text(ellipsize(booked, body, noteX - bookedX - 8f), bookedX, baseline, body)
            if (note != null) {
                // Puts the note's first line on the same baseline as the other cells.
                canvas?.withTranslation(noteX, baseline + note.getLineAscent(0)) { note.draw(this) }
            }
            y += rowHeight
            canvas?.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rule)
        }
    }

    private fun sectionHeading(text: String) {
        y += 18f
        // Keep a heading together with at least two rows.
        ensureSpace(22f + 2 * 16f)
        drawText(text, MARGIN, heading)
        y += 4f
    }

    private fun newPage() {
        finishPage()
        pageNumber++
        y = MARGIN
        document?.let {
            val newPage = it.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            page = newPage
            canvas = newPage.canvas
        }
    }

    private fun finishPage() {
        val current = page ?: return
        val baseline = PAGE_HEIGHT - MARGIN + 8f
        text("LehrZeit · ${String.format(labels.createdOn, longDate.format(createdOn))}", MARGIN, baseline, small)
        textRight(String.format(labels.pageOf, pageNumber, pageCount), MARGIN + CONTENT_WIDTH, baseline, small)
        document?.finishPage(current)
        page = null
        canvas = null
    }

    private fun ensureSpace(height: Float) {
        if (y + height > PAGE_HEIGHT - MARGIN - FOOTER_HEIGHT) newPage()
    }

    /** Draws a line of text at the current position and moves below it. */
    private fun drawText(text: String, x: Float, paint: TextPaint) {
        val metrics = paint.fontMetrics
        ensureSpace(metrics.descent - metrics.ascent)
        text(text, x, y - metrics.ascent, paint)
        y += metrics.descent - metrics.ascent
    }

    private fun text(text: String, x: Float, baseline: Float, paint: Paint) {
        canvas?.drawText(text, x, baseline, paint)
    }

    private fun textRight(text: String, right: Float, baseline: Float, paint: Paint) {
        text(text, right - paint.measureText(text), baseline, paint)
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String =
        TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()

    private fun hours(millis: Long) = "${formatHoursMinutes(millis)} h"

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }
}
