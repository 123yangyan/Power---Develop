package com.owner.mindbody.data

import com.owner.mindbody.data.local.ActivityMinuteSampleEntity
import com.owner.mindbody.data.local.AppDatabase

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

    suspend fun flush() {
        buffer.flush()
    }
}
