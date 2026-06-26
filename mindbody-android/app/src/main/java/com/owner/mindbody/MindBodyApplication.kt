package com.owner.mindbody

import android.app.Application
import com.owner.mindbody.BuildConfig
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.DeveloperPreferences
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.data.stream.PpiUploadLogBuffer
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.owner.mindbody.polar.PolarBleManager
import com.owner.mindbody.util.AppLogger
import com.owner.mindbody.notification.PhysioNotificationManager
import com.owner.mindbody.worker.BleSchedulerWorker
import com.owner.mindbody.worker.MoodReminderScheduler
import com.owner.mindbody.worker.MoodReminderWorker
import com.owner.mindbody.worker.PpiStreamWorker
import com.owner.mindbody.worker.PruneDataWorker
import com.owner.mindbody.worker.SyncWorker
import androidx.work.ExistingWorkPolicy
import com.polar.sdk.api.PolarBleApiDefaultImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MindBodyApplication : Application() {

    lateinit var storage: AppStorage
        private set

    lateinit var devicePreferences: DevicePreferences
        private set

    lateinit var moodPreferences: MoodPreferences
        private set

    lateinit var developerPreferences: DeveloperPreferences
        private set

    lateinit var polarBleManager: PolarBleManager
        private set

    val ppiUploadLogBuffer: PpiUploadLogBuffer = PpiUploadLogBuffer()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            storage = AppStorage(this, applicationScope)
            applicationScope.launch {
                storage.loadPersistedFeedback()
            }
            devicePreferences = DevicePreferences(this)
            moodPreferences = MoodPreferences(this)
            developerPreferences = DeveloperPreferences(this)
            val deviceSyncManager = DeviceSyncManager(storage, storage.deviceSyncPreferences)
            storage.initDeviceSync(deviceSyncManager)
            polarBleManager = PolarBleManager(this, storage, devicePreferences, deviceSyncManager)
            AppLogger.i(
                "MindBodyApp",
                "启动 v${BuildConfig.VERSION_NAME} · Polar SDK ${PolarBleApiDefaultImpl.versionInfo()}"
            )
            PhysioNotificationManager.ensureChannel(this)
            MoodReminderScheduler.schedule(this)
            MoodReminderWorker.createChannel(this)
            SyncWorker.schedulePeriodic(this)
            PpiStreamWorker.scheduleRepeating(this)
            PruneDataWorker.schedulePeriodic(this)
            runBlocking {
                if (devicePreferences.bleNightlyScheduleEnabled.first()) {
                    BleSchedulerWorker.scheduleFromPreferences(
                        this@MindBodyApplication,
                        ExistingWorkPolicy.KEEP
                    )
                } else {
                    BleSchedulerWorker.cancel(this@MindBodyApplication)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MindBodyApp", "初始化失败，请重启应用", e)
            android.widget.Toast.makeText(
                this,
                "应用初始化失败，请尝试重启应用",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onTerminate() {
        runBlocking {
            storage.flushAll()
        }
        polarBleManager.shutdown()
        super.onTerminate()
    }
}
