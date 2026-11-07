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
import javax.mail.Flags
import javax.mail.Folder

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

    suspend fun syncMessages(folderId: Long, limit: Int = 30): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val folder = folderDao.getFolderById(folderId)
                ?: return@withContext Result.failure(IllegalArgumentException("Folder not found"))
            val account = accountDao.getAccountById(folder.accountId)
                ?: return@withContext Result.failure(IllegalArgumentException("Account not found"))
            val credential = getCredential(account)

            val store = imapClient.connectAndGetStore(account, credential)
            val imapFolder = store.getFolder(folder.serverPath) as IMAPFolder

            val headers = try {
                imapClient.fetchHeaders(imapFolder, startUid = 1L, limit = limit)
            } finally {
                if (imapFolder.isOpen) imapFolder.close(false)
                store.close()
            }

            val messageEntities = headers.map { h ->
                val existing = messageDao.getMessageByUid(folderId, h.uid)
                MessageEntity(
                    id = existing?.id ?: 0,
                    accountId = folder.accountId,
                    folderId = folderId,
                    serverUid = h.uid,
                    messageId = h.messageId,
                    senderName = h.senderName,
                    senderAddress = h.senderAddress,
                    recipientsTo = h.recipientsTo,
                    recipientsCc = h.recipientsCc,
                    recipientsBcc = h.recipientsBcc,
                    subject = h.subject,
                    previewSnippet = h.previewSnippet,
                    bodyPlain = existing?.bodyPlain,
                    bodyHtml = existing?.bodyHtml,
                    receivedDate = h.receivedDate,
                    isRead = h.isRead,
                    isStarred = h.isStarred,
                    hasAttachments = h.hasAttachments
                )
            }

            messageDao.insertMessages(messageEntities)

            val unreadCount = messageDao.getUnreadCount(folderId)
            val totalCount = messageDao.getTotalCount(folderId)
            folderDao.updateCounts(folderId, unreadCount, totalCount)

            Result.success(messageEntities.size)
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
                    hasAttachments = detail.attachments.isNotEmpty()
                )
                messageDao.updateMessage(updated)

                if (detail.attachments.isNotEmpty()) {
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
