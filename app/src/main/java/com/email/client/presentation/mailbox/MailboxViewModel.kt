package com.email.client.presentation.mailbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AuthType
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MailFilter {
    ALL,
    UNREAD,
    STARRED
}

const val ALL_ACCOUNTS_ID = -1L

val ALL_ACCOUNTS_META = AccountEntity(
    id = ALL_ACCOUNTS_ID,
    email = "all@accounts",
    displayName = "All Accounts",
    authType = AuthType.PASSWORD,
    imapHost = "",
    smtpHost = ""
)

val ALL_INBOXES_FOLDER = FolderEntity(id = -101L, accountId = ALL_ACCOUNTS_ID, name = "All Inboxes", serverPath = "ALL_INBOX", type = FolderType.INBOX)
val ALL_SENT_FOLDER = FolderEntity(id = -102L, accountId = ALL_ACCOUNTS_ID, name = "All Sent", serverPath = "ALL_SENT", type = FolderType.SENT)
val ALL_STARRED_FOLDER = FolderEntity(id = -103L, accountId = ALL_ACCOUNTS_ID, name = "All Starred", serverPath = "ALL_STARRED", type = FolderType.STARRED)
val ALL_DRAFTS_FOLDER = FolderEntity(id = -104L, accountId = ALL_ACCOUNTS_ID, name = "All Drafts", serverPath = "ALL_DRAFTS", type = FolderType.DRAFTS)
val ALL_TRASH_FOLDER = FolderEntity(id = -105L, accountId = ALL_ACCOUNTS_ID, name = "All Trash", serverPath = "ALL_TRASH", type = FolderType.TRASH)

val COMBINED_FOLDERS = listOf(
    ALL_INBOXES_FOLDER,
    ALL_SENT_FOLDER,
    ALL_STARRED_FOLDER,
    ALL_DRAFTS_FOLDER,
    ALL_TRASH_FOLDER
)

data class MailboxUiState(
    val selectedAccount: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val selectedFolder: FolderEntity? = null,
    val filter: MailFilter = MailFilter.ALL,
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

    private var folderCollectionJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            emailRepository.getAllAccounts().collect { accounts ->
                val currentSelected = _uiState.value.selectedAccount
                val updatedSelected = if (currentSelected?.id == ALL_ACCOUNTS_ID && accounts.size > 1) {
                    ALL_ACCOUNTS_META
                } else {
                    accounts.find { it.id == currentSelected?.id } ?: accounts.firstOrNull()
                }

                _uiState.update { current ->
                    current.copy(
                        accounts = accounts,
                        selectedAccount = updatedSelected
                    )
                }

                if (updatedSelected != null && (currentSelected == null || updatedSelected.id != currentSelected.id || _uiState.value.folders.isEmpty())) {
                    loadFolders(updatedSelected.id)
                }
            }
        }
    }

    private fun loadFolders(accountId: Long) {
        folderCollectionJob?.cancel()
        if (accountId == ALL_ACCOUNTS_ID) {
            _uiState.update { current ->
                current.copy(
                    folders = COMBINED_FOLDERS,
                    selectedFolder = current.selectedFolder?.takeIf { sf -> COMBINED_FOLDERS.any { it.id == sf.id } }
                        ?: ALL_INBOXES_FOLDER
                )
            }
            autoSyncCurrentView()
            return
        }

        folderCollectionJob = viewModelScope.launch {
            emailRepository.syncFolders(accountId)
            emailRepository.getFolders(accountId).collect { folders ->
                val prevSelected = _uiState.value.selectedFolder
                val newSelected = prevSelected?.takeIf { sf -> folders.any { it.id == sf.id } }
                    ?: folders.firstOrNull { it.type == FolderType.INBOX }
                    ?: folders.firstOrNull()

                val shouldAutoSync = prevSelected == null || prevSelected.id != newSelected?.id

                _uiState.update { current ->
                    current.copy(
                        folders = folders,
                        selectedFolder = newSelected
                    )
                }

                if (shouldAutoSync && newSelected != null && newSelected.id > 0) {
                    autoSyncCurrentView()
                }
            }
        }
    }

    val messages: StateFlow<List<MessageEntity>> = _uiState
        .flatMapLatest { state ->
            val account = state.selectedAccount ?: return@flatMapLatest flowOf(emptyList())

            val rawFlow = if (account.id == ALL_ACCOUNTS_ID) {
                if (state.searchQuery.isNotBlank()) {
                    emailRepository.searchAllMessages(state.searchQuery)
                } else {
                    val fType = state.selectedFolder?.type ?: FolderType.INBOX
                    if (fType == FolderType.STARRED) {
                        emailRepository.getAllStarredMessages()
                    } else {
                        emailRepository.getMessagesForFolderType(fType)
                    }
                }
            } else {
                if (state.searchQuery.isNotBlank()) {
                    emailRepository.searchMessages(account.id, state.searchQuery)
                } else {
                    val folderId = state.selectedFolder?.id ?: return@flatMapLatest flowOf(emptyList())
                    emailRepository.getMessages(folderId)
                }
            }

            rawFlow.map { list ->
                when (state.filter) {
                    MailFilter.ALL -> list
                    MailFilter.UNREAD -> list.filter { !it.isRead }
                    MailFilter.STARRED -> list.filter { it.isStarred }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectAccount(account: AccountEntity) {
        _uiState.update { it.copy(selectedAccount = account, selectedFolder = null) }
        loadFolders(account.id)
    }

    fun selectFolder(folder: FolderEntity) {
        _uiState.update { it.copy(selectedFolder = folder, searchQuery = "", isSearching = false) }
        autoSyncCurrentView()
    }

    fun setFilter(filter: MailFilter) {
        _uiState.update { it.copy(filter = filter) }
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

    private fun autoSyncCurrentView() {
        val state = _uiState.value
        val account = state.selectedAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            if (account.id == ALL_ACCOUNTS_ID) {
                emailRepository.syncAllInboxes()
            } else {
                val folderId = state.selectedFolder?.id
                if (folderId != null && folderId > 0) {
                    emailRepository.syncMessages(folderId)
                }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun refreshMessages() {
        autoSyncCurrentView()
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
