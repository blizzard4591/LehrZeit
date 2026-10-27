// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.janaberger.lehrzeit.LehrZeitApplication
import de.janaberger.lehrzeit.MainActivity
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.time.formatTimeOfDay
import de.janaberger.lehrzeit.tracking.PausedTimer
import de.janaberger.lehrzeit.tracking.RunningTimer
import de.janaberger.lehrzeit.tracking.TrackingActionReceiver
import de.janaberger.lehrzeit.tracking.TrackingNotifier
import de.janaberger.lehrzeit.tracking.TrackingOverview
import de.janaberger.lehrzeit.tracking.WidgetUpdater
import de.janaberger.lehrzeit.tracking.putComboKey
import de.janaberger.lehrzeit.ui.theme.DarkColors
import de.janaberger.lehrzeit.ui.theme.LightColors
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget: the timer with Pause and Stop (or Resume), and below it as many of the
 * Track screen's tiles as fit. Tapping a tile starts it; while another timer runs,
 * [SwitchConfirmActivity] asks first.
 */
class TrackingWidget : GlanceAppWidget() {
    // The tile grid is laid out for the widget's actual size.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val overview = (context.applicationContext as LehrZeitApplication).container.trackingOverview()
        val initial = overview.first()
        provideContent {
            val state by overview.collectAsState(initial)
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) GlanceTheme.colors else FallbackColors) {
                WidgetContent(state)
            }
        }
    }
}

class TrackingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TrackingWidget()
}

class GlanceWidgetUpdater(private val context: Context) : WidgetUpdater {
    override suspend fun update() {
        // A widget problem must never get in the way of tracking.
        try {
            TrackingWidget().updateAll(context)
        } catch (e: Exception) {
            Log.w("LehrZeit", "Updating the widgets failed", e)
        }
    }
}

// Android 12+ uses the wallpaper colors, like the app; older versions get the app's fixed palette.
private val FallbackColors: ColorProviders = ColorProviders(light = LightColors, dark = DarkColors)

@Composable
private fun WidgetContent(state: TrackingOverview) {
    val size = LocalSize.current
    val grid = tileGrid(size.width.value, size.height.value)
    val rows = state.tiles.take(grid.capacity).chunked(grid.columns)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .systemCornerRadius()
            .padding(WidgetDimens.PADDING.dp),
    ) {
        // Without room for tiles, the status bar takes the whole widget.
        StatusBar(state, GlanceModifier.fillMaxWidth().then(if (rows.isEmpty()) GlanceModifier.defaultWeight() else GlanceModifier.height(WidgetDimens.STATUS_HEIGHT.dp)))
        for (row in rows) {
            Row(GlanceModifier.fillMaxWidth().padding(top = WidgetDimens.GAP.dp)) {
                for (column in 0 until grid.columns) {
                    if (column > 0) Spacer(GlanceModifier.width(WidgetDimens.GAP.dp))
                    val combo = row.getOrNull(column)
                    if (combo != null) {
                        Tile(combo, state.running, GlanceModifier.defaultWeight())
                    } else {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(state: TrackingOverview, modifier: GlanceModifier) {
    val running = state.running
    val paused = state.paused
    val openApp = actionStartActivity(
        Intent(LocalContext.current, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    when {
        running != null -> RunningStatus(running, modifier.clickable(openApp))
        paused != null -> PausedStatus(paused, modifier.clickable(openApp))
        else -> Row(modifier.padding(horizontal = 6.dp).clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                LocalContext.current.getString(R.string.track_idle_title),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RunningStatus(running: RunningTimer, modifier: GlanceModifier) {
    val context = LocalContext.current
    val textColor = GlanceTheme.colors.onPrimaryContainer
    Row(
        modifier = modifier
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                running.combo.label(context.getString(R.string.activity_general)),
                style = TextStyle(color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chronometer(running.startUtc)
                Text(
                    "  " + context.getString(R.string.since_time, formatTimeOfDay(running.startUtc, context.resources.configuration.locales[0])),
                    style = TextStyle(color = textColor, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
        ActionButton(R.drawable.ic_pause, R.string.action_pause, TrackingNotifier.ACTION_PAUSE, filled = false)
        ActionButton(R.drawable.ic_stop, R.string.action_stop, TrackingNotifier.ACTION_STOP, filled = true)
    }
}

@Composable
private fun PausedStatus(paused: PausedTimer, modifier: GlanceModifier) {
    val context = LocalContext.current
    Row(modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                context.getString(R.string.paused_at, formatTimeOfDay(paused.pausedAt, context.resources.configuration.locales[0])),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
            Text(
                paused.combo.label(context.getString(R.string.activity_general)),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
        ActionButton(R.drawable.ic_play, R.string.action_resume, TrackingNotifier.ACTION_RESUME, filled = true)
    }
}

/** The system draws the running time from the start, so the widget needs no updates while it counts. */
@Composable
private fun Chronometer(startUtc: Long) {
    val context = LocalContext.current
    val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - startUtc)
    AndroidRemoteViews(
        RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.widget_chronometer, base, null, true)
        },
        // Otherwise it only gets the height of the smaller text beside it and is cut off.
        modifier = GlanceModifier.wrapContentHeight(),
    )
}

@Composable
private fun ActionButton(@DrawableRes icon: Int, @StringRes label: Int, action: String, filled: Boolean) {
    val context = LocalContext.current
    CircleIconButton(
        imageProvider = ImageProvider(icon),
        contentDescription = context.getString(label),
        onClick = actionSendBroadcast(Intent(context, TrackingActionReceiver::class.java).setAction(action)),
        modifier = GlanceModifier.size(44.dp),
        backgroundColor = if (filled) GlanceTheme.colors.primary else null,
        contentColor = if (filled) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onPrimaryContainer,
    )
}

/** Like the Track screen's tile: an activity-colored edge, the activity and the class. */
@Composable
private fun Tile(combo: Combo, running: RunningTimer?, modifier: GlanceModifier) {
    val context = LocalContext.current
    val isRunning = running?.combo?.key == combo.key
    val action: Action? = when {
        running == null -> actionSendBroadcast(
            Intent(context, TrackingActionReceiver::class.java).setAction(TrackingNotifier.ACTION_START).putComboKey(combo.key),
        )
        isRunning -> null
        else -> actionStartActivity(SwitchConfirmActivity.intent(context, combo.key))
    }
    Row(
        modifier = modifier
            .height(WidgetDimens.TILE_HEIGHT.dp)
            .cornerRadius(12.dp)
            .background(if (isRunning) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surface)
            .then(if (action != null) GlanceModifier.clickable(action) else GlanceModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(GlanceModifier.width(4.dp).fillMaxHeight().background(Color(combo.activity.color)))
        Column(GlanceModifier.defaultWeight().padding(horizontal = 8.dp)) {
            Text(
                if (combo.activity.isGeneral) context.getString(R.string.activity_general) else combo.activity.name,
                style = TextStyle(
                    color = if (isRunning) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            combo.schoolClass?.let {
                Text(
                    it.name,
                    style = TextStyle(
                        color = if (isRunning) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Android 12+ rounds widgets with the launcher's radius; older versions show them square. */
private fun GlanceModifier.systemCornerRadius(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) cornerRadius(android.R.dimen.system_app_widget_background_radius) else this
