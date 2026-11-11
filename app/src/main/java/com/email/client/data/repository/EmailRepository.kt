package com.email.client.data.repository

import com.email.client.core.auth.AuthManager
import com.email.client.core.auth.OAuthConfig
import com.email.client.core.security.EncryptedSecureStorage
import com.email.client.core.security.SecureStorage
import com.email.client.data.local.dao.AccountDao
import com.email.client.data.local.dao.AttachmentDao
import com.email.client.data.local.dao.FolderDao
import com.email.client.data.local.dao.MessageDao
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AttachmentEntity
import com.email.client.data.local.entity.AuthType
import com.email.client.data.local.entity.FolderEntity
import com.email.client.data.local.entity.FolderType
import com.email.client.data.local.entity.MessageEntity
import com.email.client.data.mail.ImapClient
import com.email.client.data.mail.SmtpClient
import com.email.client.data.mail.model.OutgoingEmail
import com.sun.mail.imap.IMAPFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.AuthenticationFailedException
import javax.mail.Flags
import javax.mail.Folder

data class SyncResult(
    val totalSynced: Int,
    val newMessages: List<MessageEntity> = emptyList()
)

@Singleton
class EmailRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val secureStorage: SecureStorage,
    private val authManager: AuthManager,
    private val imapClient: ImapClient,
    private val smtpClient: SmtpClient
) {

    fun getAllAccounts(): Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    fun getDefaultAccount(): Flow<AccountEntity?> = accountDao.getDefaultAccount()

    fun getFolders(accountId: Long): Flow<List<FolderEntity>> = folderDao.getFoldersForAccount(accountId)

    fun getMessages(folderId: Long): Flow<List<MessageEntity>> = messageDao.getMessagesForFolder(folderId)

    fun observeMessage(messageId: Long): Flow<MessageEntity?> = messageDao.observeMessageById(messageId)

    fun searchMessages(accountId: Long, query: String): Flow<List<MessageEntity>> =
        messageDao.searchMessages(accountId, query)

    fun getMessagesForFolderType(folderType: FolderType): Flow<List<MessageEntity>> =
        messageDao.getMessagesForFolderType(folderType)

    fun searchAllMessages(query: String): Flow<List<MessageEntity>> =
        messageDao.searchAllMessages(query)

    fun getAllStarredMessages(): Flow<List<MessageEntity>> =
        messageDao.getAllStarredMessages()

    suspend fun syncAllInboxes(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val accounts = accountDao.getAllAccountsSync()
            var count = 0
            val allNew = mutableListOf<MessageEntity>()
            for (acc in accounts) {
                syncFolders(acc.id)
                val inbox = folderDao.getFolderByType(acc.id, FolderType.INBOX)
                if (inbox != null) {
                    val res = syncMessages(inbox.id)
                    res.getOrNull()?.let {
                        count += it.totalSynced
                        allNew.addAll(it.newMessages)
                    }
                }
            }
            Result.success(SyncResult(totalSynced = count, newMessages = allNew))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyConnection(account: AccountEntity, passwordOrToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val store = imapClient.connectAndGetStore(account, passwordOrToken)
            try {
                if (!store.isConnected) {
                    return@withContext Result.failure(IllegalStateException("Unable to connect to IMAP server"))
                }
                val defaultFolder = store.defaultFolder
                if (defaultFolder == null) {
                    return@withContext Result.failure(IllegalStateException("Connected, but mail storage is not accessible"))
                }
            } finally {
                if (store.isConnected) {
                    store.close()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            val message = when {
                e is AuthenticationFailedException || e.cause is AuthenticationFailedException ->
                    "Authentication failed: Incorrect email or password. If using Gmail, Outlook, or Yahoo, please generate and use an App Password."
                e is java.net.UnknownHostException || e.cause is java.net.UnknownHostException ->
                    "Server not found: '${account.imapHost}'. Please verify the host address."
                e is java.net.SocketTimeoutException || e.cause is java.net.SocketTimeoutException ->
                    "Connection timed out. Please check your internet connection, server host, and port."
                e is java.net.ConnectException || e.cause is java.net.ConnectException ->
                    "Connection refused to ${account.imapHost}:${account.imapPort}. Please verify port and SSL settings."
                e is javax.net.ssl.SSLException || e.cause is javax.net.ssl.SSLException ->
                    "SSL/TLS security negotiation failed. Please check the port (993 for SSL) or server security configuration."
                !e.message.isNullOrBlank() ->
                    "Failed to connect: ${e.message}"
                else ->
                    "Could not connect to email server. Please check your settings."
            }
            Result.failure(Exception(message, e))
        }
    }

    suspend fun addAccount(account: AccountEntity, passwordOrToken: String): Long {
        val id = accountDao.insertAccount(account)
        if (account.authType == AuthType.PASSWORD) {
            secureStorage.saveSecret(EncryptedSecureStorage.accountPasswordKey(id), passwordOrToken)
        } else {
            secureStorage.saveSecret(EncryptedSecureStorage.accountRefreshTokenKey(id), passwordOrToken)
        }
        return id
    }

    private suspend fun getCredential(account: AccountEntity): String {
        return if (account.authType == AuthType.PASSWORD) {
            secureStorage.getSecret(EncryptedSecureStorage.accountPasswordKey(account.id)) ?: ""
        } else {
            val config = OAuthConfig.google("")
            authManager.getValidAccessToken(account.id, config).getOrNull()
                ?: secureStorage.getSecret(EncryptedSecureStorage.accountAccessTokenKey(account.id)) ?: ""
        }
    }

    suspend fun syncFolders(accountId: Long): Result<List<FolderEntity>> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getAccountById(accountId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found"))
            val credential = getCredential(account)
            val store = imapClient.connectAndGetStore(account, credential)

            val remoteFolders = try {
                imapClient.listFolders(store)
            } finally {
                store.close()
            }

            val entities = remoteFolders.map { rf ->
                val existing = folderDao.getFolderByPath(accountId, rf.fullName)
                FolderEntity(
                    id = existing?.id ?: 0,
                    accountId = accountId,
                    name = rf.name,
                    serverPath = rf.fullName,
                    type = rf.type,
                    unreadCount = rf.unreadCount,
                    totalCount = rf.messageCount,
                    syncUidValidity = rf.uidValidity,
                    highestUid = existing?.highestUid ?: 0L
                )
            }

            folderDao.insertFolders(entities)
            Result.success(entities)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncMessages(folderId: Long, limit: Int = 100): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderById(folderId)
                ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
            val account = accountDao.getAccountById(folder.accountId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found"))
            val credential = getCredential(account)

            val existingMessages = messageDao.getMessagesForFolderSync(folderId)
            val existingMap = existingMessages.associateBy { it.serverUid }
            val cachedUids = existingMessages
                .filter { !it.bodyPlain.isNullOrBlank() || !it.bodyHtml.isNullOrBlank() }
                .map { it.serverUid }
                .toSet()

            val store = imapClient.connectAndGetStore(account, credential)
            val imapFolder = store.getFolder(folder.serverPath) as IMAPFolder

            val details = try {
                imapClient.fetchMessagesWithBodies(imapFolder, startUid = 1L, limit = limit, cachedUids = cachedUids)
            } finally {
                if (imapFolder.isOpen) imapFolder.close(false)
                store.close()
            }

            val newlyArrivedMessages = mutableListOf<MessageEntity>()

            for (d in details) {
                val existing = existingMap[d.header.uid]
                val plainText = d.bodyPlain ?: existing?.bodyPlain
                val htmlText = d.bodyHtml ?: existing?.bodyHtml
                val snippet = if (!d.header.previewSnippet.isBlank() && d.header.previewSnippet != d.header.subject) {
                    d.header.previewSnippet
                } else if (!existing?.previewSnippet.isNullOrBlank()) {
                    existing!!.previewSnippet
                } else if (!plainText.isNullOrBlank()) {
                    plainText.take(200).replace("\n", " ").trim()
                } else {
                    d.header.subject.take(120)
                }

                val messageEntity = MessageEntity(
                    id = existing?.id ?: 0,
                    accountId = folder.accountId,
                    folderId = folderId,
                    serverUid = d.header.uid,
                    messageId = d.header.messageId,
                    senderName = d.header.senderName,
                    senderAddress = d.header.senderAddress,
                    recipientsTo = d.header.recipientsTo,
                    recipientsCc = d.header.recipientsCc,
                    recipientsBcc = d.header.recipientsBcc,
                    subject = d.header.subject,
                    previewSnippet = snippet,
                    bodyPlain = plainText,
                    bodyHtml = htmlText,
                    receivedDate = d.header.receivedDate,
                    isRead = d.header.isRead,
                    isStarred = d.header.isStarred,
                    hasAttachments = d.header.hasAttachments || (existing?.hasAttachments == true)
                )

                val savedRowId = messageDao.insertMessage(messageEntity)
                val targetId = if (existing != null && existing.id > 0) existing.id else savedRowId

                if (existing == null && !messageEntity.isRead) {
                    newlyArrivedMessages.add(messageEntity.copy(id = targetId))
                }

                if (d.attachments.isNotEmpty()) {
                    val existingAttachments = attachmentDao.getAttachmentsForMessageSync(targetId)
                    if (existingAttachments.isEmpty()) {
                        val attachmentEntities = d.attachments.map { att ->
                            AttachmentEntity(
                                messageId = targetId,
                                fileName = att.fileName,
                                mimeType = att.mimeType,
                                sizeBytes = att.sizeBytes,
                                contentId = att.contentId
                            )
                        }
                        attachmentDao.insertAttachments(attachmentEntities)
                    }
                }
            }

            val unreadCount = messageDao.getUnreadCount(folderId)
            val totalCount = messageDao.getTotalCount(folderId)
            folderDao.updateCounts(folderId, unreadCount, totalCount)

            Result.success(SyncResult(totalSynced = details.size, newMessages = newlyArrivedMessages))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadMessageBody(messageId: Long): Result<MessageEntity> = withContext(Dispatchers.IO) {
        try {
            val message = messageDao.getMessageById(messageId)
                ?: return@withContext Result.failure(IllegalArgumentException("Message not found"))

            if (!message.bodyHtml.isNullOrBlank() || !message.bodyPlain.isNullOrBlank()) {
                return@withContext Result.success(message)
            }

            val folder = folderDao.getFolderById(message.folderId)
                ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
            val account = accountDao.getAccountById(folder.accountId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found"))
            val credential = getCredential(account)

            val store = imapClient.connectAndGetStore(account, credential)
            val imapFolder = store.getFolder(folder.serverPath) as IMAPFolder

            val detail = try {
                imapClient.fetchMessageDetail(imapFolder, message.serverUid)
            } finally {
                if (imapFolder.isOpen) imapFolder.close(false)
                store.close()
            }

            if (detail != null) {
                val updated = message.copy(
                    bodyPlain = detail.bodyPlain,
                    bodyHtml = detail.bodyHtml,
                    hasAttachments = detail.attachments.isNotEmpty(),
                    previewSnippet = detail.header.previewSnippet.ifBlank { message.previewSnippet }
                )
                messageDao.updateMessage(updated)

                if (detail.attachments.isNotEmpty()) {
                    val existingAtts = attachmentDao.getAttachmentsForMessageSync(message.id)
                    if (existingAtts.isEmpty()) {
                        val attachments = detail.attachments.map { att ->
                            AttachmentEntity(
                                messageId = message.id,
                                fileName = att.fileName,
                                mimeType = att.mimeType,
                                sizeBytes = att.sizeBytes,
                                contentId = att.contentId
                            )
                        }
                        attachmentDao.insertAttachments(attachments)
                    }
                }

                Result.success(updated)
            } else {
                Result.failure(IllegalStateException("Failed to retrieve message content"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setReadStatus(messageId: Long, isRead: Boolean) = withContext(Dispatchers.IO) {
        messageDao.updateReadStatus(messageId, isRead)
        val message = messageDao.getMessageById(messageId) ?: return@withContext
        val folder = folderDao.getFolderById(message.folderId) ?: return@withContext
        val account = accountDao.getAccountById(folder.accountId) ?: return@withContext
        try {
            val credential = getCredential(account)
            val store = imapClient.connectAndGetStore(account, credential)
            val imapFolder = store.getFolder(folder.serverPath) as IMAPFolder
            imapFolder.open(Folder.READ_WRITE)
            val msg = imapFolder.getMessageByUID(message.serverUid)
            msg?.setFlag(Flags.Flag.SEEN, isRead)
            imapFolder.close(false)
            store.close()
        } catch (_: Exception) {
            // Offline queueing or best-effort
        }
    }

    suspend fun setStarredStatus(messageId: Long, isStarred: Boolean) = withContext(Dispatchers.IO) {
        messageDao.updateStarredStatus(messageId, isStarred)
        val message = messageDao.getMessageById(messageId) ?: return@withContext
        val folder = folderDao.getFolderById(message.folderId) ?: return@withContext
        val account = accountDao.getAccountById(folder.accountId) ?: return@withContext
        try {
            val credential = getCredential(account)
            val store = imapClient.connectAndGetStore(account, credential)
            val imapFolder = store.getFolder(folder.serverPath) as IMAPFolder
            imapFolder.open(Folder.READ_WRITE)
            val msg = imapFolder.getMessageByUID(message.serverUid)
            msg?.setFlag(Flags.Flag.FLAGGED, isStarred)
            imapFolder.close(false)
            store.close()
        } catch (_: Exception) {
            // Best effort
        }
    }

    suspend fun sendEmail(accountId: Long, email: OutgoingEmail): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val account = accountDao.getAccountById(accountId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found"))
            val credential = getCredential(account)
            smtpClient.sendEmail(account, credential, email)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
