package com.timedrecorder.core.data.repository

import com.timedrecorder.core.model.ScheduleTask
import kotlinx.coroutines.flow.Flow

/** 录音计划任务 Repository 接口 */
interface ScheduleRepository {
    fun observeAllTasks(): Flow<List<ScheduleTask>>
    fun observeEnabledTasks(): Flow<List<ScheduleTask>>
    suspend fun getTaskById(id: Long): ScheduleTask?
    suspend fun saveTask(task: ScheduleTask): Result<Long>
    suspend fun deleteTask(task: ScheduleTask): Result<Unit>
    suspend fun toggleTaskEnabled(id: Long, enabled: Boolean): Result<Unit>
    suspend fun hasEnabledTasks(): Boolean
}
