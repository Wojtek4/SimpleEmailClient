package com.email.client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.email.client.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Delete
    suspend fun deleteMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observeMessageById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY receivedDate DESC")
    fun getMessagesForFolder(folderId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE folderId = :folderId AND serverUid = :uid LIMIT 1")
    suspend fun getMessageByUid(folderId: Long, uid: Long): MessageEntity?

    @Query("SELECT serverUid FROM messages WHERE folderId = :folderId")
    suspend fun getAllUidsForFolder(folderId: Long): List<Long>

    @Query("UPDATE messages SET isRead = :isRead WHERE id = :messageId")
    suspend fun updateReadStatus(messageId: Long, isRead: Boolean)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :messageId")
    suspend fun updateStarredStatus(messageId: Long, isStarred: Boolean)

    @Query("DELETE FROM messages WHERE folderId = :folderId AND serverUid NOT IN (:activeUids)")
    suspend fun deleteRemovedMessages(folderId: Long, activeUids: List<Long>)

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId AND isRead = 0")
    suspend fun getUnreadCount(folderId: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId")
    suspend fun getTotalCount(folderId: Long): Int

    // Full-Text Search across sender, subject, and snippet
    @Query("""
        SELECT * FROM messages
        WHERE accountId = :accountId AND (
            subject LIKE '%' || :query || '%'
            OR senderName LIKE '%' || :query || '%'
            OR senderAddress LIKE '%' || :query || '%'
            OR previewSnippet LIKE '%' || :query || '%'
        )
        ORDER BY receivedDate DESC
    """)
    fun searchMessages(accountId: Long, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND isStarred = 1 ORDER BY receivedDate DESC")
    fun getStarredMessages(accountId: Long): Flow<List<MessageEntity>>
}
