// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.track

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.MAX_NOTE_LENGTH
import de.janaberger.lehrzeit.data.nudgedStart
import de.janaberger.lehrzeit.time.formatElapsed
import de.janaberger.lehrzeit.time.formatHoursMinutes
import de.janaberger.lehrzeit.time.formatTimeOfDay
import de.janaberger.lehrzeit.time.totalWithin
import de.janaberger.lehrzeit.tracking.RunningTimer
import de.janaberger.lehrzeit.tracking.TrackingNotifier
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.DeletedEntryUndoEffect
import de.janaberger.lehrzeit.ui.components.SwitchDialog
import de.janaberger.lehrzeit.ui.components.displayName
import de.janaberger.lehrzeit.ui.components.label
import de.janaberger.lehrzeit.ui.components.minLengthText
import de.janaberger.lehrzeit.ui.components.rememberNow
import de.janaberger.lehrzeit.ui.components.timeOfDay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    onManageActivities: () -> Unit,
    onEditEntry: (id: Long) -> Unit,
    viewModel: TrackViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(TrackingNotifier.areEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsEnabled = TrackingNotifier.areEnabled(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsEnabled = TrackingNotifier.areEnabled(context)
        if (granted) viewModel.refreshNotification()
    }
    // Asks for the notification permission when tracking starts for the first time (Android 13+).
    val select: (ComboKey) -> Unit = { key ->
        if (needsNotificationPermission(context)) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.onSelect(key)
    }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val now by rememberNow(ticking = state.running != null)
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
    DeletedEntryUndoEffect(deleted, snackbarHostState, viewModel::undoDelete, viewModel::forgetDeleted)
    val resources by rememberUpdatedState(LocalResources.current)
    LaunchedEffect(viewModel) {
        viewModel.discardedNotices.collect { seconds ->
            snackbarHostState.showSnackbar(resources.getString(R.string.discarded_short, minLengthText(resources, seconds)))
        }
    }
    LaunchedEffect(viewModel) {
        // A newer notice replaces the shown one; its undo only takes back the latest step.
        viewModel.shortenedNotices.collectLatest { notice ->
            val message = resources.getString(
                R.string.nudge_shortened,
                notice.combo.label(resources.getString(R.string.activity_general)),
                formatTimeOfDay(notice.newEnd, resources.configuration.locales[0]),
            )
            val result = snackbarHostState.showSnackbar(message, actionLabel = resources.getString(R.string.action_undo), duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoNudge(notice.nudge)
        }
    }
    // The entry whose note is being edited. Saving still works if it stopped in the meantime.
    var noteEntryId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_track)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Only relevant while there is something to show, and not before the first start asked for permission.
            if (!notificationsEnabled && (state.running != null || state.paused != null)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NotificationsOffBanner(onTurnOn = { openNotificationSettings(context) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                StatusCard(
                    state = state,
                    now = now,
                    onPause = viewModel::pause,
                    onStop = viewModel::stop,
                    onResume = viewModel::resume,
                    onDismissPaused = viewModel::dismissPaused,
                    onNudge = viewModel::nudgeStart,
                    onEditRunning = onEditEntry,
                    onEditNote = { noteEntryId = it.entryId },
                )
            }
            state.today?.let { day ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.track_today, formatHoursMinutes(totalWithin(state.todayEntries, day.startUtc, day.endUtc, now))),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(state.tiles, key = { "${it.key.activityId}-${it.key.classId}" }) { combo ->
                ComboTile(
                    combo = combo,
                    isRunning = state.running?.combo?.key == combo.key,
                    onClick = { select(combo.key) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.track_all_activities))
                }
            }
        }
    }

    if (showPicker) {
        state.snapshot?.let { snapshot ->
            PickerSheet(
                snapshot = snapshot,
                favorites = state.favorites,
                onSelect = { key ->
                    showPicker = false
                    select(key)
                },
                onToggleFavorite = viewModel::toggleFavorite,
                onManageActivities = {
                    showPicker = false
                    onManageActivities()
                },
                onDismiss = { showPicker = false },
            )
        }
    }

    val running = state.running
    val target = state.pendingSwitch
    if (running != null && target != null) {
        SwitchDialog(from = running.combo, to = target, onConfirm = viewModel::confirmSwitch, onDismiss = viewModel::cancelSwitch)
    }

    noteEntryId?.let { id ->
        NoteDialog(
            initial = running?.takeIf { it.entryId == id }?.note.orEmpty(),
            onSave = { note ->
                viewModel.setNote(id, note)
                noteEntryId = null
            },
            onDismiss = { noteEntryId = null },
        )
    }
}

