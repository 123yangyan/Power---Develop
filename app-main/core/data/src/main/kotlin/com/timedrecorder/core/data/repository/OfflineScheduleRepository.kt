package com.timedrecorder.core.data.repository

import com.timedrecorder.core.common.di.IoDispatcher
import com.timedrecorder.core.data.util.ScheduleOverlapValidator
import com.timedrecorder.core.database.dao.ScheduleTaskDao
import com.timedrecorder.core.database.mapper.asEntity
import com.timedrecorder.core.database.mapper.asExternalModel
import com.timedrecorder.core.model.ScheduleTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineScheduleRepository @Inject constructor(
    private val scheduleTaskDao: ScheduleTaskDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduleRepository {

    override fun observeAllTasks(): Flow<List<ScheduleTask>> =
        scheduleTaskDao.observeAll().map { list -> list.map { it.asExternalModel() } }

    override fun observeEnabledTasks(): Flow<List<ScheduleTask>> =
        scheduleTaskDao.observeEnabled().map { list -> list.map { it.asExternalModel() } }

    override suspend fun getTaskById(id: Long): ScheduleTask? = withContext(ioDispatcher) {
        scheduleTaskDao.getById(id)?.asExternalModel()
    }

    override suspend fun saveTask(task: ScheduleTask): Result<Long> = withContext(ioDispatcher) {
        runCatching {
            // 校验开始时间必须早于结束时间
            if (task.startTimeMinutes >= task.endTimeMinutes) {
                throw IllegalArgumentException("开始时间必须早于结束时间")
            }

            // 重叠校验
            val taskList = scheduleTaskDao.observeAll().first().map { it.asExternalModel() }
            val overlap = ScheduleOverlapValidator.findOverlap(task, taskList)
            if (overlap != null) {
                val label = overlap.taskName
                    ?: "${overlap.formatStartTime()}-${overlap.formatEndTime()}"
                throw IllegalArgumentException("时间段与「$label」重叠，请调整后再保存")
            }

            val entity = task.copy(updatedAt = System.currentTimeMillis()).asEntity()
            if (task.id == 0L) {
                scheduleTaskDao.insert(entity)
            } else {
                scheduleTaskDao.update(entity)
                task.id
            }
        }
    }

    override suspend fun deleteTask(task: ScheduleTask): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            scheduleTaskDao.delete(task.asEntity())
        }
    }

    override suspend fun toggleTaskEnabled(id: Long, enabled: Boolean): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val entity = scheduleTaskDao.getById(id)
                    ?: throw NoSuchElementException("任务不存在")
                scheduleTaskDao.update(
                    entity.copy(enabled = enabled, updatedAt = System.currentTimeMillis()),
                )
            }
        }

    override suspend fun hasEnabledTasks(): Boolean = withContext(ioDispatcher) {
        scheduleTaskDao.countEnabled() > 0
    }
}
