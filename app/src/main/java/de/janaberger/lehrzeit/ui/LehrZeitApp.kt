// SPDX-FileCopyrightText: 2026 Jana Berger
// SPDX-License-Identifier: GPL-3.0-or-later

package de.janaberger.lehrzeit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import de.janaberger.lehrzeit.R
import de.janaberger.lehrzeit.ui.catalog.AccountEditScreen
import de.janaberger.lehrzeit.ui.catalog.AccountListScreen
import de.janaberger.lehrzeit.ui.catalog.ActivityEditScreen
import de.janaberger.lehrzeit.ui.catalog.ActivityListScreen
import de.janaberger.lehrzeit.ui.more.MoreScreen
import de.janaberger.lehrzeit.ui.navigation.AccountEditRoute
import de.janaberger.lehrzeit.ui.navigation.AccountsRoute
import de.janaberger.lehrzeit.ui.navigation.ActivitiesRoute
import de.janaberger.lehrzeit.ui.navigation.ActivityEditRoute
import de.janaberger.lehrzeit.ui.navigation.MoreGraph
import de.janaberger.lehrzeit.ui.navigation.MoreRoute
import de.janaberger.lehrzeit.ui.navigation.SettingsRoute
import de.janaberger.lehrzeit.ui.settings.SettingsScreen
import de.janaberger.lehrzeit.ui.navigation.StatsRoute
import de.janaberger.lehrzeit.ui.navigation.TopLevelDestination
import de.janaberger.lehrzeit.ui.navigation.TrackRoute
import de.janaberger.lehrzeit.ui.navigation.WorklogRoute
import de.janaberger.lehrzeit.ui.track.TrackScreen
import de.janaberger.lehrzeit.ui.welcome.WelcomeScreen

@Composable
fun LehrZeitApp(appViewModel: AppViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val onboardingCompleted by appViewModel.onboardingCompleted.collectAsStateWithLifecycle()
    when (onboardingCompleted) {
        null -> Surface(Modifier.fillMaxSize()) {}
        false -> WelcomeScreen()
        true -> MainScreen()
    }
}

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (selected) {
                                navController.popBackStack(destination.rootRoute, inclusive = false)
                            } else {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TrackRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<TrackRoute> { TrackScreen(onManageAccounts = { navController.navigate(AccountsRoute) }) }
            composable<WorklogRoute> { PlaceholderScreen(stringResource(R.string.nav_worklog)) }
            composable<StatsRoute> { PlaceholderScreen(stringResource(R.string.nav_stats)) }
            navigation<MoreGraph>(startDestination = MoreRoute) {
                composable<MoreRoute> {
                    MoreScreen(
                        onOpenAccounts = { navController.navigate(AccountsRoute) },
                        onOpenActivities = { navController.navigate(ActivitiesRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<AccountsRoute> {
                    AccountListScreen(
                        onBack = navController::navigateUp,
                        onOpenAccount = { id -> navController.navigate(AccountEditRoute(id)) },
                    )
                }
                composable<AccountEditRoute> { AccountEditScreen(onDone = navController::navigateUp) }
                composable<ActivitiesRoute> {
                    ActivityListScreen(
                        onBack = navController::navigateUp,
                        onOpenActivity = { id -> navController.navigate(ActivityEditRoute(id)) },
                    )
                }
                composable<ActivityEditRoute> { ActivityEditScreen(onDone = navController::navigateUp) }
                composable<SettingsRoute> { SettingsScreen(onBack = navController::navigateUp) }
            }
        }
    }
}

/** Stands in for a tab until its real screen is built. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(title: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.placeholder_coming_soon),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
