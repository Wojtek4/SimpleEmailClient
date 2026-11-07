package com.email.client.data.mail.model

import com.email.client.data.local.entity.FolderType

data class RemoteFolder(
    val name: String,
    val fullName: String,
    val type: FolderType,
    val messageCount: Int,
    val unreadCount: Int,
    val uidValidity: Long
)

data class RemoteMessageHeader(
    val uid: Long,
    val messageId: String,
    val senderName: String,
    val senderAddress: String,
    val recipientsTo: String,
    val recipientsCc: String,
    val recipientsBcc: String,
    val subject: String,
    val previewSnippet: String,
    val receivedDate: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val hasAttachments: Boolean
)

data class RemoteMessageDetail(
    val header: RemoteMessageHeader,
    val bodyPlain: String?,
    val bodyHtml: String?,
    val attachments: List<RemoteAttachment>
)

data class RemoteAttachment(
    val partIndex: Int,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentId: String? = null
)

data class OutgoingEmail(
    val senderAddress: String,
    val senderName: String,
    val recipientsTo: List<String>,
    val recipientsCc: List<String> = emptyList(),
    val recipientsBcc: List<String> = emptyList(),
    val subject: String,
    val bodyText: String,
    val isHtml: Boolean = false,
    val attachmentPaths: List<String> = emptyList()
)
