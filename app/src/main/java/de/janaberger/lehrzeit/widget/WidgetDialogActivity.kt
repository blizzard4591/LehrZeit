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
import de.janaberger.lehrzeit.data.db.SchoolClass
import de.janaberger.lehrzeit.data.db.WorkActivity
import de.janaberger.lehrzeit.tracking.getComboKey
import de.janaberger.lehrzeit.tracking.putComboKey
import de.janaberger.lehrzeit.tracking.showDiscardedToast
import de.janaberger.lehrzeit.ui.components.ClassSheet
import de.janaberger.lehrzeit.ui.components.SwitchDialog
import de.janaberger.lehrzeit.ui.theme.LehrZeitTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The widget's questions after a tile was tapped: which class to book, or "Stop A and start B?"
 * while another timer runs. A widget can't show a dialog itself, so this transparent activity
 * shows just the sheet or dialog over the home screen.
 */
class WidgetDialogActivity : ComponentActivity() {
    private sealed interface Question {
        data class ChooseClass(val activity: WorkActivity, val classes: List<SchoolClass>, val running: Combo?) : Question
        data class ConfirmSwitch(val from: Combo, val to: Combo) : Question
    }

    private var question by mutableStateOf<Question?>(null)

    private val container get() = (application as LehrZeitApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val key = intent.getComboKey() ?: return finish()
        lifecycleScope.launch {
            question = (if (intent.action == ACTION_CHOOSE_CLASS) classQuestion(key.activityId) else switchQuestion(key))
                ?: return@launch finish()
        }
        setContent {
            LehrZeitTheme {
                when (val q = question) {
                    is Question.ChooseClass -> ClassSheet(q.activity, q.classes, q.running, onSelect = ::start, onDismiss = ::finish)
                    is Question.ConfirmSwitch -> SwitchDialog(from = q.from, to = q.to, onConfirm = { start(q.to.key) }, onDismiss = ::finish)
                    null -> Unit
                }
            }
        }
    }

    private suspend fun classQuestion(activityId: Long): Question? {
        val snapshot = container.catalog.observeSnapshot().first()
        val activity = snapshot.activeActivities.firstOrNull { it.id == activityId } ?: return null
        val classes = snapshot.activeClassesFor(activityId)
        // Its classes went away since the widget was drawn, so it's booked on its own like any other tile.
        if (classes.isEmpty()) return switchQuestion(ComboKey(activityId, null))
        val running = container.entries.getRunning()?.let { Combo(it.activity, it.schoolClass) }
        return Question.ChooseClass(activity, classes, running)
    }

    private suspend fun switchQuestion(key: ComboKey): Question? {
        // If the timer stopped since the widget was drawn, there's nothing to ask.
        if (container.trackingController.startIfIdle(key)) return null
        val from = container.entries.getRunning()?.let { Combo(it.activity, it.schoolClass) }
        val to = container.catalog.getCombo(key)
        return if (from == null || to == null) null else Question.ConfirmSwitch(from, to)
    }

    private fun start(key: ComboKey) {
        // Outlives this activity, which closes right away.
        container.applicationScope.launch {
            if (container.trackingController.start(key)) showDiscardedToast(applicationContext, container.preferences)
        }
        finish()
    }

    companion object {
        // Distinct actions keep the two kinds of tiles' PendingIntents apart.
        private const val ACTION_CHOOSE_CLASS = "de.janaberger.lehrzeit.action.CHOOSE_CLASS"
        private const val ACTION_CONFIRM_SWITCH = "de.janaberger.lehrzeit.action.CONFIRM_SWITCH"

        /** Asks which class to book [activityId] on; picking one starts it, or switches to it at once. */
        fun chooseClass(context: Context, activityId: Long): Intent = intent(context, ACTION_CHOOSE_CLASS, ComboKey(activityId, null))

        /** Starts [key], or asks first if another timer runs. */
        fun confirmSwitch(context: Context, key: ComboKey): Intent = intent(context, ACTION_CONFIRM_SWITCH, key)

        private fun intent(context: Context, action: String, key: ComboKey): Intent =
            Intent(context, WidgetDialogActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putComboKey(key)
    }
}
