package ca.gibert.tvtracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.gibert.tvtracker.data.AppContainer
import ca.gibert.tvtracker.data.AuthRepository
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState = authRepository.state
    val deviceLogin = authRepository.deviceLogin

    fun saveClientId(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            authRepository.saveClientId(
                clientId = clientId,
                clientSecret = clientSecret.ifBlank { null },
            )
        }
    }

    fun beginDeviceLogin() = authRepository.startDeviceLogin()

    fun cancelDeviceLogin() = authRepository.cancelDeviceLogin()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(container.authRepository) }
        }
    }
}
