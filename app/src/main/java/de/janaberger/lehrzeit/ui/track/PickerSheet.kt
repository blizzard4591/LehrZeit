// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.CatalogSnapshot
import de.janaberger.lehrzeit.data.ComboKey
import de.janaberger.lehrzeit.data.db.Account
import de.janaberger.lehrzeit.data.db.WorkActivity
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.NoteText

private data class PickerGroup(val account: Account, val activities: List<WorkActivity>)

/**
 * All bookable accounts with their activities. Tapping an account books it alone; tapping an
 * activity books the combination. The star pins a combination to the home screen; it and the
 * "Manage accounts" link are left out when their callback is null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(
    snapshot: CatalogSnapshot,
    favorites: Set<ComboKey>,
    onSelect: (ComboKey) -> Unit,
    onToggleFavorite: ((ComboKey) -> Unit)?,
    onManageAccounts: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    val generalName = stringResource(R.string.account_general)
    val groups = pickerGroups(snapshot, query.trim(), generalName)
    val searching = query.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty") { NoteText(stringResource(R.string.picker_no_results)) }
            }
            groups.forEach { group ->
                val account = group.account
                val isExpanded = searching || account.id in expanded
                item(key = "account-${account.id}") {
                    val key = ComboKey(account.id, null)
                    ListItem(
                        headlineContent = { Text(if (account.isGeneral) generalName else account.name) },
                        leadingContent = { ColorDot(account.color) },
                        trailingContent = {
                            Row {
                                // General is always on the home screen, so it can't be pinned.
                                if (!account.isGeneral && onToggleFavorite != null) {
                                    FavoriteButton(isFavorite = key in favorites, onClick = { onToggleFavorite(key) })
                                }
                                if (group.activities.isNotEmpty() && !searching) {
                                    IconButton(onClick = { expanded = if (isExpanded) expanded - account.id else expanded + account.id }) {
                                        Icon(
                                            painterResource(if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                                            contentDescription = stringResource(if (isExpanded) R.string.picker_collapse else R.string.picker_expand),
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onSelect(key) },
                    )
                }
                if (isExpanded) {
                    items(group.activities, key = { "activity-${account.id}-${it.id}" }) { activity ->
                        val key = ComboKey(account.id, activity.id)
                        ListItem(
                            headlineContent = { Text(activity.name) },
                            leadingContent = { Spacer(Modifier.width(24.dp)) },
                            trailingContent = onToggleFavorite?.let { toggle ->
                                { FavoriteButton(isFavorite = key in favorites, onClick = { toggle(key) }) }
                            },
                            modifier = Modifier.clickable { onSelect(key) },
                        )
                    }
                }
            }
            if (onManageAccounts != null) {
                item(key = "manage") {
                    TextButton(onClick = onManageAccounts, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.picker_manage))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = stringResource(if (isFavorite) R.string.picker_unpin else R.string.picker_pin),
        )
    }
}

/**
 * Active accounts with their offered activities. While searching, an account matching the query
 * shows all its activities; otherwise only the matching activities are shown.
 */
private fun pickerGroups(snapshot: CatalogSnapshot, query: String, generalName: String): List<PickerGroup> =
    snapshot.activeAccounts.mapNotNull { account ->
        val activities = snapshot.activeActivitiesFor(account.id)
        val accountName = if (account.isGeneral) generalName else account.name
        when {
            query.isEmpty() || accountName.contains(query, ignoreCase = true) -> PickerGroup(account, activities)
            else -> activities.filter { it.name.contains(query, ignoreCase = true) }
                .takeIf { it.isNotEmpty() }
                ?.let { PickerGroup(account, it) }
        }
    }
