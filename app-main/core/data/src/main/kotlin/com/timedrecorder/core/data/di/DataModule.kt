package com.timedrecorder.core.data.di

import com.timedrecorder.core.data.repository.AudioFileRepository
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.repository.MessageRepository
import com.timedrecorder.core.data.repository.OfflineAudioFileRepository
import com.timedrecorder.core.data.repository.OfflineLogRepository
import com.timedrecorder.core.data.repository.OfflineMessageRepository
import com.timedrecorder.core.data.repository.OfflineResultRepository
import com.timedrecorder.core.data.repository.OfflineScheduleRepository
import com.timedrecorder.core.data.repository.OfflineUploadRepository
import com.timedrecorder.core.data.repository.ResultRepository
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.data.repository.UploadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt 数据层绑定模块 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: OfflineScheduleRepository): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindAudioFileRepository(impl: OfflineAudioFileRepository): AudioFileRepository

    @Binds
    @Singleton
    abstract fun bindUploadRepository(impl: OfflineUploadRepository): UploadRepository

    @Binds
    @Singleton
    abstract fun bindResultRepository(impl: OfflineResultRepository): ResultRepository

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: OfflineMessageRepository): MessageRepository

    @Binds
    @Singleton
    abstract fun bindLogRepository(impl: OfflineLogRepository): LogRepository
}
