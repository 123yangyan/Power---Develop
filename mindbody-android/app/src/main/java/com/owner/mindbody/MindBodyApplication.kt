package com.owner.mindbody

import android.app.Application
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.data.storage.AppStorage
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
        polarBleManager = PolarBleManager(this, storage.hr, devicePreferences)
    }

    override fun onTerminate() {
        runBlocking {
            storage.flushAll()
        }
        polarBleManager.shutdown()
        super.onTerminate()
    }
}
