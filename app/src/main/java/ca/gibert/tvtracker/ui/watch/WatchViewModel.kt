package ca.gibert.tvtracker.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.TraktRepository
import ca.gibert.tvtracker.data.WatchItem
import ca.gibert.tvtracker.util.episodeCode
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

data class WatchUiState(
    val items: List<WatchItem> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
    val lastUpdated: Long? = null,
    val markingEpisodeIds: Set<Int> = emptySet(),
)

sealed interface WatchEvent {
    data class Marked(val label: String, val episodeId: Int) : WatchEvent
    data class Message(val text: String) : WatchEvent
}

class WatchViewModel(
    private val repository: TraktRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WatchUiState())
    val state: StateFlow<WatchUiState> = _state.asStateFlow()

    private val _events = Channel<WatchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false, userInitiated = true)

    /**
     * Called when the screen becomes visible again (for example after returning
     * from a show's detail screen, where episodes may have been marked watched).
     */
    fun onResume() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        load(initial = false)
    }

    fun markWatched(item: WatchItem.UpNext) {
        val episode = item.progress.nextEpisode ?: return
        val episodeId = episode.ids.trakt
        if (episodeId in _state.value.markingEpisodeIds) return

        viewModelScope.launch {
            _state.update {
                it.copy(markingEpisodeIds = it.markingEpisodeIds + episodeId)
            }
            try {
                repository.markEpisodeWatched(episodeId)
                _events.send(
                    WatchEvent.Marked(
                        label = "${item.show.title} · ${episodeCode(episode.season, episode.number)}",
                        episodeId = episodeId,
                    ),
                )
                refreshAfterMutation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(WatchEvent.Message(friendlyError(error)))
            } finally {
                _state.update {
                    it.copy(markingEpisodeIds = it.markingEpisodeIds - episodeId)
                }
            }
        }
    }

    fun undoMark(episodeId: Int) {
        viewModelScope.launch {
            try {
                repository.markEpisodeUnwatched(episodeId)
                _events.send(WatchEvent.Message("Episode marked unwatched"))
                refreshAfterMutation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _events.send(WatchEvent.Message(friendlyError(error)))
            }
        }
    }

    private fun refreshAfterMutation() {
        load(initial = false)
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (initial) {
                val cached = repository.cachedToWatch()
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
                val snapshot = repository.refreshToWatch()
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
                    _events.send(WatchEvent.Message(message))
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WatchViewModel(container.traktRepository) }
        }
    }
}
