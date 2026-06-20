package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SkinTempSampleEntity
import kotlinx.coroutines.flow.Flow

class SkinTempRepository(private val database: AppDatabase) {

    private val dao = database.skinTempSampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<SkinTempSampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveSample(timestamp: Long, temperatureC: Float) {
        buffer.enqueue(
            SkinTempSampleEntity(timestamp = timestamp, temperatureC = temperatureC)
        )
    }

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<SkinTempSampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    suspend fun flush() {
        buffer.flush()
    }

    suspend fun getUnsynced(limit: Int): List<SkinTempSampleEntity> {
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
