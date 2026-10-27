// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.catalog

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.data.db.WorkActivity
import de.janaberger.lehrzeit.ui.AppViewModelProvider
import de.janaberger.lehrzeit.ui.components.BackTopAppBar
import de.janaberger.lehrzeit.ui.components.ColorDot
import de.janaberger.lehrzeit.ui.components.DragHandleIcon
import de.janaberger.lehrzeit.ui.components.NoteText
import de.janaberger.lehrzeit.ui.components.archivedSection
import de.janaberger.lehrzeit.ui.components.displayName
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ActivityListScreen(
    onBack: () -> Unit,
    onOpenActivity: (id: Long?) -> Unit,
    viewModel: ActivityListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Local copy that follows the finger while dragging; the database is written when the drag ends.
    var active by remember(state.active) { mutableStateOf(state.active) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = active.indexOfFirst { it.id == from.key }
        val toIndex = active.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            active = active.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        topBar = { BackTopAppBar(stringResource(R.string.activities_title), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenActivity(null) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.activities_add))
            }
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            state.general?.let { general ->
                item(key = "general") {
                    ListItem(
                        headlineContent = { Text(general.displayName()) },
                        supportingContent = { Text(stringResource(R.string.activities_general_summary)) },
                        leadingContent = { ColorDot(general.color) },
                        modifier = Modifier.clickable { onOpenActivity(general.id) },
                    )
                }
            }
            if (active.isEmpty()) {
                item(key = "empty") { NoteText(stringResource(R.string.activities_empty)) }
            }
            items(active, key = { it.id }) { item ->
                ReorderableItem(reorderState, key = item.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp, label = "dragElevation")
                    Surface(shadowElevation = elevation) {
                        ActivityRow(
                            item = item,
                            onClick = { onOpenActivity(item.id) },
                            trailing = {
                                DragHandleIcon(
                                    Modifier.draggableHandle(
                                        onDragStopped = { viewModel.saveOrder(active.map { it.id }) },
                                    ),
                                )
                            },
                        )
                    }
                }
            }
            archivedSection(
                count = state.archived.size,
                expanded = showArchived,
                onToggle = { showArchived = !showArchived },
            ) {
                items(state.archived, key = { "archived-${it.id}" }) { item ->
                    ActivityRow(item = item, onClick = { onOpenActivity(item.id) })
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(
    item: WorkActivity,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(item.displayName()) },
        supportingContent = { Text(stringResource(if (item.perClass) R.string.activity_per_class else R.string.activity_no_classes)) },
        leadingContent = { ColorDot(item.color) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
