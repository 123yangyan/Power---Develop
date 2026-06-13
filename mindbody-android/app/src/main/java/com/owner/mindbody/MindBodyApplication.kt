package com.owner.mindbody

import android.app.Application
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.storage.AppStorage
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.owner.mindbody.polar.PolarBleManager
import kotlinx.coroutines.runBlocking

class MindBodyApplication : Application() {

    lateinit var storage: AppStorage
        private set

    lateinit var devicePreferences: DevicePreferences
        private set

    lateinit var polarBleManager: PolarBleManager
        private set

    override fun onCreate() {
        super.onCreate()
        storage = AppStorage(this)
        devicePreferences = DevicePreferences(this)
        val deviceSyncManager = DeviceSyncManager(storage, storage.deviceSyncPreferences)
        storage.initDeviceSync(deviceSyncManager)
        polarBleManager = PolarBleManager(this, storage, devicePreferences, deviceSyncManager)
    }

    override fun onTerminate() {
        runBlocking {
            storage.flushAll()
        }
        polarBleManager.shutdown()
        super.onTerminate()
    }
}
