package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.PpiSampleEntity
import com.polar.sdk.api.model.PolarPpiData
import kotlinx.coroutines.flow.Flow

class PpiRepository(private val database: AppDatabase) {

    private val dao = database.ppiSampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<PpiSampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveSample(timestamp: Long, sample: PolarPpiData.PolarPpiSample) {
        buffer.enqueue(
            PpiSampleEntity(
                timestamp = timestamp,
                ppiMs = sample.ppi,
                errorEstimateMs = sample.errorEstimate,
                hrBpm = sample.hr,
                blockerBit = sample.blockerBit,
                skinContactSupported = sample.skinContactSupported,
                skinContactStatus = sample.skinContactStatus
            )
        )
    }

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<PpiSampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    suspend fun flush() {
        buffer.flush()
    }

    suspend fun getUnsynced(limit: Int): List<PpiSampleEntity> {
        flush()
        return dao.getUnsynced(limit)
    }

    suspend fun markSynced(ids: List<Long>, remoteId: String? = null) {
        dao.markSynced(ids, remoteId)
    }

    suspend fun markFailed(ids: List<Long>) {
        dao.markFailed(ids)
    }
}
