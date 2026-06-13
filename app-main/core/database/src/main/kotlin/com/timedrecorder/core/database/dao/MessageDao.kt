package com.timedrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timedrecorder.core.database.entity.MessageEntity
import com.timedrecorder.core.model.MessageType
import kotlinx.coroutines.flow.Flow

/** 消息中心 DAO */
@Dao
interface MessageDao {
    @Query("SELECT * FROM message_center ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_center WHERE type = :type ORDER BY created_at DESC")
    fun observeByType(type: MessageType): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message_center ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM message_center WHERE is_read = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageEntity): Long

    @Query("UPDATE message_center SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE message_center SET is_read = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM message_center WHERE file_id = :fileId")
    suspend fun deleteByFileId(fileId: Long)
}
