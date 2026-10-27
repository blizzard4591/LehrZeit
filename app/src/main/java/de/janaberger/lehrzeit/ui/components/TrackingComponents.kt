// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.components

import android.content.res.Resources
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.time.formatTimeOfDay
import kotlinx.coroutines.delay

@Composable
fun Combo.label(): String = label(stringResource(R.string.activity_general))

@Composable
fun timeOfDay(utc: Long): String = formatTimeOfDay(utc, LocalConfiguration.current.locales[0])

/** "30 seconds", "1 minute", "5 minutes" */
fun minLengthText(resources: Resources, seconds: Int): String =
    if (seconds < 60) {
        resources.getQuantityString(R.plurals.duration_seconds, seconds, seconds)
    } else {
        resources.getQuantityString(R.plurals.duration_minutes, seconds / 60, seconds / 60)
    }

@Composable
fun minLengthText(seconds: Int): String = minLengthText(LocalResources.current, seconds)

/** The current time; while [ticking], it updates on every full second. */
@Composable
fun rememberNow(ticking: Boolean): State<Long> = produceState(System.currentTimeMillis(), ticking) {
    value = System.currentTimeMillis()
    while (ticking) {
        delay(1_000 - System.currentTimeMillis() % 1_000)
        value = System.currentTimeMillis()
    }
}

/** "Switch? This stops 7b and starts 8a." Used by the Track screen and for widget taps. */
@Composable
fun SwitchDialog(from: Combo, to: Combo, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.switch_title)) },
        text = { Text(stringResource(R.string.switch_text, from.label(), to.label())) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_switch)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
