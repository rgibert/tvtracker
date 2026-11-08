package ca.gibert.tvtracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.AuthState
import ca.gibert.tvtracker.data.AuthRepository
import ca.gibert.tvtracker.data.HiddenShow
import ca.gibert.tvtracker.data.HiddenShowStore
import ca.gibert.tvtracker.data.NetworkOverride
import ca.gibert.tvtracker.data.NetworkOverrideStore
import ca.gibert.tvtracker.data.TraktRepository
import ca.gibert.tvtracker.data.User
import ca.gibert.tvtracker.util.friendlyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val profile: User? = null,
    val clientId: String = "",
    val isLoadingProfile: Boolean = true,
    val profileError: String? = null,
    val isBusy: Boolean = false,
    val hiddenShows: List<HiddenShow> = emptyList(),
    val networkOverrides: List<NetworkOverride> = emptyList(),
)

class SettingsViewModel(
    private val container: AppContainer,
    private val repository: TraktRepository,
    private val authRepository: AuthRepository,
    private val hiddenShowStore: HiddenShowStore,
    private val networkOverrideStore: NetworkOverrideStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            clientId = (authRepository.state.value as? AuthState.LoggedIn)?.clientId.orEmpty(),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        loadProfile()
        loadHiddenShows()
        loadNetworkOverrides()
    }

    fun refreshProfile() = loadProfile()

    /** Puts a hidden show back into To Watch and Coming Up. */
    fun unhide(show: HiddenShow) {
        viewModelScope.launch {
            try {
                hiddenShowStore.unhide(show.trakt)
                _state.update { state ->
                    state.copy(hiddenShows = state.hiddenShows.filterNot { it.trakt == show.trakt })
                }
                _messages.send("${show.title} is back in your lists")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    /** Drops a pinned channel so the show uses Trakt's network again. */
    fun resetNetwork(override: NetworkOverride) {
        viewModelScope.launch {
            try {
                networkOverrideStore.remove(override.trakt)
                _state.update { state ->
                    state.copy(
                        networkOverrides = state.networkOverrides
                            .filterNot { it.trakt == override.trakt },
                    )
                }
                _messages.send("${override.title} uses Trakt's channel again")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    private fun loadNetworkOverrides() {
        viewModelScope.launch {
            try {
                val overrides = networkOverrideStore.read()
                _state.update { it.copy(networkOverrides = overrides) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Local and cheap to re-read; leave the section out on failure.
            }
        }
    }

    private fun loadHiddenShows() {
        viewModelScope.launch {
            try {
                val shows = hiddenShowStore.read()
                _state.update { it.copy(hiddenShows = shows) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The list is local and cheap to re-read; leave the section
                // hidden rather than showing an error for it.
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                container.signOut()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun forgetApiApp() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                container.forgetApiApp()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            try {
                repository.clearCache()
                _messages.send("Saved data cleared")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _messages.send(friendlyError(error))
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingProfile = true, profileError = null) }
            try {
                val profile = repository.getProfile()
                _state.update {
                    it.copy(profile = profile, isLoadingProfile = false, profileError = null)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isLoadingProfile = false,
                        profileError = friendlyError(error),
                    )
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    container = container,
                    repository = container.traktRepository,
                    authRepository = container.authRepository,
                    hiddenShowStore = container.hiddenShowStore,
                    networkOverrideStore = container.networkOverrideStore,
                )
            }
        }
    }
}
