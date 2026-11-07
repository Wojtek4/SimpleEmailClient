package com.email.client.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.email.client.data.local.dao.AttachmentDao
import com.email.client.data.local.entity.AttachmentEntity
import com.email.client.data.local.entity.MessageEntity
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageDetailUiState(
    val message: MessageEntity? = null,
    val attachments: List<AttachmentEntity> = emptyList(),
    val isLoadingBody: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val emailRepository: EmailRepository,
    private val attachmentDao: AttachmentDao
) : ViewModel() {

    private val messageId: Long = savedStateHandle.get<Long>("messageId") ?: 0L

    private val _uiState = MutableStateFlow(MessageDetailUiState())
    val uiState: StateFlow<MessageDetailUiState> = _uiState.asStateFlow()

    init {
        loadMessage()
    }

    private fun loadMessage() {
        if (messageId <= 0L) return

        viewModelScope.launch {
            emailRepository.observeMessage(messageId).collect { msg ->
                _uiState.update { it.copy(message = msg) }
                if (msg != null && !msg.isRead) {
                    emailRepository.setReadStatus(msg.id, true)
                }
            }
        }

        viewModelScope.launch {
            attachmentDao.getAttachmentsForMessage(messageId).collect { atts ->
                _uiState.update { it.copy(attachments = atts) }
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBody = true) }
            val result = emailRepository.loadMessageBody(messageId)
            _uiState.update {
                it.copy(
                    isLoadingBody = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun toggleStarred() {
        val currentMsg = _uiState.value.message ?: return
        viewModelScope.launch {
            emailRepository.setStarredStatus(currentMsg.id, !currentMsg.isStarred)
        }
    }
}
