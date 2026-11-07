package com.email.client.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class FolderType {
    INBOX,
    SENT,
    DRAFTS,
    TRASH,
    ARCHIVE,
    SPAM,
    STARRED,
    CUSTOM
}

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["accountId", "serverPath"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val name: String,
    val serverPath: String,
    val type: FolderType,
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
    val syncUidValidity: Long = 0L,
    val highestUid: Long = 0L
)
