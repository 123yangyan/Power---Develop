package com.timedrecorder.sync.di

import com.timedrecorder.core.data.record.RecordingController
import com.timedrecorder.core.data.scheduler.RecordingScheduler
import com.timedrecorder.core.data.scheduler.UploadScheduler
import com.timedrecorder.sync.RecordServiceController
import com.timedrecorder.sync.scheduler.SyncRecordingScheduler
import com.timedrecorder.sync.worker.WorkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindModule {
    @Binds
    @Singleton
    abstract fun bindRecordingScheduler(impl: SyncRecordingScheduler): RecordingScheduler

    /**
     * 绑定 RecordingController 接口。
     * feature/home 的 HomeViewModel 通过此接口控制录音会话。
     */
    @Binds
    @Singleton
    abstract fun bindRecordingController(impl: RecordServiceController): RecordingController

    /**
     * T1/T10：绑定 UploadScheduler 接口。
     * feature/files 的 FilesViewModel 通过此接口触发批量重试上传。
     */
    @Binds
    @Singleton
    abstract fun bindUploadScheduler(impl: WorkScheduler): UploadScheduler
}
