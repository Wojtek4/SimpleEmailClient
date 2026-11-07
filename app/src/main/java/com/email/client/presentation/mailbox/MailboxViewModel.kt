package com.email.client.presentation.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.FolderEntity
import com.email.client.data.local.entity.FolderType
import com.email.client.data.local.entity.MessageEntity
import com.email.client.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MailboxUiState(
    val selectedAccount: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolder: FolderEntity? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MailboxViewModel @Inject constructor(
    private val emailRepository: EmailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MailboxUiState())
    val uiState: StateFlow<MailboxUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            emailRepository.getAllAccounts().collect { accounts ->
                _uiState.update { current ->
                    val selected = current.selectedAccount ?: accounts.firstOrNull()
                    current.copy(
                        accounts = accounts,
                        selectedAccount = selected
                    )
                }
                _uiState.value.selectedAccount?.let { acc ->
                    loadFolders(acc.id)
                }
            }
        }
    }

    private fun loadFolders(accountId: Long) {
        viewModelScope.launch {
            emailRepository.getFolders(accountId).collect { folders ->
                _uiState.update { current ->
                    val selectedFolder = current.selectedFolder
                        ?: folders.firstOrNull { it.type == FolderType.INBOX }
                        ?: folders.firstOrNull()
                    current.copy(
                        folders = folders,
                        selectedFolder = selectedFolder
                    )
                }
            }
        }
    }

    val messages: StateFlow<List<MessageEntity>> = _uiState
        .flatMapLatest { state ->
            val accountId = state.selectedAccount?.id ?: return@flatMapLatest flowOf(emptyList())
            if (state.searchQuery.isNotBlank()) {
                emailRepository.searchMessages(accountId, state.searchQuery)
            } else {
                val folderId = state.selectedFolder?.id ?: return@flatMapLatest flowOf(emptyList())
                emailRepository.getMessages(folderId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectAccount(account: AccountEntity) {
        _uiState.update { it.copy(selectedAccount = account, selectedFolder = null) }
        loadFolders(account.id)
    }

    fun selectFolder(folder: FolderEntity) {
        _uiState.update { it.copy(selectedFolder = folder, searchQuery = "", isSearching = false) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _uiState.update {
            val nowSearching = !it.isSearching
            it.copy(isSearching = nowSearching, searchQuery = if (!nowSearching) "" else it.searchQuery)
        }
    }

    fun refreshMessages() {
        val folderId = _uiState.value.selectedFolder?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = emailRepository.syncMessages(folderId)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    errorMessage = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun toggleReadStatus(message: MessageEntity) {
        viewModelScope.launch {
            emailRepository.setReadStatus(message.id, !message.isRead)
        }
    }

    fun toggleStarredStatus(message: MessageEntity) {
        viewModelScope.launch {
            emailRepository.setStarredStatus(message.id, !message.isStarred)
        }
    }
}