private const val NUDGE_MINUTES = 5

@Composable
private fun StatusCard(
    state: TrackUiState,
    now: Long,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onResume: () -> Unit,
    onDismissPaused: () -> Unit,
    onNudge: (minutes: Int) -> Unit,
    onEditRunning: (entryId: Long) -> Unit,
    onEditNote: (RunningTimer) -> Unit,
) {
    val running = state.running
    val paused = state.paused
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (running != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                running != null -> {
                    ComboHeadline(running.combo)
                    Text(
                        formatElapsed(now - running.startUtc),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    val step = NUDGE_MINUTES * 60_000L
                    StartRow(
                        startUtc = running.startUtc,
                        canMoveEarlier = nudgedStart(running.startUtc, state.beforeRunning, -step, now) != null,
                        canMoveLater = nudgedStart(running.startUtc, state.beforeRunning, step, now) != null,
                        onNudge = onNudge,
                        onEdit = { onEditRunning(running.entryId) },
                    )
                    NoteButton(running.note, onClick = { onEditNote(running) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_pause), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_pause))
                        }
                        Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                            Icon(painterResource(R.drawable.ic_stop), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_stop))
                        }
                    }
                }
                paused != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.track_paused), style = MaterialTheme.typography.labelLarge)
                            ComboHeadline(paused.combo)
                            Text(stringResource(R.string.paused_at, timeOfDay(paused.pausedAt)))
                        }
                        IconButton(onClick = onDismissPaused) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_dismiss))
                        }
                    }
                    Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_resume))
                    }
                }
                else -> {
                    Text(stringResource(R.string.track_idle_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.track_idle_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** "Start [−5] 10:15 [+5]": the buttons sit on either side of the time they move. Tapping the time opens the editor. */
@Composable
private fun StartRow(
    startUtc: Long,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onNudge: (minutes: Int) -> Unit,
    onEdit: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.track_start), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(12.dp))
        NudgeButton(R.string.nudge_earlier, R.string.nudge_earlier_description, enabled = canMoveEarlier) { onNudge(-NUDGE_MINUTES) }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(timeOfDay(startUtc), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(16.dp))
        }
        NudgeButton(R.string.nudge_later, R.string.nudge_later_description, enabled = canMoveLater) { onNudge(NUDGE_MINUTES) }
    }
}

@Composable
private fun NudgeButton(@StringRes label: Int, @StringRes description: Int, enabled: Boolean, onClick: () -> Unit) {
    val descriptionText = stringResource(description)
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp)) {
        // The button merges its content, so this replaces the bare "−5" for screen readers.
        Text(stringResource(label), modifier = Modifier.clearAndSetSemantics { contentDescription = descriptionText })
    }
}

/** The running entry's note, or "Add note"; either opens the note dialog. */
@Composable
private fun NoteButton(note: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        // A note reads as text, the invitation to add one as a button.
        colors = if (note.isEmpty()) ButtonDefaults.textButtonColors() else ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_note),
            contentDescription = if (note.isEmpty()) null else stringResource(R.string.entry_note),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(note.ifEmpty { stringResource(R.string.note_add) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoteDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(initial.length)))
    }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.entry_note)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.text.length <= MAX_NOTE_LENGTH) text = it },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ComboHeadline(combo: Combo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(combo.activity.color, size = 12.dp)
        Spacer(Modifier.width(8.dp))
        Text(combo.label(), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ComboTile(combo: Combo, isRunning: Boolean, onClick: () -> Unit) {
    val runningDescription = stringResource(R.string.track_running)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = if (isRunning) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { if (isRunning) stateDescription = runningDescription },
    ) {
        Row(Modifier.fillMaxHeight()) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color(combo.activity.color)),
            )
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    combo.activity.displayName(),
                    // Long German words like "Unterrichtsvorbereitung" need a hyphen to fit two lines.
                    style = MaterialTheme.typography.titleMedium.copy(hyphens = Hyphens.Auto),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                combo.schoolClass?.let {
                    Text(
                        it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsOffBanner(onTurnOn: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.notifications_off),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onTurnOn) { Text(stringResource(R.string.action_turn_on)) }
        }
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
