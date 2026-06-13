package com.owner.mindbody.data.storage

import android.content.Context
import com.owner.mindbody.data.HrRepository
import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.sync.SyncManager

/**
 * App 的统一存储入口。
 *
 * 后续所有功能模块都应优先从这里拿 Repository，而不是到处直接 new。
 * 这样未来新增“心情记录”“AI 指导缓存”“设备事件”等数据类型时，
 * 调用方式可以保持统一：app.storage.xxx。
 */
class AppStorage(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val hr: HrRepository = HrRepository(database)
    val sync: SyncManager = SyncManager(hr)

    suspend fun flushAll() {
        hr.flush()
    }
}
