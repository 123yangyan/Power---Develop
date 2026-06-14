package com.owner.mindbody.data.storage

import android.content.Context
import com.owner.mindbody.data.AccRepository
import com.owner.mindbody.data.ActivityDayRepository
import com.owner.mindbody.data.ActivityMinuteRepository
import com.owner.mindbody.data.DeviceSyncPreferences
import com.owner.mindbody.data.Hr247Repository
import com.owner.mindbody.data.HrRepository
import com.owner.mindbody.data.MoodRepository
import com.owner.mindbody.data.NightlyRechargeRepository
import com.owner.mindbody.data.Ppi247Repository
import com.owner.mindbody.data.PpiRepository
import com.owner.mindbody.data.SleepRepository
import com.owner.mindbody.data.SkinTemp247Repository
import com.owner.mindbody.data.SkinTempRepository
import com.owner.mindbody.data.StorageStatsRepository
import com.owner.mindbody.data.TrainingRepository
import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.sync.DeviceSyncManager
import com.owner.mindbody.data.sync.SyncManager

/**
 * App 的统一存储入口。
 * 功能层只通过 app.storage.xxx 读写数据，禁止绕过门面直接访问 DAO。
 */
class AppStorage(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)

    // 实时 BLE 流
    val hr: HrRepository = HrRepository(database)
    val skinTemp: SkinTempRepository = SkinTempRepository(database)
    val acc: AccRepository = AccRepository(database)
    val ppi: PpiRepository = PpiRepository(database)

    // 设备离线同步
    val activityDay: ActivityDayRepository = ActivityDayRepository(database)
    val activityMinute: ActivityMinuteRepository = ActivityMinuteRepository(database)
    val hr247: Hr247Repository = Hr247Repository(database)
    val ppi247: Ppi247Repository = Ppi247Repository(database)
    val skinTemp247: SkinTemp247Repository = SkinTemp247Repository(database)
    val nightlyRecharge: NightlyRechargeRepository = NightlyRechargeRepository(database)
    val sleep: SleepRepository = SleepRepository(database)
    val training: TrainingRepository = TrainingRepository(database)

    // Phase 2 心情记录
    val mood: MoodRepository = MoodRepository(database)

    /** 开发者看板：各表行数统计（只读） */
    val storageStats: StorageStatsRepository = StorageStatsRepository(database, this)

    val sync: SyncManager = SyncManager(hr)
    val deviceSyncPreferences: DeviceSyncPreferences = DeviceSyncPreferences(context)
    lateinit var deviceSync: DeviceSyncManager

    fun initDeviceSync(deviceSyncManager: DeviceSyncManager) {
        deviceSync = deviceSyncManager
    }

    suspend fun flushAll() {
        hr.flush()
        skinTemp.flush()
        acc.flush()
        ppi.flush()
        hr247.flush()
        ppi247.flush()
        skinTemp247.flush()
        activityMinute.flush()
    }
}
