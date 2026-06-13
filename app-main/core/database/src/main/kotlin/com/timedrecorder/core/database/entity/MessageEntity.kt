package com.timedrecorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timedrecorder.core.model.MessageType

/** Room 实体：message_center 表 */
@Entity(tableName = "message_center")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val type: MessageType = MessageType.ALERT,
    @ColumnInfo(name = "file_id")
    val fileId: Long?,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
