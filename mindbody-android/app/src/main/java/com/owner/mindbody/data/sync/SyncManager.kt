package com.owner.mindbody.data.sync

import com.owner.mindbody.data.HrRepository
import com.owner.mindbody.data.local.HrSampleEntity

/**
 * 云端同步预留入口。
 *
 * 当前阶段只负责提供“从本地拿未同步数据”的统一接口，不做真实网络请求。
 * Phase 3 接入 server `/api/vitals/hr/batch` 时，可以在这里补 Retrofit/WorkManager。
 */
class SyncManager(
    private val hrRepository: HrRepository
) {
    suspend fun getPendingHrSamples(limit: Int = 500): List<HrSampleEntity> {
        return hrRepository.getUnsynced(limit)
    }

    suspend fun markHrSamplesSynced(ids: List<Long>, remoteId: String? = null) {
        hrRepository.markSynced(ids, remoteId)
    }

    suspend fun markHrSamplesFailed(ids: List<Long>) {
        hrRepository.markFailed(ids)
    }

    suspend fun syncOnce(): SyncResult {
        // TODO Phase 3：这里对接 Retrofit + WorkManager，批量上传 HR / mood / guidance 数据。
        val pendingHrCount = getPendingHrSamples().size
        return SyncResult(pendingHrCount = pendingHrCount)
    }
}

data class SyncResult(
    val pendingHrCount: Int = 0
)
