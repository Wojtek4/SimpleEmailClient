package com.email.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.email.client.data.local.dao.AccountDao
import com.email.client.data.local.dao.AttachmentDao
import com.email.client.data.local.dao.FolderDao
import com.email.client.data.local.dao.MessageDao
import com.email.client.data.local.entity.AccountEntity
import com.email.client.data.local.entity.AttachmentEntity
import com.email.client.data.local.entity.FolderEntity
import com.email.client.data.local.entity.MessageEntity
import com.email.client.data.local.entity.MessageFtsEntity

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        AttachmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val DATABASE_NAME = "simple_email_client.db"
    }
}
