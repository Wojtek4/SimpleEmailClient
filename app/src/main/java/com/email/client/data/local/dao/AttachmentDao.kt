package com.email.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.email.client.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    fun getAttachmentsForMessage(messageId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun getAttachmentsForMessageSync(messageId: Long): List<AttachmentEntity>

    @Query("UPDATE attachments SET localFilePath = :filePath, isDownloaded = 1 WHERE id = :attachmentId")
    suspend fun markDownloaded(attachmentId: Long, filePath: String)
}
