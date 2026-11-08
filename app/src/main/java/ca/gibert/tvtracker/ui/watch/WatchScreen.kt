package ca.gibert.tvtracker.ui.watch

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.Episode
import ca.gibert.tvtracker.data.Show
import ca.gibert.tvtracker.data.WatchItem
import ca.gibert.tvtracker.data.withNetworkOverride
import ca.gibert.tvtracker.ui.components.CachedBanner
import ca.gibert.tvtracker.ui.components.EmptyState
import ca.gibert.tvtracker.ui.components.ErrorState
import ca.gibert.tvtracker.ui.components.Pill
import ca.gibert.tvtracker.ui.components.PosterImage
import ca.gibert.tvtracker.ui.components.ServiceLine
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.ZenIconButton
import ca.gibert.tvtracker.util.episodeCode
import ca.gibert.tvtracker.util.formatShortDate
import ca.gibert.tvtracker.util.isAired
import ca.gibert.tvtracker.util.parseTraktInstant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    container: AppContainer,
    onOpenShow: (Int) -> Unit,
    viewModel: WatchViewModel = viewModel(factory = WatchViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WatchEvent.Marked -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "Marked ${event.label} watched",
                        actionLabel = "Undo",
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoMark(event.episodeId)
                    }
                }

                is WatchEvent.Message -> {
                    snackbarHostState.showSnackbar(event.text)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Items are kept unfiltered in state so an unhide shows up even when
            // a refresh can't reach Trakt; the list itself is filtered here.
            val items = state.items
                .filter { it.show.ids.trakt !in state.hiddenIds }
                .map { it.withNetworkOverride(state.networkOverrides) }

            when {
                state.isLoading && items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                items.isEmpty() && state.errorMessage != null -> {
                    ErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.CheckCircle,
                            title = "You're all caught up",
                            message = "Shows you watch and shows on your Trakt watchlist will " +
                                "appear here with the next episode to watch.",
                            action = {
                                androidx.compose.material3.Button(onClick = viewModel::refresh) {
                                    Text("Check again")
                                }
                            },
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "header") {
                            WatchHeader()
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = state.lastUpdated)
                            }
                        }

                        items(
                            items = items,
                            key = { item -> watchItemKey(item) },
                        ) { item ->
                            WatchCard(
                                item = item,
                                isMarking = when (item) {
                                    is WatchItem.UpNext -> item.progress.nextEpisode
                                        ?.ids
                                        ?.trakt in state.markingEpisodeIds

                                    is WatchItem.Watchlisted -> false
                                },
                                onClick = { onOpenShow(item.show.ids.trakt) },
                                onMarkWatched = {
                                    if (item is WatchItem.UpNext) {
                                        viewModel.markWatched(item)
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        item(key = "footer") {
                            LastUpdatedFooter(state.lastUpdated)
                        }
                    }
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
}

@Composable
private fun WatchHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        Text(
            text = "YOUR QUEUE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "To Watch",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun WatchCard(
    item: WatchItem,
    isMarking: Boolean,
    onClick: () -> Unit,
    onMarkWatched: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val show = item.show
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                show = show,
                modifier = Modifier
                    .width(64.dp)
                    .height(96.dp),
                cornerRadius = 14.dp,
            )

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = show.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (item is WatchItem.UpNext) {
                        Pill("Next up", emphasis = true)
                    } else {
                        Pill("Watchlist")
                    }
                }

                Spacer(Modifier.height(3.dp))

                when (item) {
                    is WatchItem.UpNext -> {
                        val episode = item.progress.nextEpisode
                        if (episode != null) {
                            EpisodeSummary(episode)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = airLabel(episode),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                            // "Next up" cards never used to show the network,
                            // so this is where the service becomes visible.
                            if (!show.network.isNullOrBlank()) {
                                Spacer(Modifier.height(6.dp))
                                ServiceLine(network = show.network)
                            }
                            Spacer(Modifier.height(10.dp))
                            ProgressBar(
                                completed = item.progress.completed,
                                aired = item.progress.aired,
                            )
                        } else if (!show.network.isNullOrBlank()) {
                            ServiceLine(network = show.network)
                        }
                    }

                    is WatchItem.Watchlisted -> {
                        Text(
                            text = "On your watchlist",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        ServiceLine(
                            network = show.network,
                            prefix = show.year?.toString(),
                            fallback = "Tap to see episodes",
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            when (item) {
                is WatchItem.UpNext -> {
                    val episode = item.progress.nextEpisode
                    ZenIconButton(
                        onClick = onMarkWatched,
                        enabled = !isMarking,
                    ) {
                        if (isMarking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.onSurface,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = episode?.let {
                                    "Mark ${episodeCode(it.season, it.number)} watched"
                                } ?: "Mark watched",
                                tint = colors.onSurface,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                is WatchItem.Watchlisted -> {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeSummary(episode: Episode) {
    val title = episode.title?.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}"
    Text(
        text = "${episodeCode(episode.season, episode.number)} · $title",
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProgressBar(completed: Int, aired: Int) {
    val fraction = if (aired > 0) {
        (completed.toFloat() / aired.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        // Material3 draws a "stop indicator" dot at the track's right end;
        // it reads as a stray pixel next to our cards, so skip it.
        drawStopIndicator = {},
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "$completed of $aired aired watched",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun airLabel(episode: Episode): String {
    val instant = parseTraktInstant(episode.firstAired) ?: return "Air date unknown"
    return if (isAired(episode.firstAired)) {
        "Aired ${formatShortDate(instant)}"
    } else {
        "Airs ${formatShortDate(instant)}"
    }
}

@Composable
private fun LastUpdatedFooter(lastUpdated: Long?) {
    if (lastUpdated == null) return
    val relative = DateUtils.getRelativeTimeSpanString(
        lastUpdated,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    )
    Text(
        text = "Updated $relative",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private fun watchItemKey(item: WatchItem): String = when (item) {
    is WatchItem.UpNext -> "up_next_${item.show.ids.trakt}"
    is WatchItem.Watchlisted -> "watchlist_${item.show.ids.trakt}"
}

private val WatchItem.show: Show
    get() = when (this) {
        is WatchItem.UpNext -> this.show
        is WatchItem.Watchlisted -> this.show
    }
