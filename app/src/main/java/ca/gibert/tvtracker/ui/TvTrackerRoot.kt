package ca.gibert.tvtracker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.AuthState
import ca.gibert.tvtracker.ui.auth.AuthScreen
import ca.gibert.tvtracker.ui.detail.ShowDetailScreen
import ca.gibert.tvtracker.ui.settings.SettingsScreen
import ca.gibert.tvtracker.ui.upcoming.UpcomingScreen
import ca.gibert.tvtracker.ui.watch.WatchScreen

private object Routes {
    const val WATCH = "watch"
    const val UPCOMING = "upcoming"
    const val SETTINGS = "settings"
    const val SHOW = "show/{showId}"
    const val SHOW_ARG = "showId"

    fun show(showId: Int) = "show/$showId"
}

private data class TabDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabDestination(Routes.WATCH, "To Watch", Icons.Outlined.CheckCircle),
    TabDestination(Routes.UPCOMING, "Coming Up", Icons.Outlined.CalendarMonth),
)

@Composable
fun TvTrackerRoot(container: AppContainer) {
    val authState by container.authRepository.state.collectAsStateWithLifecycle()

    when (val state = authState) {
        AuthState.Loading -> LoadingScreen()
        AuthState.NeedsClientId -> AuthScreen(container = container, loggedOut = null)
        is AuthState.LoggedOut -> AuthScreen(container = container, loggedOut = state)
        is AuthState.LoggedIn -> MainScaffold(container = container)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isDetail = currentRoute == Routes.SHOW
    val isTab = tabs.any { it.route == currentRoute }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            Routes.WATCH -> "To Watch"
                            Routes.UPCOMING -> "Coming Up"
                            Routes.SETTINGS -> "Settings"
                            Routes.SHOW -> "Show details"
                            else -> "TV Tracker"
                        },
                    )
                },
                navigationIcon = {
                    if (!isTab && currentRoute != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (isTab) {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WATCH,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(Routes.WATCH) {
                WatchScreen(
                    container = container,
                    onOpenShow = { showId ->
                        navController.navigate(Routes.show(showId))
                    },
                )
            }

            composable(Routes.UPCOMING) {
                UpcomingScreen(
                    container = container,
                    onOpenShow = { showId ->
                        navController.navigate(Routes.show(showId))
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(container = container)
            }

            composable(
                route = Routes.SHOW,
                arguments = listOf(
                    navArgument(Routes.SHOW_ARG) { type = NavType.IntType },
                ),
            ) { entry ->
                val showId = entry.arguments?.getInt(Routes.SHOW_ARG) ?: return@composable
                ShowDetailScreen(
                    container = container,
                    showId = showId,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
