package ca.gibert.tvtracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.tvtracker.BuildConfig
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.DEFAULT_PLEX_ADDRESS
import ca.gibert.tvtracker.data.User
import ca.gibert.tvtracker.ui.components.Pill
import ca.gibert.tvtracker.ui.openUrl
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.LocalZenColors
import coil.compose.AsyncImage

private const val TRAKT_URL = "https://trakt.tv"
private const val TRAKT_API_APPS_URL = "https://app.trakt.tv/settings/apps/api"

@Composable
fun SettingsScreen(
    container: AppContainer,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var confirmForgetApp by rememberSaveable { mutableStateOf(false) }
    var editingPlexAddress by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileCard(
                profile = state.profile,
                isLoading = state.isLoadingProfile,
                error = state.profileError,
                onRetry = viewModel::refreshProfile,
            )

            SettingsSection(title = "Account") {
                SettingsRow(
                    icon = Icons.Outlined.Tv,
                    title = "Trakt API app",
                    subtitle = state.clientId.takeIf { it.isNotBlank() }?.let(::maskClientId)
                        ?: "Not configured",
                    onClick = { confirmForgetApp = true },
                    trailing = "Change",
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 67.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    title = "Disconnect account",
                    subtitle = "Sign out and remove saved data from this device",
                    onClick = { confirmSignOut = true },
                    enabled = !state.isBusy,
                )
            }

            if (state.hiddenShows.isNotEmpty()) {
                SettingsSection(title = "Hidden shows") {
                    state.hiddenShows
                        .sortedBy { it.title.lowercase() }
                        .forEachIndexed { index, show ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 67.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.5f),
                                )
                            }
                            SettingsRow(
                                icon = Icons.Outlined.VisibilityOff,
                                title = show.title,
                                subtitle = "Hidden from To Watch and Coming Up",
                                onClick = { viewModel.unhide(show) },
                                trailing = "Show",
                            )
                        }
                }
            }

            if (state.networkOverrides.isNotEmpty()) {
                SettingsSection(title = "Pinned channels") {
                    state.networkOverrides
                        .sortedBy { it.title.lowercase() }
                        .forEachIndexed { index, override ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 67.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                        .copy(alpha = 0.5f),
                                )
                            }
                            SettingsRow(
                                icon = Icons.Outlined.LiveTv,
                                title = override.title,
                                subtitle = "Shown as ${override.network}",
                                onClick = { viewModel.resetNetwork(override) },
                                trailing = "Reset",
                            )
                        }
                }
            }

            SettingsSection(title = "Plex") {
                SettingsRow(
                    icon = Icons.Outlined.LiveTv,
                    title = "Plex address",
                    subtitle = state.plexAddress
                        ?: "${DEFAULT_PLEX_ADDRESS.substringAfter("://")} (default)",
                    onClick = { editingPlexAddress = true },
                    trailing = "Change",
                )
            }

            SettingsSection(title = "Saved data") {
                SettingsRow(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Clear cached shows",
                    subtitle = "Lists refresh the next time you open them",
                    onClick = viewModel::clearCache,
                    enabled = !state.isBusy,
                )
            }

            SettingsSection(title = "About") {
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    title = "TV Tracker",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · shows only",
                    showChevron = false,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 67.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Open Trakt",
                    subtitle = TRAKT_URL,
                    onClick = { context.openUrl(TRAKT_URL) },
                    showChevron = false,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 67.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = "Manage API apps on Trakt",
                    subtitle = TRAKT_API_APPS_URL,
                    onClick = { context.openUrl(TRAKT_API_APPS_URL) },
                    showChevron = false,
                )
            }

            Text(
                text = "Unofficial app. Your Trakt username, password and tokens never " +
                    "leave this device except to talk to Trakt's API.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Disconnect from Trakt?",
            message = "You'll need to approve this app again to see your shows. " +
                "Saved data on this device will be cleared.",
            confirmLabel = "Disconnect",
            onConfirm = {
                confirmSignOut = false
                viewModel.signOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }

    if (editingPlexAddress) {
        PlexAddressDialog(
            current = state.plexAddress,
            onSave = { address ->
                editingPlexAddress = false
                viewModel.setPlexAddress(address)
            },
            onDismiss = { editingPlexAddress = false },
        )
    }

    if (confirmForgetApp) {
        ConfirmDialog(
            title = "Change the Trakt API app?",
            message = "This disconnects your account and removes the saved API app " +
                "settings from this device. You'll be asked for a Client ID again.",
            confirmLabel = "Change app",
            onConfirm = {
                confirmForgetApp = false
                viewModel.forgetApiApp()
            },
            onDismiss = { confirmForgetApp = false },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: User?,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val zen = LocalZenColors.current

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        brush = zen.iridescentBrush,
        strong = true,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(profile)

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                when {
                    profile != null -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile.name?.takeIf { it.isNotBlank() }
                                    ?: profile.username,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                                color = zen.onAccent,
                            )
                            if (profile.vip) {
                                Spacer(Modifier.width(8.dp))
                                Pill("VIP")
                            }
                        }
                        Text(
                            text = "@${profile.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = zen.onAccent.copy(alpha = 0.64f),
                        )
                    }

                    isLoading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = zen.onAccent,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Loading your Trakt profile…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = zen.onAccent.copy(alpha = 0.72f),
                            )
                        }
                    }

                    else -> {
                        Text(
                            text = "Trakt account connected",
                            style = MaterialTheme.typography.titleSmall,
                            color = zen.onAccent,
                        )
                        if (error != null) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = zen.onAccent.copy(alpha = 0.64f),
                            )
                        }
                    }
                }
            }

            if (error != null && !isLoading) {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Avatar(profile: User?) {
    val zen = LocalZenColors.current
    val avatar = profile?.images?.avatar?.full
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(zen.logoBrush)
            .border(2.dp, zen.glassBorderStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = zen.onAccent,
            modifier = Modifier.size(30.dp),
        )
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            strong = true,
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val zen = LocalZenColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (enabled) {
                        zen.logoBrush
                    } else {
                        androidx.compose.ui.graphics.SolidColor(colors.surfaceContainerHigh)
                    },
                )
                .border(
                    1.dp,
                    if (enabled) zen.glassBorder else colors.outlineVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) zen.onAccent else colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        } else if (onClick != null && showChevron) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Edits where "Open in Plex" goes for shows pinned to Plex: a home server
 * (`192.168.1.10:32400`) or Plex's hosted app when left blank.
 */
@Composable
private fun PlexAddressDialog(
    current: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(current.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plex address") },
        text = {
            Column {
                Text(
                    text = "Shows whose channel is Plex get an \"Open in Plex\" button that " +
                        "searches this server. Leave it blank to use app.plex.tv.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.10:32400") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onSave(address) },
                    ),
                    supportingText = {
                        Text("Without http:// or https://, http:// is assumed.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(address) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = { onSave("") }) {
                        Text("Use default")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun maskClientId(clientId: String): String {
    if (clientId.length <= 12) return clientId
    return "${clientId.take(6)}…${clientId.takeLast(4)}"
}
