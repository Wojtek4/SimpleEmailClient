package com.email.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.email.client.data.local.entity.FolderEntity
import com.email.client.data.local.entity.FolderType
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY type ASC, name ASC")
    fun getFoldersForAccount(accountId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND type = :type LIMIT 1")
    suspend fun getFolderByType(accountId: Long, type: FolderType): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND serverPath = :serverPath LIMIT 1")
    suspend fun getFolderByPath(accountId: Long, serverPath: String): FolderEntity?

    @Query("UPDATE folders SET unreadCount = :unread, totalCount = :total WHERE id = :folderId")
    suspend fun updateCounts(folderId: Long, unread: Int, total: Int)
}
