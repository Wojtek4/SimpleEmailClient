package com.email.client.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AuthType
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val email: String = "",
    val displayName: String = "",
    val passwordOrToken: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val isAdvanced: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAccountCreated: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val emailRepository: EmailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        _uiState.update { current ->
            val hostGuess = guessHostFromEmail(email)
            current.copy(
                email = email,
                imapHost = if (!current.isAdvanced) hostGuess.first else current.imapHost,
                smtpHost = if (!current.isAdvanced) hostGuess.second else current.smtpHost,
                errorMessage = null
            )
        }
    }

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name) }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(passwordOrToken = password) }
    }

    fun onImapHostChanged(host: String) {
        _uiState.update { it.copy(imapHost = host) }
    }

    fun onImapPortChanged(port: String) {
        _uiState.update { it.copy(imapPort = port) }
    }

    fun onSmtpHostChanged(host: String) {
        _uiState.update { it.copy(smtpHost = host) }
    }

    fun onSmtpPortChanged(port: String) {
        _uiState.update { it.copy(smtpPort = port) }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(isAdvanced = !it.isAdvanced) }
    }

    fun saveAccount() {
        val state = _uiState.value
        if (state.email.isBlank() || state.passwordOrToken.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val account = AccountEntity(
                    email = state.email.trim(),
                    displayName = state.displayName.ifBlank { state.email.substringBefore("@") },
                    authType = AuthType.PASSWORD,
                    imapHost = state.imapHost.trim(),
                    imapPort = state.imapPort.toIntOrNull() ?: 993,
                    imapUseSsl = true,
                    smtpHost = state.smtpHost.trim(),
                    smtpPort = state.smtpPort.toIntOrNull() ?: 465,
                    smtpUseSsl = true,
                    isDefault = true
                )

                // 1. Verify credentials and connection with server first
                val verifyResult = emailRepository.verifyConnection(account, state.passwordOrToken)
                if (verifyResult.isFailure) {
                    val errorMsg = verifyResult.exceptionOrNull()?.message 
                        ?: "Failed to connect to email server. Please check your credentials and server settings."
                    _uiState.update { it.copy(isLoading = false, errorMessage = errorMsg) }
                    return@launch
                }

                // 2. Only if connection succeeds, persist account and sync folders
                val accountId = emailRepository.addAccount(account, state.passwordOrToken)
                emailRepository.syncFolders(accountId)

                // 3. Mark created to allow navigation to mailbox
                _uiState.update { it.copy(isLoading = false, isAccountCreated = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Failed to connect to email server") }
            }
        }
    }

    private fun guessHostFromEmail(email: String): Pair<String, String> {
        val domain = email.substringAfter("@", "").lowercase()
        return when {
            domain.contains("gmail") -> Pair("imap.gmail.com", "smtp.gmail.com")
            domain.contains("outlook") || domain.contains("hotmail") -> Pair("outlook.office365.com", "smtp.office365.com")
            domain.contains("yahoo") -> Pair("imap.mail.yahoo.com", "smtp.mail.yahoo.com")
            domain.contains("icloud") -> Pair("imap.mail.me.com", "smtp.mail.me.com")
            domain.isNotBlank() -> Pair("imap.$domain", "smtp.$domain")
            else -> Pair("", "")
        }
    }
}
