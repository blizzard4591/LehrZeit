// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.ui.components.ChoiceRow
import de.janaberger.lehrzeit.ui.components.ClassChoices
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.displayName

/**
 * What an entry is booked on, in the same two steps as on the Track screen: first the activity,
 * then its class if it has any. The current choice, [selected], has check marks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    snapshot: CatalogSnapshot,
    selected: ComboKey?,
    onSelect: (ComboKey) -> Unit,
    onDismiss: () -> Unit,
) {
    // The activity whose classes are shown; null while the activities are.
    var classesOf by rememberSaveable { mutableStateOf<Long?>(null) }
    val activity = classesOf?.let { id -> snapshot.activeActivities.firstOrNull { it.id == id } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (activity != null) {
            BackHandler { classesOf = null }
            ClassChoices(
                activity = activity,
                classes = snapshot.activeClassesFor(activity.id),
                selected = selected,
                onSelect = onSelect,
                onBack = { classesOf = null },
            )
        } else {
            LazyColumn {
                items(snapshot.activeActivities, key = { it.id }) { item ->
                    val hasClasses = snapshot.activeClassesFor(item.id).isNotEmpty()
                    ChoiceRow(
                        text = item.displayName(),
                        isSelected = item.id == selected?.activityId,
                        onClick = { if (hasClasses) classesOf = item.id else onSelect(ComboKey(item.id, null)) },
                        leadingContent = { ColorDot(item.color) },
                        hasNextStep = hasClasses,
                    )
                }
            }
        }
    }
}
