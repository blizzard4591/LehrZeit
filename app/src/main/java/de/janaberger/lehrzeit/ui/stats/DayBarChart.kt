// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.stats.DayTotal
import de.janaberger.lehrzeit.stats.hourScale
import java.time.LocalDate

private val PLOT_HEIGHT = 160.dp
private val AXIS_WIDTH = 40.dp

/**
 * Hours per day as columns: one series, one color, thin bars with a rounded top on a shared
 * baseline, hairline gridlines at clean hour steps. Each column is tappable and has a spoken
 * description, so its value is never only reachable through a gesture.
 */
@Composable
fun DayBarChart(
    days: List<DayTotal>,
    xLabels: List<String?>,
    descriptions: List<String>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = hourScale(days.maxOf { it.millis })
    val topMillis = scale.topHours * 3_600_000.0
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisTextStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val tickLabels = (0..scale.topHours step scale.stepHours).map { it to stringResource(R.string.hours_value, it.toString()) }

    Column(modifier.padding(horizontal = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(PLOT_HEIGHT)
                .drawBehind {
                    val axisWidth = AXIS_WIDTH.toPx()
                    for ((hours, label) in tickLabels) {
                        val y = size.height * (1f - hours / scale.topHours.toFloat())
                        drawLine(gridColor, Offset(axisWidth, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        val text = textMeasurer.measure(label, axisTextStyle)
                        val textY = (y - text.size.height / 2f).coerceIn(0f, size.height - text.size.height)
                        drawText(text, axisTextColor, Offset(axisWidth - text.size.width - 8.dp.toPx(), textY))
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(start = AXIS_WIDTH),
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = day.date == selected
                    // With a selection, the other days step back so the chosen one stands out.
                    val color = if (selected == null || isSelected) barColor else barColor.copy(alpha = 0.35f)
                    Box(
                        contentAlignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = isSelected, role = Role.Button, onClick = { onSelect(day.date) })
                            .semantics { contentDescription = descriptions[index] },
                    ) {
                        if (day.millis > 0) {
                            Box(
                                Modifier
                                    .padding(horizontal = 1.dp) // the 2 dp gap between neighbouring bars
                                    .widthIn(max = 24.dp)
                                    .fillMaxWidth()
                                    .fillMaxHeight((day.millis / topMillis).toFloat().coerceIn(0.01f, 1f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AXIS_WIDTH, top = 4.dp),
        ) {
            xLabels.forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (label != null) {
                        Text(
                            label,
                            style = axisTextStyle,
                            color = axisTextColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}
