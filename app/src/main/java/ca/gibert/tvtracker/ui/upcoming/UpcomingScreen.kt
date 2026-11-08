package ca.gibert.tvtracker.ui.upcoming

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import ca.gibert.tvtracker.ui.components.ServiceLine
import ca.gibert.tvtracker.ui.rememberIs24HourClock
import ca.gibert.tvtracker.ui.theme.GlassSurface
import ca.gibert.tvtracker.ui.theme.LocalZenColors
import ca.gibert.tvtracker.util.episodeCode
import ca.gibert.tvtracker.util.formatRelativeDay
import ca.gibert.tvtracker.util.formatTime
import ca.gibert.tvtracker.util.localDate
import ca.gibert.tvtracker.util.monthCalendarDays
import ca.gibert.tvtracker.util.parseTraktInstant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private enum class UpcomingViewMode(val label: String) {
    List("List"),
    Calendar("Calendar"),
}

private const val CALENDAR_MONTHS_AHEAD = 12L
private const val MONTH_SWIPE_THRESHOLD = 80f

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

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Items stay unfiltered in state so unhiding works even when the refresh
    // fails; this list is what every section below renders.
    val items = remember(state.items, state.hiddenIds) {
        state.items.filter { it.show.ids.trakt !in state.hiddenIds }
    }

    val grouped = remember(items) {
        items
            .mapNotNull { item ->
                parseTraktInstant(item.firstAired)?.let { instant -> instant to item }
            }
            .groupBy { (instant, _) -> instant.localDate() }
            .toSortedMap()
    }

    var viewMode by rememberSaveable { mutableStateOf(UpcomingViewMode.List) }

    val today = remember { LocalDate.now() }
    val currentMonth = remember(today) { YearMonth.from(today) }
    val monthLimit = remember(currentMonth) { currentMonth.plusMonths(CALENDAR_MONTHS_AHEAD) }
    val calendarMode = viewMode == UpcomingViewMode.Calendar

    val defaultSelectedDate = remember(grouped, today) {
        grouped.keys.firstOrNull { !it.isBefore(today) }
            ?: grouped.keys.firstOrNull()
            ?: today
    }

    // A negative sentinel means "no explicit selection yet", which lets the
    // default follow the data when the first refresh arrives.
    var selectedEpochDay by rememberSaveable { mutableStateOf(-1L) }
    var shownMonthText by rememberSaveable { mutableStateOf("") }
    val selectedDate = if (selectedEpochDay >= 0) {
        LocalDate.ofEpochDay(selectedEpochDay)
    } else {
        defaultSelectedDate
    }
    val shownMonth = shownMonthText
        .takeIf { it.isNotBlank() }
        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        ?.takeIf { !it.isBefore(currentMonth) && !it.isAfter(monthLimit) }
        ?: YearMonth.from(selectedDate)

    // Seed the displayed month from the two-week list while its full-month
    // snapshot loads, so switching to the calendar shows data immediately.
    val calendarSource = remember(state.calendarMonths, shownMonth, items, state.hiddenIds) {
        (state.calendarMonths[shownMonth] ?: items)
            .filter { it.show.ids.trakt !in state.hiddenIds }
    }
    val calendarGrouped = remember(calendarSource, shownMonth) {
        calendarSource
            .mapNotNull { item ->
                val instant = parseTraktInstant(item.firstAired) ?: return@mapNotNull null
                val date = instant.localDate()
                if (YearMonth.from(date) == shownMonth) date to (instant to item) else null
            }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
    }

    val selectedEntries = remember(calendarGrouped, selectedDate) {
        calendarGrouped[selectedDate].orEmpty()
    }
    val episodeCounts = remember(calendarGrouped) {
        calendarGrouped.mapValues { (_, entries) -> entries.size }
    }
    val calendarLoading = shownMonth in state.loadingMonths
    val calendarError = state.calendarErrors[shownMonth]
    val canGoPrevious = shownMonth > currentMonth
    val canGoNext = shownMonth < monthLimit

    fun selectDate(date: LocalDate) {
        selectedEpochDay = date.toEpochDay()
        shownMonthText = YearMonth.from(date).toString()
    }

    fun shiftMonth(offset: Long) {
        val target = shownMonth.plusMonths(offset)
        if (target < currentMonth || target > monthLimit) return
        shownMonthText = target.toString()

        val firstAiring = state.calendarMonths[target]
            .orEmpty()
            .mapNotNull { parseTraktInstant(it.firstAired)?.localDate() }
            .filter { YearMonth.from(it) == target }
            .minOrNull()
        selectedEpochDay = when {
            target == currentMonth -> today.toEpochDay()
            firstAiring != null -> firstAiring.toEpochDay()
            else -> target.atDay(1).toEpochDay()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (calendarMode) {
            viewModel.onCalendarResume(shownMonth)
        } else {
            viewModel.onResume()
        }
    }

    LaunchedEffect(calendarMode, shownMonth) {
        if (calendarMode) {
            viewModel.loadCalendarMonth(shownMonth)
        }
    }

    LaunchedEffect(calendarMode) {
        if (!calendarMode) viewModel.onResume()
    }

    // When a future month finishes loading, move the placeholder selection on
    // the first day to the first day that actually has an episode.
    LaunchedEffect(calendarGrouped, shownMonth) {
        if (selectedEpochDay == shownMonth.atDay(1).toEpochDay() &&
            calendarGrouped[selectedDate].isNullOrEmpty()
        ) {
            calendarGrouped.keys.firstOrNull()?.let { firstAiring ->
                selectedEpochDay = firstAiring.toEpochDay()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = if (calendarMode) calendarLoading else state.isRefreshing,
            onRefresh = {
                if (calendarMode) {
                    viewModel.loadCalendarMonth(shownMonth, force = true)
                } else {
                    viewModel.refresh()
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !calendarMode && state.isLoading && items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                !calendarMode && items.isEmpty() && state.errorMessage != null -> {
                    ErrorState(
                        message = state.errorMessage.orEmpty(),
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                !calendarMode && items.isEmpty() -> {
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
                            UpcomingHeader(
                                viewMode = viewMode,
                                onViewModeChange = { viewMode = it },
                            )
                        }

                        if (state.showingCached) {
                            item(key = "cached-banner") {
                                CachedBanner(lastUpdated = state.lastUpdated)
                            }
                        }

                        when (viewMode) {
                            UpcomingViewMode.List -> {
                                grouped.forEach { (date, entries) ->
                                    item(key = "day_$date") {
                                        DayHeader(date = date, count = entries.size)
                                    }
                                    items(
                                        items = entries,
                                        key = { (_, item) ->
                                            "calendar_${item.show.ids.trakt}_${
                                                item.episode.ids.trakt
                                            }"
                                        },
                                    ) { (instant, item) ->
                                        UpcomingRow(
                                            item = item,
                                            time = formatTime(instant, is24Hour),
                                            onClick = { onOpenShow(item.show.ids.trakt) },
                                        )
                                    }
                                }
                            }

                            UpcomingViewMode.Calendar -> {
                                item(key = "calendar-grid") {
                                    UpcomingCalendarCard(
                                        month = shownMonth,
                                        selectedDate = selectedDate,
                                        today = today,
                                        episodeCounts = episodeCounts,
                                        canGoPrevious = canGoPrevious,
                                        canGoNext = canGoNext,
                                        onPrevious = { shiftMonth(-1) },
                                        onNext = { shiftMonth(1) },
                                        onSelectDate = ::selectDate,
                                    )
                                }

                                when {
                                    calendarLoading && calendarGrouped.isEmpty() -> {
                                        item(key = "calendar-loading") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 24.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        }
                                    }

                                    calendarError != null && calendarGrouped.isEmpty() -> {
                                        item(key = "calendar-error-$shownMonth") {
                                            ErrorState(
                                                message = calendarError,
                                                onRetry = {
                                                    viewModel.loadCalendarMonth(
                                                        shownMonth,
                                                        force = true,
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    else -> {
                                        item(key = "selected-day-$selectedDate") {
                                            DayHeader(
                                                date = selectedDate,
                                                count = selectedEntries.size,
                                            )
                                        }

                                        if (selectedEntries.isEmpty()) {
                                            item(key = "selected-day-empty") {
                                                CalendarEmptyDay(
                                                    message = if (calendarGrouped.isEmpty()) {
                                                        "No episodes air this month."
                                                    } else {
                                                        "No episodes air on this day."
                                                    },
                                                )
                                            }
                                        } else {
                                            items(
                                                items = selectedEntries,
                                                key = { (_, item) ->
                                                    "selected_${item.show.ids.trakt}_${
                                                        item.episode.ids.trakt
                                                    }"
                                                },
                                            ) { (instant, item) ->
                                                UpcomingRow(
                                                    item = item,
                                                    time = formatTime(instant, is24Hour),
                                                    onClick = { onOpenShow(item.show.ids.trakt) },
                                                )
                                            }
                                        }
                                    }
                                }
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
private fun UpcomingHeader(
    viewMode: UpcomingViewMode,
    onViewModeChange: (UpcomingViewMode) -> Unit,
) {
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
        Spacer(Modifier.height(10.dp))
        ViewModeToggle(selected = viewMode, onSelect = onViewModeChange)
    }
}

@Composable
private fun ViewModeToggle(
    selected: UpcomingViewMode,
    onSelect: (UpcomingViewMode) -> Unit,
) {
    val zen = LocalZenColors.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(zen.mutedFill)
            .border(1.dp, zen.glassBorder, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UpcomingViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (isSelected) {
                            Modifier.background(zen.iridescentBrush)
                        } else {
                            Modifier
                        },
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(mode) },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) zen.onAccent else colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpcomingCalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    episodeCounts: Map<LocalDate, Int>,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val locale = Locale.getDefault()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val days = remember(month, firstDayOfWeek) { monthCalendarDays(month, firstDayOfWeek) }
    val weekdayLabels = remember(firstDayOfWeek, locale) {
        (0L until 7L).map { offset ->
            firstDayOfWeek.plus(offset).getDisplayName(JavaTextStyle.SHORT, locale)
        }
    }
    val monthLabel = remember(month, locale) {
        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
    }
    val swipeModifier = Modifier.pointerInput(month, canGoPrevious, canGoNext) {
        var dragDistance = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragDistance = 0f },
            onHorizontalDrag = { _, dragAmount -> dragDistance += dragAmount },
            onDragEnd = {
                when {
                    dragDistance <= -MONTH_SWIPE_THRESHOLD && canGoNext -> onNext()
                    dragDistance >= MONTH_SWIPE_THRESHOLD && canGoPrevious -> onPrevious()
                }
            },
            onDragCancel = { dragDistance = 0f },
        )
    }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .then(swipeModifier),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MonthArrow(
                    enabled = canGoPrevious,
                    description = "Previous month",
                    icon = Icons.Outlined.ChevronLeft,
                    onClick = onPrevious,
                )
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(
                    enabled = canGoNext,
                    description = "Next month",
                    icon = Icons.Outlined.ChevronRight,
                    onClick = onNext,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDayCell(
                            day = day,
                            inMonth = YearMonth.from(day) == month,
                            selected = day == selectedDate,
                            isToday = day == today,
                            episodeCount = episodeCounts[day] ?: 0,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectDate(day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthArrow(
    enabled: Boolean,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.35f),
        shape = CircleShape,
        strong = true,
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    episodeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zen = LocalZenColors.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val textColor = when {
        selected -> zen.onAccent
        !inMonth -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        else -> colors.onSurface
    }

    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(zen.iridescentBrush)
                } else {
                    Modifier
                },
            )
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, zen.glassBorderStrong, shape)
                } else {
                    Modifier
                },
            )
            .then(
                if (inMonth) {
                    Modifier.selectable(
                        selected = selected,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
            if (inMonth && episodeCount > 0) {
                Spacer(Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(episodeCount, 3)) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (selected) zen.onAccent else colors.primary),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEmptyDay(message: String = "No episodes air on this day.") {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
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
        if (count > 0) {
            Pill(text = "$count ${if (count == 1) "episode" else "episodes"}")
        }
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
                    ServiceLine(
                        network = item.show.network,
                        modifier = Modifier.weight(1f, fill = false),
                    )
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
        textAlign = TextAlign.Center,
    )
}
