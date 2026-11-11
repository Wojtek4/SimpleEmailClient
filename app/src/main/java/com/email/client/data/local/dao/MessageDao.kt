package com.email.client.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.email.client.data.local.entity.FolderType
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

    @Query("SELECT * FROM messages WHERE folderId = :folderId")
    suspend fun getMessagesForFolderSync(folderId: Long): List<MessageEntity>

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

    // Search across title (subject), sender, recipient, content (body & preview), and attachment file names
    @Query("""
        SELECT * FROM messages m
        WHERE m.accountId = :accountId AND (
            m.subject LIKE '%' || :query || '%'
            OR m.senderName LIKE '%' || :query || '%'
            OR m.senderAddress LIKE '%' || :query || '%'
            OR m.recipientsTo LIKE '%' || :query || '%'
            OR m.recipientsCc LIKE '%' || :query || '%'
            OR m.recipientsBcc LIKE '%' || :query || '%'
            OR m.previewSnippet LIKE '%' || :query || '%'
            OR m.bodyPlain LIKE '%' || :query || '%'
            OR m.bodyHtml LIKE '%' || :query || '%'
            OR EXISTS (
                SELECT 1 FROM attachments a
                WHERE a.messageId = m.id AND a.fileName LIKE '%' || :query || '%'
            )
        )
        ORDER BY m.receivedDate DESC
    """)
    fun searchMessages(accountId: Long, query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND isStarred = 1 ORDER BY receivedDate DESC")
    fun getStarredMessages(accountId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY receivedDate DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("""
        SELECT m.* FROM messages m
        JOIN folders f ON m.folderId = f.id
        WHERE f.type = :folderType
        ORDER BY m.receivedDate DESC
    """)
    fun getMessagesForFolderType(folderType: FolderType): Flow<List<MessageEntity>>

    // Search across all accounts
    @Query("""
        SELECT * FROM messages m
        WHERE (
            m.subject LIKE '%' || :query || '%'
            OR m.senderName LIKE '%' || :query || '%'
            OR m.senderAddress LIKE '%' || :query || '%'
            OR m.recipientsTo LIKE '%' || :query || '%'
            OR m.recipientsCc LIKE '%' || :query || '%'
            OR m.recipientsBcc LIKE '%' || :query || '%'
            OR m.previewSnippet LIKE '%' || :query || '%'
            OR m.bodyPlain LIKE '%' || :query || '%'
            OR m.bodyHtml LIKE '%' || :query || '%'
            OR EXISTS (
                SELECT 1 FROM attachments a
                WHERE a.messageId = m.id AND a.fileName LIKE '%' || :query || '%'
            )
        )
        ORDER BY m.receivedDate DESC
    """)
    fun searchAllMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY receivedDate DESC")
    fun getAllStarredMessages(): Flow<List<MessageEntity>>
}
