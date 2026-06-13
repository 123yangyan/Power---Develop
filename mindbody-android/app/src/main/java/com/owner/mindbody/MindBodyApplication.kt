package com.owner.mindbody

import android.app.Application
import com.owner.mindbody.BuildConfig
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.DeveloperPreferences
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.owner.mindbody.polar.PolarBleManager
import com.owner.mindbody.util.AppLogger
import com.owner.mindbody.worker.MoodReminderScheduler
import com.owner.mindbody.worker.MoodReminderWorker
import com.polar.sdk.api.PolarBleApiDefaultImpl
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

    override fun onCreate() {
        super.onCreate()
        storage = AppStorage(this)
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
        MoodReminderScheduler.schedule(this)
        MoodReminderWorker.createChannel(this)
    }

    override fun onTerminate() {
        runBlocking {
            storage.flushAll()
        }
        polarBleManager.shutdown()
        super.onTerminate()
    }
}
