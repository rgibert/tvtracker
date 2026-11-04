package ca.gibert.tvtracker.ui.upcoming

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.CalendarItem
import ca.gibert.tvtracker.ui.components.CachedBanner
import ca.gibert.tvtracker.ui.components.EmptyState
import ca.gibert.tvtracker.ui.components.ErrorState
import ca.gibert.tvtracker.ui.components.Pill
import ca.gibert.tvtracker.ui.components.PosterImage
import ca.gibert.tvtracker.ui.rememberIs24HourClock
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.LocalZenColors
import ca.gibert.tvtracker.util.episodeCode
import ca.gibert.tvtracker.util.formatRelativeDay
import ca.gibert.tvtracker.util.formatTime
import ca.gibert.tvtracker.util.localDate
import ca.gibert.tvtracker.util.parseTraktInstant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    container: AppContainer,
    onOpenShow: (Int) -> Unit,
    viewModel: UpcomingViewModel = viewModel(factory = UpcomingViewModel.factory(container)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val is24Hour = rememberIs24HourClock()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResume()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val grouped = remember(state.items) {
        state.items
            .mapNotNull { item ->
                parseTraktInstant(item.firstAired)?.let { instant -> instant to item }
            }
            .groupBy { (instant, _) -> instant.localDate() }
            .toSortedMap()
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.items.isEmpty() && state.errorMessage != null -> {
                    ErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                state.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Outlined.CalendarMonth,
                            title = "Nothing coming up",
                            message = "Episodes airing in the next two weeks for shows you " +
                                "watch or have on your Trakt watchlist will appear here.",
                            action = {
                                Button(onClick = viewModel::refresh) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "header") {
                            UpcomingHeader(items = state.items)
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = state.lastUpdated)
                            }
                        }

                        grouped.forEach { (date, entries) ->
                            item(key = "day_$date") {
                                DayHeader(date = date, count = entries.size)
                            }
                            items(
                                items = entries,
                                key = { (_, item) -> "calendar_${item.show.ids.trakt}_${item.episode.ids.trakt}" },
                            ) { (instant, item) ->
                                UpcomingRow(
                                    item = item,
                                    time = formatTime(instant, is24Hour),
                                    onClick = { onOpenShow(item.show.ids.trakt) },
                                )
                            }
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
private fun UpcomingHeader(items: List<CalendarItem>) {
    val showCount = items.map { it.show.ids.trakt }.distinct().size
    val zen = LocalZenColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        Text(
            text = "YOUR CALENDAR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Coming Up",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                items.isEmpty() -> "Nothing scheduled in the next 14 days"
                showCount == 1 -> "${items.size} ${
                    if (items.size == 1) "episode" else "episodes"
                } from 1 show in the next 14 days"
                else -> "${items.size} episodes from $showCount shows in the next 14 days"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(zen.iridescentBrush)
                        .border(1.dp, zen.glassBorderStrong, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = zen.onAccent,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (items.size == 1) "1 episode" else "${items.size} episodes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "from $showCount ${if (showCount == 1) "show" else "shows"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(text = "14 days", emphasis = true)
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = formatRelativeDay(date),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Pill(text = "$count ${if (count == 1) "episode" else "episodes"}")
    }
}

@Composable
private fun UpcomingRow(
    item: CalendarItem,
    time: String,
    onClick: () -> Unit,
) {
    val episode = item.episode
    val colors = MaterialTheme.colorScheme

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                show = item.show,
                modifier = Modifier
                    .width(50.dp)
                    .height(75.dp),
                cornerRadius = 12.dp,
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.show.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${episodeCode(episode.season, episode.number)} · ${
                        episode.title?.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.show.network?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    episodeBadge(episode.episodeType)?.let { badge ->
                        Pill(text = badge, emphasis = true)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Pill(text = time, emphasis = true)
        }
    }
}

private fun episodeBadge(type: String?): String? = when (type) {
    "series_premiere" -> "Series premiere"
    "season_premiere", "mid_season_premiere" -> "Premiere"
    "series_finale" -> "Series finale"
    "season_finale", "mid_season_finale" -> "Finale"
    else -> null
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
