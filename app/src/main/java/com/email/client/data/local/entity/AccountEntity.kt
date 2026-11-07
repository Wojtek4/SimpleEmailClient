package com.email.client.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthType {
    PASSWORD,
    OAUTH2
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val displayName: String,
    val authType: AuthType,
    val imapHost: String,
    val imapPort: Int = 993,
    val imapUseSsl: Boolean = true,
    val smtpHost: String,
    val smtpPort: Int = 465,
    val smtpUseSsl: Boolean = true,
    val isDefault: Boolean = false,
    val lastSyncTimestamp: Long = 0L
)
