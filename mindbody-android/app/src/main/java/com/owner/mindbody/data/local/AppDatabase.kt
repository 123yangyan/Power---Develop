package com.owner.mindbody.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HrSampleEntity::class,
        SkinTempSampleEntity::class,
        AccMinuteSummaryEntity::class,
        PpiSampleEntity::class,
        ActivityDaySummaryEntity::class,
        Hr247SampleEntity::class,
        Ppi247SampleEntity::class,
        SkinTemp247SampleEntity::class,
        NightlyRechargeEntity::class,
        ActivityMinuteSampleEntity::class,
        SleepSessionEntity::class,
        TrainingSessionEntity::class,
        MoodEntryEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun hrSampleDao(): HrSampleDao
    abstract fun skinTempSampleDao(): SkinTempSampleDao
    abstract fun accMinuteSummaryDao(): AccMinuteSummaryDao
    abstract fun ppiSampleDao(): PpiSampleDao
    abstract fun activityDaySummaryDao(): ActivityDaySummaryDao
    abstract fun hr247SampleDao(): Hr247SampleDao
    abstract fun ppi247SampleDao(): Ppi247SampleDao
    abstract fun skinTemp247SampleDao(): SkinTemp247SampleDao
    abstract fun nightlyRechargeDao(): NightlyRechargeDao
    abstract fun activityMinuteSampleDao(): ActivityMinuteSampleDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun trainingSessionDao(): TrainingSessionDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun storageStatsDao(): StorageStatsDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

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
                        id, timestamp, bpm, rrMs,
                        timestamp AS createdAt, timestamp AS updatedAt,
                        CASE WHEN synced = 1 THEN 'SYNCED' ELSE 'PENDING' END,
                        NULL
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

        /** v2 -> v3：在线流三张表（皮肤温度、ACC 分钟聚合、PPI）。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skin_temp_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        temperatureC REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skin_temp_samples_timestamp ON skin_temp_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skin_temp_samples_syncState ON skin_temp_samples(syncState)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_skin_temp_samples_timestamp_syncState
                    ON skin_temp_samples(timestamp, syncState)
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS acc_minute_summary (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        minuteTimestamp INTEGER NOT NULL,
                        avgMagnitudeMg REAL NOT NULL,
                        maxMagnitudeMg REAL NOT NULL,
                        sampleCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_acc_minute_summary_minuteTimestamp
                    ON acc_minute_summary(minuteTimestamp)
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_acc_minute_summary_syncState ON acc_minute_summary(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ppi_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        ppiMs INTEGER NOT NULL,
                        errorEstimateMs INTEGER,
                        hrBpm INTEGER,
                        blockerBit INTEGER NOT NULL,
                        skinContactSupported INTEGER NOT NULL,
                        skinContactStatus INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ppi_samples_timestamp ON ppi_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ppi_samples_syncState ON ppi_samples(syncState)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_ppi_samples_timestamp_syncState
                    ON ppi_samples(timestamp, syncState)
                    """.trimIndent()
                )
            }
        }

        /** v3 -> v4：设备离线同步八张表。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_day_summary (
                        date TEXT NOT NULL PRIMARY KEY,
                        steps INTEGER,
                        activeTimeMinutes INTEGER,
                        caloriesTotal INTEGER,
                        caloriesActivity INTEGER,
                        caloriesTraining INTEGER,
                        caloriesBmr INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_day_summary_syncState ON activity_day_summary(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hr_247_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        bpm INTEGER NOT NULL,
                        triggerType TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_247_samples_timestamp ON hr_247_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_hr_247_samples_syncState ON hr_247_samples(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ppi_247_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        ppiMs INTEGER NOT NULL,
                        errorEstimateMs INTEGER,
                        triggerType TEXT,
                        skinContact TEXT,
                        movement TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ppi_247_samples_timestamp ON ppi_247_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ppi_247_samples_syncState ON ppi_247_samples(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skin_temp_247_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        temperatureC REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skin_temp_247_samples_timestamp ON skin_temp_247_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skin_temp_247_samples_syncState ON skin_temp_247_samples(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nightly_recharge (
                        date TEXT NOT NULL PRIMARY KEY,
                        ansChargePercent INTEGER,
                        recoveryIndicator INTEGER,
                        ansRate INTEGER,
                        hrMeanBpm INTEGER,
                        hrMinBpm INTEGER,
                        rrMeanMs INTEGER,
                        breathingRateHz REAL,
                        sleepTip TEXT,
                        vitalityTip TEXT,
                        exerciseTip TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nightly_recharge_syncState ON nightly_recharge(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS activity_minute_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        steps INTEGER,
                        metX100 INTEGER,
                        activityLevel INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_minute_samples_timestamp ON activity_minute_samples(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_activity_minute_samples_syncState ON activity_minute_samples(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sleep_sessions (
                        date TEXT NOT NULL PRIMARY KEY,
                        sleepStartTimeMs INTEGER,
                        sleepEndTimeMs INTEGER,
                        sleepGoalMinutes INTEGER,
                        userSleepRating INTEGER,
                        batteryRanOut INTEGER NOT NULL,
                        sleepSkinTempCelsius REAL,
                        sleepSkinTempDeviation REAL,
                        sleepWakePhasesJson TEXT,
                        sleepCyclesJson TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sleep_sessions_syncState ON sleep_sessions(syncState)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_sessions (
                        devicePath TEXT NOT NULL PRIMARY KEY,
                        sessionDate TEXT NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        exerciseCount INTEGER NOT NULL,
                        startTimeMs INTEGER,
                        endTimeMs INTEGER,
                        durationSeconds INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_training_sessions_syncState ON training_sessions(syncState)")
            }
        }

        /** v5 -> v6：mood_entries 增加 roleId（情绪角色 ID）。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mood_entries ADD COLUMN roleId TEXT")
            }
        }

        /** v4 -> v5：心情记录表 mood_entries。 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mood_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        fact TEXT NOT NULL,
                        coordX INTEGER NOT NULL,
                        coordY INTEGER NOT NULL,
                        occurredAt INTEGER NOT NULL,
                        hrAtEntry INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        syncState TEXT NOT NULL,
                        remoteId TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_entries_occurredAt ON mood_entries(occurredAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_entries_syncState ON mood_entries(syncState)")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_mood_entries_occurredAt_syncState
                    ON mood_entries(occurredAt, syncState)
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
