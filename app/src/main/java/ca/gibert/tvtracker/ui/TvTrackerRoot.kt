package ca.gibert.tvtracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val isTab = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    AppTopBarTitle(isTab = isTab, currentRoute = currentRoute)
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
                        FilledTonalIconButton(
                            onClick = { navController.navigate(Routes.SETTINGS) },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        bottomBar = {
            if (isTab) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        ),
                    ) {
                        NavigationBar(
                            modifier = Modifier.height(76.dp),
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
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
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
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
private fun AppTopBarTitle(isTab: Boolean, currentRoute: String?) {
    if (isTab) {
        val colors = MaterialTheme.colorScheme
        val subtitle = if (currentRoute == Routes.UPCOMING) "Next 14 days" else "Your queue"

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.primary, colors.tertiary),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "TV Tracker",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    } else {
        Text(
            text = when (currentRoute) {
                Routes.SETTINGS -> "Settings"
                Routes.SHOW -> "Show details"
                else -> "TV Tracker"
            },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
