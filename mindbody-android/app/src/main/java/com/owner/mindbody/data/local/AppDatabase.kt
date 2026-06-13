package com.owner.mindbody.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HrSampleEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hrSampleDao(): HrSampleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v1 -> v2：
         * 把旧的 synced 布尔字段迁移为通用 SyncMeta 字段。
         *
         * 这里不用 destructive migration（破坏性重建），因为用户明确要求数据完整性，
         * 旧心率数据必须一条不丢地复制到新表。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hr_samples_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        bpm INTEGER NOT NULL,
                        rrMs INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO hr_samples_new (
                        id, timestamp, bpm, rrMs, createdAt, updatedAt, syncState, remoteId
                    )
                    SELECT
                        id,
                        timestamp,
                        bpm,
                        rrMs,
                        timestamp AS createdAt,
                        timestamp AS updatedAt,
                        CASE WHEN synced = 1 THEN 'SYNCED' ELSE 'PENDING' END AS syncState,
                        NULL AS remoteId
                    FROM hr_samples
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE hr_samples")
                db.execSQL("ALTER TABLE hr_samples_new RENAME TO hr_samples")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_samples_timestamp ON hr_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_samples_syncState ON hr_samples(syncState)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_hr_samples_timestamp_syncState
                    ON hr_samples(timestamp, syncState)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindbody.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
