package com.email.client.presentation.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.email.client.data.local.dao.MessageDao
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.mail.model.OutgoingEmail
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComposeUiState(
    val selectedAccount: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val recipientsTo: String = "",
    val recipientsCc: String = "",
    val recipientsBcc: String = "",
    val showCcBcc: Boolean = false,
    val subject: String = "",
    val body: String = "",
    val attachmentPaths: List<String> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
    val isSent: Boolean = false
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val emailRepository: EmailRepository,
    private val messageDao: MessageDao
) : ViewModel() {

    private val replyToMessageId: Long? = savedStateHandle.get<Long>("replyToMessageId")

    private val _uiState = MutableStateFlow(ComposeUiState())
    val uiState: StateFlow<ComposeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            emailRepository.getAllAccounts().collect { accounts ->
                _uiState.update { current ->
                    current.copy(
                        accounts = accounts,
                        selectedAccount = current.selectedAccount ?: accounts.firstOrNull()
                    )
                }
            }
        }

        if (replyToMessageId != null && replyToMessageId > 0) {
            viewModelScope.launch {
                val orig = messageDao.getMessageById(replyToMessageId)
                if (orig != null) {
                    val subjectPrefix = if (orig.subject.startsWith("Re:", ignoreCase = true)) "" else "Re: "
                    _uiState.update {
                        it.copy(
                            recipientsTo = orig.senderAddress,
                            subject = subjectPrefix + orig.subject,
                            body = "\n\n--- Original Message ---\nFrom: ${orig.senderName} <${orig.senderAddress}>\nSubject: ${orig.subject}\n\n${orig.bodyPlain ?: orig.previewSnippet}"
                        )
                    }
                }
            }
        }
    }

    fun onRecipientsToChanged(to: String) = _uiState.update { it.copy(recipientsTo = to) }
    fun onRecipientsCcChanged(cc: String) = _uiState.update { it.copy(recipientsCc = cc) }
    fun onRecipientsBccChanged(bcc: String) = _uiState.update { it.copy(recipientsBcc = bcc) }
    fun onSubjectChanged(subj: String) = _uiState.update { it.copy(subject = subj) }
    fun onBodyChanged(b: String) = _uiState.update { it.copy(body = b) }
    fun toggleCcBcc() = _uiState.update { it.copy(showCcBcc = !it.showCcBcc) }

    fun addAttachment(path: String) {
        _uiState.update { it.copy(attachmentPaths = it.attachmentPaths + path) }
    }

    fun removeAttachment(path: String) {
        _uiState.update { it.copy(attachmentPaths = it.attachmentPaths - path) }
    }

    fun sendEmail() {
        val state = _uiState.value
        val account = state.selectedAccount
        if (account == null) {
            _uiState.update { it.copy(errorMessage = "No sending account selected") }
            return
        }
        if (state.recipientsTo.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter at least one recipient") }
            return
        }

        val toList = state.recipientsTo.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        val ccList = state.recipientsCc.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
        val bccList = state.recipientsBcc.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }

        val outgoing = OutgoingEmail(
            senderAddress = account.email,
            senderName = account.displayName,
            recipientsTo = toList,
            recipientsCc = ccList,
            recipientsBcc = bccList,
            subject = state.subject,
            bodyText = state.body,
            attachmentPaths = state.attachmentPaths
        )

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, errorMessage = null) }
            val result = emailRepository.sendEmail(account.id, outgoing)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSending = false, isSent = true) }
            } else {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to send email"
                    )
                }
            }
        }
    }
}
