package ca.gibert.tvtracker.ui.upcoming

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.CalendarItem
import ca.gibert.tvtracker.data.HiddenShowStore
import ca.gibert.tvtracker.data.NetworkOverrideStore
import ca.gibert.tvtracker.data.TraktRepository
import ca.gibert.tvtracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class UpcomingUiState(
    val items: List<CalendarItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
    val lastUpdated: Long? = null,
    val hiddenIds: Set<Int> = emptySet(),
    /** Show id → channel the user pinned in place of Trakt's network. */
    val networkOverrides: Map<Int, String> = emptyMap(),
    val calendarMonths: Map<YearMonth, List<CalendarItem>> = emptyMap(),
    val calendarFetchedAt: Map<YearMonth, Long> = emptyMap(),
    val loadingMonths: Set<YearMonth> = emptySet(),
    val calendarErrors: Map<YearMonth, String> = emptyMap(),
)

class UpcomingViewModel(
    private val repository: TraktRepository,
    private val hiddenShows: HiddenShowStore,
    private val networkOverrides: NetworkOverrideStore,
) : ViewModel() {

    private val _state = MutableStateFlow(UpcomingUiState())
    val state: StateFlow<UpcomingUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false, userInitiated = true)

    /**
     * Loads a whole month for the calendar view, reusing a recent in-memory or
     * disk snapshot when possible. Pull-to-refresh passes [force] to bypass
     * that window.
     */
    fun loadCalendarMonth(month: YearMonth, force: Boolean = false) {
        if (month in _state.value.loadingMonths) return
        if (!force && isCalendarMonthFresh(_state.value, month)) return

        // Mark the month as in-flight before launching so two effects reacting
        // to the same month cannot start duplicate requests.
        _state.update {
            it.copy(
                loadingMonths = it.loadingMonths + month,
                calendarErrors = it.calendarErrors - month,
            )
        }

        viewModelScope.launch {
            try {
                if (_state.value.calendarMonths[month] == null) {
                    repository.cachedUpcomingMonth(month)?.let { cached ->
                        _state.update {
                            it.copy(
                                calendarMonths = it.calendarMonths + (month to cached.items),
                                calendarFetchedAt = it.calendarFetchedAt +
                                    (month to cached.fetchedAt),
                            )
                        }
                    }
                }

                if (!force && isCalendarMonthFresh(_state.value, month)) return@launch

                val snapshot = repository.refreshUpcomingMonth(month)
                _state.update {
                    it.copy(
                        calendarMonths = it.calendarMonths + (month to snapshot.items),
                        calendarFetchedAt = it.calendarFetchedAt +
                            (month to snapshot.fetchedAt),
                        calendarErrors = it.calendarErrors - month,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = friendlyError(error)
                val hasData = _state.value.calendarMonths[month] != null
                _state.update {
                    it.copy(
                        calendarErrors = if (hasData) {
                            it.calendarErrors - month
                        } else {
                            it.calendarErrors + (month to message)
                        },
                    )
                }
                if (hasData) _messages.send(message)
            } finally {
                _state.update { it.copy(loadingMonths = it.loadingMonths - month) }
            }
        }
    }

    /**
     * Resume hook for the calendar view. It only refreshes the visible month,
     * not the separate two-week list, so a calendar resume costs one request.
     */
    fun onCalendarResume(month: YearMonth) {
        viewModelScope.launch { syncHidden() }
        loadCalendarMonth(month)
    }

    /**
     * Called when the screen becomes visible again (for example after returning
     * from a show's detail screen, where episodes may have been marked watched).
     */
    fun onResume() {
        // Hidden shows can be toggled from Settings or a show's detail page
        // while this screen is not composed, so re-read the list first.
        viewModelScope.launch { syncHidden() }
        if (_state.value.isLoading || _state.value.isRefreshing) return
        if (isFresh(_state.value.lastUpdated)) return
        load(initial = false)
    }

    private suspend fun syncHidden() {
        // Both are local preferences edited from other screens (a show's
        // detail page or Settings), so they are re-read together.
        val hidden = hiddenShows.readIds()
        val overrides = networkOverrides.readMap()
        _state.update { it.copy(hiddenIds = hidden, networkOverrides = overrides) }
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            syncHidden()

            if (initial) {
                val cached = repository.cachedUpcoming()
                if (cached != null) {
                    _state.update {
                        it.copy(
                            items = cached.items,
                            lastUpdated = cached.fetchedAt,
                            isLoading = false,
                            showingCached = true,
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isLoading = it.items.isEmpty(),
                    isRefreshing = it.items.isNotEmpty(),
                    errorMessage = null,
                )
            }

            try {
                val snapshot = repository.refreshUpcoming()
                _state.update {
                    it.copy(
                        items = snapshot.items,
                        lastUpdated = snapshot.fetchedAt,
                        isLoading = false,
                        isRefreshing = false,
                        showingCached = false,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = friendlyError(error)
                val hasItems = _state.value.items.isNotEmpty()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (hasItems) null else message,
                        showingCached = hasItems,
                    )
                }
                if (userInitiated && hasItems) {
                    _messages.send(message)
                }
            }
        }
    }

    private fun isFresh(lastUpdated: Long?): Boolean =
        lastUpdated != null &&
            System.currentTimeMillis() - lastUpdated < AUTO_REFRESH_INTERVAL_MS

    private fun isCalendarMonthFresh(state: UpcomingUiState, month: YearMonth): Boolean {
        if (state.calendarMonths[month] == null) return false
        val fetchedAt = state.calendarFetchedAt[month] ?: return false
        return System.currentTimeMillis() - fetchedAt < AUTO_REFRESH_INTERVAL_MS
    }

    companion object {
        // The calendar only changes when episodes air or lists change, so it
        // does not need to be re-fetched every time the screen becomes visible.
        private const val AUTO_REFRESH_INTERVAL_MS = 30 * 60 * 1000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                UpcomingViewModel(
                    repository = container.traktRepository,
                    hiddenShows = container.hiddenShowStore,
                    networkOverrides = container.networkOverrideStore,
                )
            }
        }
    }
}
