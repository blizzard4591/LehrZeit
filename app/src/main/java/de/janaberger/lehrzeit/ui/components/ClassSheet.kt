// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.Combo
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.db.SchoolClass
import de.janaberger.lehrzeit.data.db.WorkActivity

/**
 * Which class to book [activity] on, after its tile was tapped on the Track screen or in the widget.
 * While [running] is set, the sheet says that it stops: picking a row switches at once, without the
 * "Switch?" question, because the pick already is a second, deliberate tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassSheet(
    activity: WorkActivity,
    classes: List<SchoolClass>,
    running: Combo?,
    onSelect: (ComboKey) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ClassChoices(
            activity = activity,
            classes = classes,
            selected = running?.key,
            onSelect = onSelect,
            subtitle = running?.let { stringResource(R.string.class_sheet_stops, it.label()) },
        )
    }
}

/**
 * The class step of a booking: the activity as a header, then "Without class" and the classes.
 * The row for [selected] has a check mark. [onBack] adds a back arrow, for the entry editor's picker.
 */
@Composable
fun ClassChoices(
    activity: WorkActivity,
    classes: List<SchoolClass>,
    selected: ComboKey?,
    onSelect: (ComboKey) -> Unit,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    LazyColumn {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, bottom = 8.dp),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                ColorDot(activity.color, size = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(activity.displayName(), style = MaterialTheme.typography.titleMedium)
                    subtitle?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider()
        }
        // null stands for "Without class".
        items(listOf(null) + classes, key = { "class-${it?.id ?: "none"}" }) { schoolClass ->
            val key = ComboKey(activity.id, schoolClass?.id)
            ChoiceRow(
                text = schoolClass?.name ?: stringResource(R.string.without_class),
                isSelected = key == selected,
                onClick = { onSelect(key) },
            )
        }
    }
}

/** A row in a booking picker; the chosen one has a check mark. */
@Composable
fun ChoiceRow(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable (() -> Unit)? = null,
    hasNextStep: Boolean = false,
) {
    ListItem(
        headlineContent = { Text(text) },
        leadingContent = leadingContent,
        trailingContent = if (isSelected || hasNextStep) {
            {
                Row {
                    if (isSelected) Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                    if (hasNextStep) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        } else {
            null
        },
        // Lets the sheet's own color through, like its header.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        // A row leading to the classes isn't a final choice, so it isn't announced as a radio button.
        modifier = Modifier.selectable(selected = isSelected, role = if (hasNextStep) null else Role.RadioButton, onClick = onClick),
    )
}
