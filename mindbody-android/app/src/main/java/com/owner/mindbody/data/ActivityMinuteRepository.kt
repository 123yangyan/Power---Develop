package com.owner.mindbody.data

import com.owner.mindbody.data.local.ActivityMinuteSampleEntity
import com.owner.mindbody.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow

class ActivityMinuteRepository(private val database: AppDatabase) {

    private val dao = database.activityMinuteSampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<ActivityMinuteSampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveAll(samples: List<ActivityMinuteSampleEntity>) {
        buffer.enqueueAll(samples)
    }

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<ActivityMinuteSampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    suspend fun flush() {
        buffer.flush()
    }

    suspend fun getUnsynced(limit: Int): List<ActivityMinuteSampleEntity> {
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
