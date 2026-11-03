package ca.gibert.tvtracker.ui.upcoming

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import ca.gibert.tvtracker.ui.components.SectionHeader
import ca.gibert.tvtracker.ui.rememberIs24HourClock
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
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
private fun DayHeader(date: LocalDate, count: Int) {
    SectionHeader(
        title = formatRelativeDay(date),
        trailing = buildString {
            append(
                date.format(
                    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()),
                ),
            )
            append(" · ")
            append(count)
            append(if (count == 1) " episode" else " episodes")
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun UpcomingRow(
    item: CalendarItem,
    time: String,
    onClick: () -> Unit,
) {
    val episode = item.episode
    val colors = MaterialTheme.colorScheme

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterImage(
                show = item.show,
                modifier = Modifier
                    .width(48.dp)
                    .height(72.dp),
            )

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.show.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${episodeCode(episode.season, episode.number)} · ${
                        episode.title?.takeIf { it.isNotBlank() } ?: "Episode ${episode.number}"
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.show.network?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    episodeBadge(episode.episodeType)?.let { badge ->
                        Pill(text = badge, emphasis = true)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Text(
                text = time,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
            )
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
