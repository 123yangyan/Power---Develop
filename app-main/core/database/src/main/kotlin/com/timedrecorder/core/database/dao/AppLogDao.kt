package com.timedrecorder.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.timedrecorder.core.database.entity.AppLogEntity
import com.timedrecorder.core.model.LogType
import kotlinx.coroutines.flow.Flow

/** 诊断日志 DAO */
@Dao
interface AppLogDao {
    @Query("SELECT * FROM app_log ORDER BY created_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AppLogEntity>>

    @Query("SELECT * FROM app_log WHERE log_type = :type ORDER BY created_at DESC LIMIT :limit")
    fun observeByType(type: LogType, limit: Int): Flow<List<AppLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AppLogEntity): Long

    @Query("DELETE FROM app_log WHERE created_at < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)
}
