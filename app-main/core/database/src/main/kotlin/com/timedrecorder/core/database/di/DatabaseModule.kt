package com.timedrecorder.core.database.di

import android.content.Context
import androidx.room.Room
import com.timedrecorder.core.database.RecorderDatabase
import com.timedrecorder.core.database.dao.AppLogDao
import com.timedrecorder.core.database.dao.AudioFileDao
import com.timedrecorder.core.database.dao.MessageDao
import com.timedrecorder.core.database.dao.ProcessResultDao
import com.timedrecorder.core.database.dao.ScheduleTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt 模块：提供 Room Database 与各 Dao */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideRecorderDatabase(
        @ApplicationContext context: Context,
    ): RecorderDatabase = Room.databaseBuilder(
        context,
        RecorderDatabase::class.java,
        "recorder.db",
    )
        .addMigrations(
            RecorderDatabase.MIGRATION_1_2,
            RecorderDatabase.MIGRATION_2_3,
            RecorderDatabase.MIGRATION_3_4,
            RecorderDatabase.MIGRATION_4_5,
        )
        .build()

    @Provides
    fun provideScheduleTaskDao(db: RecorderDatabase): ScheduleTaskDao = db.scheduleTaskDao()

    @Provides
    fun provideAudioFileDao(db: RecorderDatabase): AudioFileDao = db.audioFileDao()

    @Provides
    fun provideProcessResultDao(db: RecorderDatabase): ProcessResultDao = db.processResultDao()

    @Provides
    fun provideMessageDao(db: RecorderDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideAppLogDao(db: RecorderDatabase): AppLogDao = db.appLogDao()
}
