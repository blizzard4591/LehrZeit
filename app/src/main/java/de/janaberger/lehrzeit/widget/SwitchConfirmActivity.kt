// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import de.janaberger.lehrzeit.LehrZeitApplication
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.tracking.getComboKey
import de.janaberger.lehrzeit.tracking.putComboKey
import de.janaberger.lehrzeit.tracking.showDiscardedToast
import de.janaberger.lehrzeit.ui.components.SwitchDialog
import de.janaberger.lehrzeit.ui.theme.LehrZeitTheme
import kotlinx.coroutines.launch

/**
 * Asks "Stop A and start B?" when a widget tile is tapped while another timer runs. A widget can't
 * show a dialog itself, so this transparent activity shows just the dialog over the home screen.
 */
class SwitchConfirmActivity : ComponentActivity() {
    private var question by mutableStateOf<Pair<Combo, Combo>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val key = intent.getComboKey() ?: return finish()
        val container = (application as LehrZeitApplication).container
        lifecycleScope.launch {
            // If the timer stopped since the widget was drawn, there's nothing to ask.
            if (container.trackingController.startIfIdle(key)) return@launch finish()
            val from = container.entries.getRunning()?.let { Combo(it.activity, it.schoolClass) }
            val to = container.catalog.getCombo(key)
            if (from == null || to == null) finish() else question = from to to
        }
        setContent {
            LehrZeitTheme {
                question?.let { (from, to) ->
                    SwitchDialog(from = from, to = to, onConfirm = { confirm(key) }, onDismiss = ::finish)
                }
            }
        }
    }

    private fun confirm(key: ComboKey) {
        val container = (application as LehrZeitApplication).container
        // Outlives this activity, which closes right away.
        container.applicationScope.launch {
            if (container.trackingController.start(key)) showDiscardedToast(applicationContext, container.preferences)
        }
        finish()
    }

    companion object {
        fun intent(context: Context, key: ComboKey): Intent =
            Intent(context, SwitchConfirmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putComboKey(key)
    }
}
