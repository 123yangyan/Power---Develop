package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.StorageStatsDao
import com.owner.mindbody.data.storage.AppStorage

/** 表所属类别，用于看板分组展示 */
enum class StorageTableCategory(val label: String) {
    LIVE_STREAM("实时 BLE 流"),
    OFFLINE_SYNC("设备离线同步"),
    BUSINESS("业务数据")
}

/** 单表统计快照 */
data class TableStorageStat(
    val tableName: String,
    val displayName: String,
    val category: StorageTableCategory,
    val rowCount: Long,
    val lastUpdatedMs: Long?
)

/**
 * 读取 Room 各表行数与最近更新时间，供开发者看板使用。
 * 统计前会先 flush 缓冲，确保数字反映已落盘数据。
 */
class StorageStatsRepository(
    private val database: AppDatabase,
    private val storage: AppStorage
) {
    private val dao: StorageStatsDao = database.storageStatsDao()

    suspend fun loadStats(): List<TableStorageStat> {
        storage.flushAll()
        return listOf(
            stat(
                tableName = "hr_samples",
                displayName = "实时心率",
                category = StorageTableCategory.LIVE_STREAM,
                count = dao.countHrSamples(),
                lastUpdatedMs = dao.lastHrSampleTimestamp()
            ),
            stat(
                tableName = "skin_temp_samples",
                displayName = "实时皮肤温度",
                category = StorageTableCategory.LIVE_STREAM,
                count = dao.countSkinTempSamples(),
                lastUpdatedMs = dao.lastSkinTempTimestamp()
            ),
            stat(
                tableName = "acc_minute_summary",
                displayName = "加速度10秒汇总",
                category = StorageTableCategory.LIVE_STREAM,
                count = dao.countAccMinuteSummary(),
                lastUpdatedMs = dao.lastAccMinuteTimestamp()
            ),
            stat(
                tableName = "ppi_samples",
                displayName = "实时 PPI",
                category = StorageTableCategory.LIVE_STREAM,
                count = dao.countPpiSamples(),
                lastUpdatedMs = dao.lastPpiTimestamp()
            ),
            stat(
                tableName = "hr_247_samples",
                displayName = "24/7 心率",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countHr247Samples(),
                lastUpdatedMs = dao.lastHr247Timestamp()
            ),
            stat(
                tableName = "ppi_247_samples",
                displayName = "24/7 PPI",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countPpi247Samples(),
                lastUpdatedMs = dao.lastPpi247Timestamp()
            ),
            stat(
                tableName = "skin_temp_247_samples",
                displayName = "24/7 皮肤温度",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countSkinTemp247Samples(),
                lastUpdatedMs = dao.lastSkinTemp247Timestamp()
            ),
            stat(
                tableName = "activity_day_summary",
                displayName = "每日活动汇总",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countActivityDaySummary(),
                lastUpdatedMs = dao.lastActivityDayUpdatedAt()
            ),
            stat(
                tableName = "activity_minute_samples",
                displayName = "活动分钟样本",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countActivityMinuteSamples(),
                lastUpdatedMs = dao.lastActivityMinuteTimestamp()
            ),
            stat(
                tableName = "nightly_recharge",
                displayName = "夜间恢复",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countNightlyRecharge(),
                lastUpdatedMs = dao.lastNightlyRechargeUpdatedAt()
            ),
            stat(
                tableName = "sleep_sessions",
                displayName = "睡眠会话",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countSleepSessions(),
                lastUpdatedMs = dao.lastSleepUpdatedAt()
            ),
            stat(
                tableName = "training_sessions",
                displayName = "训练会话",
                category = StorageTableCategory.OFFLINE_SYNC,
                count = dao.countTrainingSessions(),
                lastUpdatedMs = dao.lastTrainingUpdatedAt()
            ),
            stat(
                tableName = "mood_entries",
                displayName = "心情记录",
                category = StorageTableCategory.BUSINESS,
                count = dao.countMoodEntries(),
                lastUpdatedMs = dao.lastMoodOccurredAt()
            )
        )
    }

    private fun stat(
        tableName: String,
        displayName: String,
        category: StorageTableCategory,
        count: Long,
        lastUpdatedMs: Long?
    ): TableStorageStat {
        return TableStorageStat(
            tableName = tableName,
            displayName = displayName,
            category = category,
            rowCount = count,
            lastUpdatedMs = if (count > 0) lastUpdatedMs else null
        )
    }
}
