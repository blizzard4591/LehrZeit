// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.janaberger.lehrzeit.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    onOpenAccounts: () -> Unit,
    onOpenActivities: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            MoreItem(stringResource(R.string.accounts_title), stringResource(R.string.more_accounts_summary), onOpenAccounts)
            MoreItem(stringResource(R.string.activities_title), stringResource(R.string.more_activities_summary), onOpenActivities)
            MoreItem(stringResource(R.string.export_title), stringResource(R.string.more_export_summary), onOpenExport)
            MoreItem(stringResource(R.string.backup_title), stringResource(R.string.more_backup_summary), onOpenBackup)
            MoreItem(stringResource(R.string.settings_title), stringResource(R.string.more_settings_summary), onOpenSettings)
        }
    }
}

@Composable
private fun MoreItem(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
