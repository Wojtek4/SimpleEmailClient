package com.email.client.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "messages_fts")
@Fts4(contentEntity = MessageEntity::class)
data class MessageFtsEntity(
    val subject: String,
    val senderName: String,
    val senderAddress: String,
    val previewSnippet: String,
    val bodyPlain: String?
)
