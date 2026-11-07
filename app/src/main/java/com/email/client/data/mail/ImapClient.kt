package com.email.client.data.mail

import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AuthType
import com.email.client.data.local.entity.FolderType
import com.email.client.data.mail.model.RemoteAttachment
import com.email.client.data.mail.model.RemoteFolder
import com.email.client.data.mail.model.RemoteMessageDetail
import com.email.client.data.mail.model.RemoteMessageHeader
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility

@Singleton
class ImapClient @Inject constructor() {

    suspend fun connectAndGetStore(account: AccountEntity, credentialOrToken: String): IMAPStore =
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.host", account.imapHost)
                put("mail.imaps.port", account.imapPort.toString())
                put("mail.imaps.ssl.enable", account.imapUseSsl.toString())
                put("mail.imaps.timeout", "20000")
                put("mail.imaps.connectiontimeout", "20000")
                put("mail.imaps.partialfetch", "true")

                if (account.authType == AuthType.OAUTH2) {
                    put("mail.imaps.auth.mechanisms", "XOAUTH2")
                    put("mail.imaps.auth.plain.disable", "true")
                }
            }

            val session = Session.getInstance(props, null)
            val store = session.getStore("imaps") as IMAPStore
            store.connect(account.imapHost, account.imapPort, account.email, credentialOrToken)
            store
        }

    suspend fun listFolders(store: IMAPStore): List<RemoteFolder> = withContext(Dispatchers.IO) {
        val defaultFolder = store.defaultFolder
        val folderList = mutableListOf<RemoteFolder>()
        collectFoldersRecursive(defaultFolder, folderList)
        folderList
    }

    private fun collectFoldersRecursive(parent: Folder, result: MutableList<RemoteFolder>) {
        val subFolders = parent.list("*")
        for (f in subFolders) {
            if ((f.type and Folder.HOLDS_MESSAGES) != 0) {
                val fType = detectFolderType(f.name, f.fullName)
                var unread = 0
                var total = 0
                var uidValidity = 0L

                try {
                    f.open(Folder.READ_ONLY)
                    unread = f.unreadMessageCount
                    total = f.messageCount
                    if (f is UIDFolder) {
                        uidValidity = f.uidValidity
                    }
                    f.close(false)
                } catch (_: Exception) {
                    // Ignored if folder cannot be opened in read-only
                }

                result.add(
                    RemoteFolder(
                        name = f.name,
                        fullName = f.fullName,
                        type = fType,
                        messageCount = total,
                        unreadCount = unread,
                        uidValidity = uidValidity
                    )
                )
            }
        }
    }

    suspend fun fetchHeaders(
        folder: IMAPFolder,
        startUid: Long = 1L,
        limit: Int = 50
    ): List<RemoteMessageHeader> = withContext(Dispatchers.IO) {
        if (!folder.isOpen) {
            folder.open(Folder.READ_ONLY)
        }

        val total = folder.messageCount
        if (total == 0) return@withContext emptyList()

        val startIndex = maxOf(1, total - limit + 1)
        val messages = folder.getMessages(startIndex, total)

        val fp = FetchProfile().apply {
            add(FetchProfile.Item.ENVELOPE)
            add(FetchProfile.Item.FLAGS)
            add(UIDFolder.FetchProfileItem.UID)
            add(FetchProfile.Item.CONTENT_INFO)
        }
        folder.fetch(messages, fp)

        val headers = mutableListOf<RemoteMessageHeader>()
        for (i in messages.indices.reversed()) {
            val msg = messages[i]
            val uid = folder.getUID(msg)
            if (uid >= startUid) {
                headers.add(parseMessageHeader(msg, uid))
            }
        }
        headers
    }

    suspend fun fetchMessageDetail(
        folder: IMAPFolder,
        uid: Long
    ): RemoteMessageDetail? = withContext(Dispatchers.IO) {
        if (!folder.isOpen) {
            folder.open(Folder.READ_ONLY)
        }

        val msg = folder.getMessageByUID(uid) ?: return@withContext null
        val header = parseMessageHeader(msg, uid)

        val bodyPlain = StringBuilder()
        val bodyHtml = StringBuilder()
        val attachments = mutableListOf<RemoteAttachment>()

        parsePartContent(msg, bodyPlain, bodyHtml, attachments, 0)

        val finalPlain = bodyPlain.toString().trim().ifEmpty { null }
        val finalHtml = bodyHtml.toString().trim().ifEmpty { null }

        RemoteMessageDetail(
            header = header,
            bodyPlain = finalPlain,
            bodyHtml = finalHtml,
            attachments = attachments
        )
    }

    private fun parsePartContent(
        part: Part,
        bodyPlain: StringBuilder,
        bodyHtml: StringBuilder,
        attachments: MutableList<RemoteAttachment>,
        index: Int
    ) {
        val disposition = part.disposition
        val isAttachment = disposition?.equals(Part.ATTACHMENT, ignoreCase = true) == true ||
                (!part.fileName.isNullOrBlank() && disposition?.equals(Part.INLINE, ignoreCase = true) != true)

        if (isAttachment) {
            val rawName = part.fileName ?: "attachment_$index"
            val decodedName = try { MimeUtility.decodeText(rawName) } catch (_: Exception) { rawName }
            attachments.add(
                RemoteAttachment(
                    partIndex = index,
                    fileName = decodedName,
                    mimeType = part.contentType?.substringBefore(";")?.trim() ?: "application/octet-stream",
                    sizeBytes = maxOf(0L, part.size.toLong()),
                    contentId = part.getHeader("Content-ID")?.firstOrNull()?.trim('<', '>')
                )
            )
            return
        }

        if (part.isMimeType("text/plain")) {
            val content = part.content
            if (content is String) {
                bodyPlain.append(content)
            } else if (content is InputStream) {
                bodyPlain.append(readStreamToString(content))
            }
        } else if (part.isMimeType("text/html")) {
            val content = part.content
            if (content is String) {
                bodyHtml.append(content)
            } else if (content is InputStream) {
                bodyHtml.append(readStreamToString(content))
            }
        } else if (part.isMimeType("multipart/*")) {
            val mp = part.content as? Multipart
            if (mp != null) {
                for (i in 0 until mp.count) {
                    parsePartContent(mp.getBodyPart(i), bodyPlain, bodyHtml, attachments, i)
                }
            }
        }
    }

    private fun readStreamToString(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(4096)
        var nRead: Int
        while (input.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toString("UTF-8")
    }

    private fun parseMessageHeader(msg: Message, uid: Long): RemoteMessageHeader {
        val messageId = msg.getHeader("Message-ID")?.firstOrNull() ?: ""
        val fromArray = msg.from
        val senderAddress = if (!fromArray.isNullOrEmpty()) {
            (fromArray[0] as? InternetAddress)?.address ?: fromArray[0].toString()
        } else ""

        val senderName = if (!fromArray.isNullOrEmpty()) {
            (fromArray[0] as? InternetAddress)?.personal ?: senderAddress
        } else ""

        val toRecipients = formatAddresses(msg.getRecipients(Message.RecipientType.TO))
        val ccRecipients = formatAddresses(msg.getRecipients(Message.RecipientType.CC))
        val bccRecipients = formatAddresses(msg.getRecipients(Message.RecipientType.BCC))

        val subject = try {
            msg.subject ?: ""
        } catch (_: Exception) {
            ""
        }

        val receivedDate = msg.receivedDate?.time ?: msg.sentDate?.time ?: System.currentTimeMillis()
        val isRead = msg.isSet(Flags.Flag.SEEN)
        val isStarred = msg.isSet(Flags.Flag.FLAGGED)

        val snippet = subject.take(120)

        return RemoteMessageHeader(
            uid = uid,
            messageId = messageId,
            senderName = senderName,
            senderAddress = senderAddress,
            recipientsTo = toRecipients,
            recipientsCc = ccRecipients,
            recipientsBcc = bccRecipients,
            subject = subject,
            previewSnippet = snippet,
            receivedDate = receivedDate,
            isRead = isRead,
            isStarred = isStarred,
            hasAttachments = false
        )
    }

    private fun formatAddresses(addresses: Array<javax.mail.Address>?): String {
        if (addresses.isNullOrEmpty()) return ""
        return addresses.joinToString(", ") { addr ->
            (addr as? InternetAddress)?.let {
                if (it.personal != null) "${it.personal} <${it.address}>" else it.address
            } ?: addr.toString()
        }
    }

    private fun detectFolderType(name: String, fullName: String): FolderType {
        val lower = "${name.lowercase()} ${fullName.lowercase()}"
        return when {
            lower.contains("inbox") -> FolderType.INBOX
            lower.contains("sent") -> FolderType.SENT
            lower.contains("draft") -> FolderType.DRAFTS
            lower.contains("trash") || lower.contains("bin") || lower.contains("deleted") -> FolderType.TRASH
            lower.contains("archive") -> FolderType.ARCHIVE
            lower.contains("spam") || lower.contains("junk") -> FolderType.SPAM
            lower.contains("starred") || lower.contains("flagged") -> FolderType.STARRED
            else -> FolderType.CUSTOM
        }
    }
}
