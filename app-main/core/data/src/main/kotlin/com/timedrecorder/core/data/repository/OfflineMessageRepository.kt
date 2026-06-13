package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.database.dao.MessageDao
import com.timedrecorder.core.database.entity.MessageEntity
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.MessageItem
import com.timedrecorder.core.model.MessageType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineMessageRepository @Inject constructor(
    private val messageDao: MessageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MessageRepository {

    override fun observeAllMessages(): Flow<List<MessageItem>> =
        messageDao.observeAll().map { list -> list.map { it.asExternalModel() } }

    override fun observeRecentMessages(limit: Int): Flow<List<MessageItem>> =
        messageDao.observeRecent(limit).map { list -> list.map { it.asExternalModel() } }

    override fun observeAlertMessages(): Flow<List<MessageItem>> =
        messageDao.observeByType(MessageType.ALERT).map { list -> list.map { it.asExternalModel() } }

    override fun observeUnreadCount(): Flow<Int> = messageDao.observeUnreadCount()

    override suspend fun markAsRead(id: Long) = withContext(ioDispatcher) {
        messageDao.markAsRead(id)
    }

    override suspend fun markAllAsRead() = withContext(ioDispatcher) {
        messageDao.markAllAsRead()
    }

    override suspend fun createAlertMessage(title: String, content: String, fileId: Long?) {
        withContext(ioDispatcher) {
            messageDao.insert(
                MessageEntity(
                    title = title,
                    content = content,
                    type = MessageType.ALERT,
                    fileId = fileId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun createSystemMessage(title: String, content: String) {
        withContext(ioDispatcher) {
            messageDao.insert(
                MessageEntity(
                    title = title,
                    content = content,
                    type = MessageType.SYSTEM,
                    fileId = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }
}
