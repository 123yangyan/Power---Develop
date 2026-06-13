package com.timedrecorder.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.timedrecorder.core.database.converter.EnumConverters
import com.timedrecorder.core.database.dao.AppLogDao
import com.timedrecorder.core.database.dao.AudioFileDao
import com.timedrecorder.core.database.dao.MessageDao
import com.timedrecorder.core.database.dao.ProcessResultDao
import com.timedrecorder.core.database.dao.ScheduleTaskDao
import com.timedrecorder.core.database.entity.AppLogEntity
import com.timedrecorder.core.database.entity.AudioFileEntity
import com.timedrecorder.core.database.entity.MessageEntity
import com.timedrecorder.core.database.entity.ProcessResultEntity
import com.timedrecorder.core.database.entity.ScheduleTaskEntity

/**
 * 本地数据库，包含 PRD §13 定义的五张表。
 */
@Database(
    entities = [
        ScheduleTaskEntity::class,
        AudioFileEntity::class,
        ProcessResultEntity::class,
        MessageEntity::class,
        AppLogEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
abstract class RecorderDatabase : RoomDatabase() {
    abstract fun scheduleTaskDao(): ScheduleTaskDao
    abstract fun audioFileDao(): AudioFileDao
    abstract fun processResultDao(): ProcessResultDao
    abstract fun messageDao(): MessageDao
    abstract fun appLogDao(): AppLogDao

    companion object {
        /** T4：V1 → V2，新增手动录音标记字段 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE audio_file ADD COLUMN is_manual_recording INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** process_result.file_id 唯一索引，轮询结果按文件 upsert */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM process_result
                    WHERE id NOT IN (
                        SELECT MAX(id) FROM process_result GROUP BY file_id
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_process_result_file_id ON process_result(file_id)",
                )
            }
        }

        /** V3 → V4：新增 ASR 转写字段 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE process_result ADD COLUMN transcript TEXT",
                )
            }
        }

        /** V4 → V5：新增 AI 短标题字段 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE process_result ADD COLUMN title TEXT",
                )
            }
        }
    }
}
