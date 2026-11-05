package ca.gibert.tvtracker.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.DetailEpisode
import ca.gibert.tvtracker.data.HiddenShowStore
import ca.gibert.tvtracker.data.ShowDetail
import ca.gibert.tvtracker.data.TraktRepository
import ca.gibert.tvtracker.util.friendlyError
import ca.gibert.tvtracker.util.isAired
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShowDetailUiState(
    val detail: ShowDetail? = null,
    val selectedSeason: Int? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val showingCached: Boolean = false,
    val mutatingEpisodeIds: Set<Int> = emptySet(),
    val isHidden: Boolean = false,
)

class ShowDetailViewModel(
    private val showId: Int,
    private val repository: TraktRepository,
    private val hiddenShows: HiddenShowStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ShowDetailUiState())
    val state: StateFlow<ShowDetailUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var userSelectedSeason = false

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false, userInitiated = true)

    fun selectSeason(number: Int) {
        userSelectedSeason = true
        _state.update { it.copy(selectedSeason = number) }
    }

    fun toggleEpisode(episode: DetailEpisode) {
        val episodeId = episode.episode.ids.trakt
        if (episodeId in _state.value.mutatingEpisodeIds) return

        viewModelScope.launch {
            _state.update {
                it.copy(mutatingEpisodeIds = it.mutatingEpisodeIds + episodeId)
            }

            val targetCompleted = !episode.completed
            val previous = _state.value.detail
            if (previous != null) {
                _state.update { state ->
                    state.copy(detail = applyCompletion(previous, episodeId, targetCompleted))
                }
            }

            try {
                if (targetCompleted) {
                    repository.markEpisodeWatched(episodeId)
                } else {
                    repository.markEpisodeUnwatched(episodeId)
                }
                refreshSilently()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Put the checkbox back the way it was if Trakt rejected the change.
                if (previous != null) {
                    _state.update { state ->
                        state.copy(detail = previous)
                    }
                }
                _messages.send(friendlyError(error))
            } finally {
                _state.update {
                    it.copy(mutatingEpisodeIds = it.mutatingEpisodeIds - episodeId)
                }
            }
        }
    }

    /**
     * Hides or restores the show across To Watch and Coming Up. Hiding is done
     * from here rather than from the lists so it stays next to everything else
     * about the show; unhiding also happens in Settings.
     */
    fun toggleHidden() {
        val detail = _state.value.detail ?: return

        viewModelScope.launch {
            try {
                if (_state.value.isHidden) {
                    hiddenShows.unhide(showId)
                    _state.update { it.copy(isHidden = false) }
                    _messages.send("${detail.show.title} is back in your lists")
                } else {
                    hiddenShows.hide(detail.show)
                    _state.update { it.copy(isHidden = true) }
                    _messages.send("${detail.show.title} is hidden from your lists")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    private fun refreshSilently() {
        viewModelScope.launch {
            try {
                val detail = repository.refreshShowDetail(showId)
                applyDetail(detail, fromCache = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The optimistic update is still visible, and the cached copy keeps
                // the change. Leave the screen alone rather than flashing an error.
            }
        }
    }

    private fun load(initial: Boolean, userInitiated: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isHidden = showId in hiddenShows.readIds()) }

            if (initial) {
                val cached = repository.cachedShowDetail(showId)
                if (cached != null) {
                    applyDetail(cached, fromCache = true)
                }
            }

            _state.update {
                it.copy(
                    isLoading = it.detail == null,
                    isRefreshing = it.detail != null,
                    errorMessage = null,
                )
            }

            try {
                val detail = repository.refreshShowDetail(showId)
                applyDetail(detail, fromCache = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = friendlyError(error)
                val hasDetail = _state.value.detail != null
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (hasDetail) null else message,
                        showingCached = hasDetail,
                    )
                }
                if (userInitiated && hasDetail) {
                    _messages.send(message)
                }
            }
        }
    }

    private fun applyDetail(detail: ShowDetail, fromCache: Boolean) {
        _state.update { state ->
            val selected = when {
                userSelectedSeason && detail.seasons.any { it.number == state.selectedSeason } ->
                    state.selectedSeason

                else -> defaultSeason(detail)
            }
            state.copy(
                detail = detail,
                selectedSeason = selected,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
                showingCached = fromCache,
            )
        }
    }

    private fun defaultSeason(detail: ShowDetail): Int? {
        val next = detail.nextEpisode
        if (next != null && detail.seasons.any { it.number == next.season }) {
            return next.season
        }
        val withUnwatched = detail.seasons
            .filter { season -> season.episodes.any { !it.completed && isAired(it.episode.firstAired) } }
            .maxByOrNull { it.number }
        return withUnwatched?.number ?: detail.seasons.maxByOrNull { it.number }?.number
    }

    private fun applyCompletion(
        detail: ShowDetail,
        episodeId: Int,
        completed: Boolean,
    ): ShowDetail {
        val seasons = detail.seasons.map { season ->
            val episodes = season.episodes.map { episode ->
                if (episode.episode.ids.trakt == episodeId) {
                    episode.copy(completed = completed)
                } else {
                    episode
                }
            }
            season.copy(episodes = episodes, completed = episodes.count { it.completed })
        }

        val next = seasons
            .asSequence()
            .flatMap { it.episodes.asSequence() }
            .filter { !it.completed && isAired(it.episode.firstAired) }
            .minWithOrNull(
                compareBy({ it.episode.season }, { it.episode.number }),
            )
            ?.episode

        return detail.copy(
            seasons = seasons,
            completedEpisodes = seasons.sumOf { it.completed },
            nextEpisode = next,
        )
    }

    companion object {
        fun factory(container: AppContainer, showId: Int): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ShowDetailViewModel(
                        showId = showId,
                        repository = container.traktRepository,
                        hiddenShows = container.hiddenShowStore,
                    )
                }
            }
    }
}
