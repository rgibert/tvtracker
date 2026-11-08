package ca.gibert.tvtracker.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.DetailEpisode
import ca.gibert.tvtracker.data.DetailSeason
import ca.gibert.tvtracker.data.Episode
import ca.gibert.tvtracker.data.Show
import ca.gibert.tvtracker.data.ShowDetail
import ca.gibert.tvtracker.ui.components.CachedBanner
import ca.gibert.tvtracker.ui.components.EmptyState
import ca.gibert.tvtracker.ui.components.EpisodeStill
import ca.gibert.tvtracker.ui.components.ErrorState
import ca.gibert.tvtracker.ui.components.Pill
import ca.gibert.tvtracker.ui.components.PosterImage
import ca.gibert.tvtracker.ui.components.ServiceBadge
import ca.gibert.tvtracker.ui.components.ShowBackdrop
import ca.gibert.tvtracker.ui.components.launchIntentFor
import ca.gibert.tvtracker.ui.components.streamingAppFor
import ca.gibert.tvtracker.ui.components.suggestedServices
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.LocalZenColors
import ca.gibert.tvtracker.util.episodeCode
import ca.gibert.tvtracker.util.formatShortDate
import ca.gibert.tvtracker.util.isAired
import ca.gibert.tvtracker.util.parseTraktInstant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowDetailScreen(
    container: AppContainer,
    showId: Int,
    viewModel: ShowDetailViewModel = viewModel(
        factory = ShowDetailViewModel.factory(container, showId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingChannel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val detail = state.displayDetail
            when {
                state.isLoading && detail == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                detail == null -> {
                    ErrorState(
                        message = state.errorMessage
                            ?: "This show couldn't be loaded.",
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                else -> {
                    DetailContent(
                        detail = detail,
                        selectedSeason = state.selectedSeason,
                        mutatingEpisodeIds = state.mutatingEpisodeIds,
                        showingCached = state.showingCached,
                        isHidden = state.isHidden,
                        traktNetwork = state.detail?.show?.network,
                        networkOverride = state.networkOverride,
                        onSelectSeason = viewModel::selectSeason,
                        onToggleEpisode = viewModel::toggleEpisode,
                        onToggleHidden = viewModel::toggleHidden,
                        onEditChannel = { editingChannel = true },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    if (editingChannel && state.detail != null) {
        ChannelDialog(
            traktNetwork = state.detail?.show?.network,
            networkOverride = state.networkOverride,
            onSave = { name ->
                editingChannel = false
                viewModel.setNetworkOverride(name)
            },
            onReset = {
                editingChannel = false
                viewModel.clearNetworkOverride()
            },
            onDismiss = { editingChannel = false },
        )
    }
}

@Composable
private fun DetailContent(
    detail: ShowDetail,
    selectedSeason: Int?,
    mutatingEpisodeIds: Set<Int>,
    showingCached: Boolean,
    isHidden: Boolean,
    traktNetwork: String?,
    networkOverride: String?,
    onSelectSeason: (Int) -> Unit,
    onToggleEpisode: (DetailEpisode) -> Unit,
    onToggleHidden: () -> Unit,
    onEditChannel: () -> Unit,
) {
    val season = detail.seasons.firstOrNull { it.number == selectedSeason }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (showingCached) {
                    CachedBanner(lastUpdated = detail.fetchedAt)
                    Spacer(Modifier.height(12.dp))
                }
                ShowHero(detail)
            }
        }

        item(key = "channel") {
            ChannelRow(
                traktNetwork = traktNetwork,
                networkOverride = networkOverride,
                onClick = onEditChannel,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // detail carries the pinned channel, so the app matches what is shown.
        item(key = "open_app") {
            OpenInAppRow(
                network = detail.show.network,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item(key = "list_visibility") {
            ListVisibilityRow(
                isHidden = isHidden,
                onToggle = onToggleHidden,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        detail.nextEpisode?.let { next ->
            item(key = "next_up") {
                NextEpisodeCard(
                    next = next,
                    isSaving = next.ids.trakt in mutatingEpisodeIds,
                    onMarkWatched = {
                        val target = detail.seasons
                            .asSequence()
                            .flatMap { it.episodes.asSequence() }
                            .firstOrNull { it.episode.ids.trakt == next.ids.trakt }
                            ?: DetailEpisode(episode = next, completed = false)
                        onToggleEpisode(target)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        if (detail.seasons.isEmpty()) {
            item(key = "no_episodes") {
                EmptyState(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    title = "No episodes yet",
                    message = "Trakt doesn't have episode information for this show yet.",
                )
            }
            return@LazyColumn
        }

        item(key = "seasons") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(detail.seasons, key = { it.number }) { item ->
                    val colors = MaterialTheme.colorScheme
                    val zen = LocalZenColors.current
                    FilterChip(
                        selected = item.number == selectedSeason,
                        onClick = { onSelectSeason(item.number) },
                        label = {
                            Text(
                                text = item.title?.takeIf { it.isNotBlank() }
                                    ?: "Season ${item.number}",
                            )
                        },
                        shape = CircleShape,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = zen.mutedFill,
                            labelColor = colors.onSurfaceVariant,
                            selectedContainerColor = colors.primary,
                            selectedLabelColor = colors.onPrimary,
                        ),
                    )
                }
            }
        }

        if (season != null) {
            item(key = "season_summary_${season.number}") {
                SeasonSummary(season)
            }

            items(
                items = season.episodes,
                key = { it.episode.ids.trakt },
            ) { episode ->
                EpisodeRow(
                    episode = episode,
                    enabled = episode.episode.ids.trakt !in mutatingEpisodeIds,
                    onToggle = { onToggleEpisode(episode) },
                )
            }
        }
    }
}

@Composable
private fun ShowHero(detail: ShowDetail) {
    val show = detail.show
    val colors = MaterialTheme.colorScheme
    val textOnHero = Color.White
    val posterShape = RoundedCornerShape(16.dp)

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        strong = true,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(244.dp),
            ) {
                ShowBackdrop(
                    show = show,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.35f to Color(0xFF07151A).copy(alpha = 0.18f),
                                1f to Color(0xFF061014).copy(alpha = 0.88f),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .width(92.dp)
                            .height(138.dp)
                            .clip(posterShape)
                            .border(1.dp, Color.White.copy(alpha = 0.5f), posterShape),
                    ) {
                        PosterImage(
                            show = show,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 16.dp,
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 2.dp),
                    ) {
                        Text(
                            text = show.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = textOnHero,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = showMeta(show),
                            style = MaterialTheme.typography.bodySmall,
                            color = textOnHero.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(Modifier.padding(18.dp)) {
                if (!show.overview.isNullOrBlank()) {
                    Text(
                        text = show.overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                val fraction = if (detail.airedEpisodes > 0) {
                    (detail.completedEpisodes.toFloat() / detail.airedEpisodes.toFloat())
                        .coerceIn(0f, 1f)
                } else {
                    0f
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${detail.completedEpisodes} of ${detail.airedEpisodes} " +
                            "aired episodes watched",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(fraction * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.08f),
                    strokeCap = StrokeCap.Round,
                    // No stop-indicator dot at the track's right end.
                    drawStopIndicator = {},
                )
            }
        }
    }
}

/**
 * "Open in <service>" for the show's network, shown only when that service's
 * app is installed. There's no per-title deep link we can rely on across
 * services, so this opens the app's home screen.
 */
@Composable
private fun OpenInAppRow(
    network: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember(network) { streamingAppFor(network) } ?: return
    val launchIntent = remember(app) { launchIntentFor(context, app) } ?: return
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = {
            // The app can be uninstalled between composition and the tap.
            runCatching { context.startActivity(launchIntent) }
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServiceBadge(network = network.orEmpty(), size = 20.dp)
            Spacer(Modifier.width(13.dp))
            Text(
                text = "Open in ${app.name}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ListVisibilityRow(
    isHidden: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isHidden) {
                    Icons.Outlined.Visibility
                } else {
                    Icons.Outlined.VisibilityOff
                },
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isHidden) "Show in my lists" else "Hide from my lists",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (isHidden) {
                        "Hidden from To Watch and Coming Up right now"
                    } else {
                        "Keeps it out of To Watch and Coming Up"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Shows which channel the app credits this show to and opens the picker.
 *
 * Trakt reports the original network, which is often not where the show airs
 * for the viewer, so the subtitle says whether the channel came from Trakt or
 * was pinned here (and what Trakt says, so the override is never a mystery).
 */
@Composable
private fun ChannelRow(
    traktNetwork: String?,
    networkOverride: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val trakt = traktNetwork?.trim()?.takeIf { it.isNotEmpty() }
    val channel = networkOverride ?: trakt

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServiceBadge(network = channel.orEmpty(), size = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = channel?.let { "Channel: $it" } ?: "Set a channel",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = when {
                        networkOverride != null && trakt != null ->
                            "Pinned by you · Trakt lists $trakt"

                        networkOverride != null -> "Pinned by you"
                        trakt != null -> "From Trakt · tap to pin where it airs for you"
                        else -> "Trakt doesn't list one for this show"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "Change channel",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelDialog(
    traktNetwork: String?,
    networkOverride: String?,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val trakt = traktNetwork?.trim()?.takeIf { it.isNotEmpty() }
    var name by rememberSaveable { mutableStateOf(networkOverride ?: trakt.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Channel") },
        text = {
            Column {
                Text(
                    text = if (trakt != null) {
                        "Trakt lists $trakt. Pick where this show airs for you; it's " +
                            "used on this page, To Watch and Coming Up."
                    } else {
                        "Pick where this show airs for you; it's used on this page, " +
                            "To Watch and Coming Up."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Channel name") },
                    singleLine = true,
                    leadingIcon = {
                        ServiceBadge(network = name.trim(), size = 22.dp)
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave(name) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                // Bounded and scrollable so the field and buttons stay on
                // screen on short displays or with large font sizes.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    suggestedServices.forEach { service ->
                        SuggestionChip(
                            onClick = { name = service },
                            label = { Text(service) },
                            icon = { ServiceBadge(network = service, size = 16.dp) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (networkOverride != null) {
                    TextButton(onClick = onReset) {
                        Text(if (trakt != null) "Use Trakt's" else "Remove")
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
private fun NextEpisodeCard(
    next: Episode,
    isSaving: Boolean,
    onMarkWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val zen = LocalZenColors.current

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        brush = zen.iridescentBrush,
        strong = true,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EpisodeStill(
                episode = next,
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f),
                cornerRadius = 12.dp,
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = zen.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "NEXT UP",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = zen.onAccent,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${episodeCode(next.season, next.number)} · ${
                        next.title?.takeIf { it.isNotBlank() } ?: "Episode ${next.number}"
                    }",
                    style = MaterialTheme.typography.titleMedium,
                    color = zen.onAccent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episodeAirLabel(next)?.let { label ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = zen.onAccent.copy(alpha = 0.72f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onMarkWatched,
                    enabled = !isSaving,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                    ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Mark watched")
                }
            }
        }
    }
}

@Composable
private fun SeasonSummary(season: DetailSeason) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = season.title?.takeIf { it.isNotBlank() } ?: "Season ${season.number}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Pill(text = "${season.completed}/${season.aired} watched")
    }
}

@Composable
private fun EpisodeRow(
    episode: DetailEpisode,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val item = episode.episode
    val aired = isAired(item.firstAired)

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = episode.completed,
                    enabled = enabled && aired,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = episode.completed,
                onCheckedChange = null,
                enabled = enabled && aired,
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.primary,
                    checkmarkColor = colors.onPrimary,
                    uncheckedColor = colors.outline,
                ),
            )
            Spacer(Modifier.width(6.dp))
            EpisodeStill(
                episode = item,
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(16f / 9f),
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = episodeCode(item.season, item.number),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (aired) colors.primary else colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.title?.takeIf { it.isNotBlank() } ?: "Episode ${item.number}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (aired) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episodeAirLabel(item)?.let { label ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun episodeAirLabel(episode: Episode): String? {
    val instant = parseTraktInstant(episode.firstAired) ?: return null
    return if (isAired(episode.firstAired)) {
        "Aired ${formatShortDate(instant)}"
    } else {
        "Airs ${formatShortDate(instant)}"
    }
}

private fun showMeta(show: Show): String = listOfNotNull(
    show.year?.toString(),
    show.network,
    show.status?.replaceFirstChar { it.uppercase() },
    show.genres.take(2).joinToString(", ").ifBlank { null },
).joinToString(" · ")
