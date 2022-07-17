package io.github.zohrevand.dialogue.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.zohrevand.core.model.data.Account
import io.github.zohrevand.core.model.data.AccountStatus.Online
import io.github.zohrevand.core.model.data.AccountStatus.ServerNotFound
import io.github.zohrevand.core.model.data.AccountStatus.Unauthorized
import io.github.zohrevand.dialogue.core.data.repository.PreferencesRepository
import io.github.zohrevand.dialogue.feature.auth.AuthUiState.AuthRequired
import io.github.zohrevand.dialogue.feature.auth.AuthUiState.UserAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState: MutableStateFlow<AuthUiState> = MutableStateFlow(AuthUiState.Checking)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkIfUserAlreadyLoggedIn()
    }

    private fun checkIfUserAlreadyLoggedIn() {
        viewModelScope.launch {
            val account = preferencesRepository.getAccount().firstOrNull()
            if (account?.status == Online) {
                _uiState.update { UserAvailable }
            } else {
                _uiState.update { AuthRequired }
            }
        }
    }

    fun login(jid: String, password: String) {
        val account = Account.create(jid, password)
        _uiState.update { AuthUiState.Loading }
        viewModelScope.launch {
            preferencesRepository.updateAccount(account)

            checkForAccountStatusChanges()
        }
    }

    private suspend fun checkForAccountStatusChanges() {
        preferencesRepository.getAccount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            ).collect { account ->
                when (account?.status) {
                    Online -> _uiState.update { AuthUiState.Success }
                    ServerNotFound -> _uiState.update { AuthUiState.Error("Server not available") }
                    Unauthorized -> _uiState.update { AuthUiState.Error("You are not authorized") }
                    else -> { /*Not interested*/
                    }
                }
            }
    }
}

sealed interface AuthUiState {
    // State for checking if there is already logged in user
    object Checking : AuthUiState

    object UserAvailable : AuthUiState

    object AuthRequired : AuthUiState

    object Loading : AuthUiState

    object Success : AuthUiState

    data class Error(val message: String) : AuthUiState
}