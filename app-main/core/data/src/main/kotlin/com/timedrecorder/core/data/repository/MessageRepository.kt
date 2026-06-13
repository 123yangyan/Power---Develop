package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.MessageItem
import com.timedrecorder.core.model.MessageType
import kotlinx.coroutines.flow.Flow

/** 消息中心 Repository 接口 */
interface MessageRepository {
    fun observeAllMessages(): Flow<List<MessageItem>>
    fun observeRecentMessages(limit: Int = 10): Flow<List<MessageItem>>
    fun observeAlertMessages(): Flow<List<MessageItem>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun markAsRead(id: Long)
    suspend fun markAllAsRead()
    suspend fun createAlertMessage(title: String, content: String, fileId: Long?)
    suspend fun createSystemMessage(title: String, content: String)
}
