package com.timedrecorder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.timedrecorder.sync.worker.PendingPollResumer
import com.timedrecorder.sync.worker.WorkScheduler
import com.timedrecorder.widget.WidgetDataUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RecorderApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var workScheduler: WorkScheduler
    @Inject lateinit var pendingPollResumer: PendingPollResumer
    @Inject lateinit var widgetDataUpdater: WidgetDataUpdater

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        workScheduler.scheduleDailyCleanup()
        // 补偿轮询：修复「已上传但无摘要」的遗漏文件
        applicationScope.launch {
            pendingPollResumer.resume()
        }
        // T11：启动 Widget 数据同步
        widgetDataUpdater.beginObserving()
    }
}
