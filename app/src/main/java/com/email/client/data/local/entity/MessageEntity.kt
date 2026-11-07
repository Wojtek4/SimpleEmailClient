package com.email.client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["folderId"]),
        Index(value = ["folderId", "serverUid"], unique = true),
        Index(value = ["receivedDate"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val folderId: Long,
    val serverUid: Long,
    val messageId: String = "",
    val senderName: String = "",
    val senderAddress: String = "",
    val recipientsTo: String = "",
    val recipientsCc: String = "",
    val recipientsBcc: String = "",
    val subject: String = "",
    val previewSnippet: String = "",
    val bodyPlain: String? = null,
    val bodyHtml: String? = null,
    val receivedDate: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val hasAttachments: Boolean = false
)
