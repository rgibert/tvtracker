package ca.gibert.tvtracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.HiddenShowStore
import ca.gibert.tvtracker.data.NetworkOverrideStore
import ca.gibert.tvtracker.data.Show
import ca.gibert.tvtracker.data.TraktRepository
import ca.gibert.tvtracker.data.normalizeSearchQuery
import ca.gibert.tvtracker.data.queuedShowIds
import ca.gibert.tvtracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddShowUiState(
    val query: String = "",
    val results: List<Show> = emptyList(),
    val isSearching: Boolean = false,
    /** The normalized query [results] belong to, or null before any search. */
    val searchedQuery: String? = null,
    val errorMessage: String? = null,
    /** Shows already on To Watch, plus any added from this screen. */
    val queuedIds: Set<Int> = emptySet(),
    val addingIds: Set<Int> = emptySet(),
    val networkOverrides: Map<Int, String> = emptyMap(),
)

sealed interface AddShowEvent {
    data class Message(val text: String) : AddShowEvent
}

class AddShowViewModel(
    private val repository: TraktRepository,
    private val hiddenShows: HiddenShowStore,
    private val networkOverrides: NetworkOverrideStore,
) : ViewModel() {

    private val _state = MutableStateFlow(AddShowUiState())
    val state: StateFlow<AddShowUiState> = _state.asStateFlow()

    private val _events = Channel<AddShowEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            // The saved To Watch snapshot is enough to flag shows that are
            // already queued; it avoids a Trakt request just to open search.
            val queued = repository.cachedToWatch()?.items?.let(::queuedShowIds).orEmpty()
            val overrides = networkOverrides.readMap()
            _state.update {
                it.copy(queuedIds = it.queuedIds + queued, networkOverrides = overrides)
            }
        }
    }

    fun onQueryChange(raw: String) {
        _state.update { it.copy(query = raw) }
        val query = normalizeSearchQuery(raw)
        if (query == _state.value.searchedQuery && searchJob?.isActive != true) return
        search(query, debounce = true)
    }

    /** Keyboard "search" action or retry: run now instead of waiting. */
    fun searchNow() = search(normalizeSearchQuery(_state.value.query), debounce = false)

    private fun search(query: String?, debounce: Boolean) {
        searchJob?.cancel()
        if (query == null) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    searchedQuery = null,
                    errorMessage = null,
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            _state.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = repository.searchShows(query)
                _state.update {
                    it.copy(
                        results = results,
                        isSearching = false,
                        searchedQuery = query,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        results = emptyList(),
                        isSearching = false,
                        searchedQuery = query,
                        errorMessage = friendlyError(error),
                    )
                }
            }
        }
    }

    fun add(show: Show) {
        val showId = show.ids.trakt
        val current = _state.value
        if (showId in current.addingIds || showId in current.queuedIds) return

        viewModelScope.launch {
            _state.update { it.copy(addingIds = it.addingIds + showId) }
            try {
                repository.addToWatchlist(show)
                // A show the user hid earlier would otherwise be added but
                // stay invisible, which looks like the add failed.
                if (showId in hiddenShows.readIds()) hiddenShows.unhide(showId)
                _state.update { it.copy(queuedIds = it.queuedIds + showId) }
                _events.send(AddShowEvent.Message("Added ${show.title} to your watchlist"))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(AddShowEvent.Message(friendlyError(error)))
            } finally {
                _state.update { it.copy(addingIds = it.addingIds - showId) }
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddShowViewModel(
                    repository = container.traktRepository,
                    hiddenShows = container.hiddenShowStore,
                    networkOverrides = container.networkOverrideStore,
                )
            }
        }
    }
}
