package com.timedrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timedrecorder.core.database.entity.ScheduleTaskEntity
import kotlinx.coroutines.flow.Flow

/** 录音计划任务 DAO */
@Dao
interface ScheduleTaskDao {
    @Query("SELECT * FROM task_schedule ORDER BY start_time ASC")
    fun observeAll(): Flow<List<ScheduleTaskEntity>>

    @Query("SELECT * FROM task_schedule WHERE enabled = 1 ORDER BY start_time ASC")
    fun observeEnabled(): Flow<List<ScheduleTaskEntity>>

    @Query("SELECT * FROM task_schedule WHERE id = :id")
    suspend fun getById(id: Long): ScheduleTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleTaskEntity): Long

    @Update
    suspend fun update(entity: ScheduleTaskEntity)

    @Delete
    suspend fun delete(entity: ScheduleTaskEntity)

    @Query("SELECT COUNT(*) FROM task_schedule WHERE enabled = 1")
    suspend fun countEnabled(): Int
}
