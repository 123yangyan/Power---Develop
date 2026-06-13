package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.Hr247SampleEntity

class Hr247Repository(private val database: AppDatabase) {

    private val dao = database.hr247SampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<Hr247SampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveAll(samples: List<Hr247SampleEntity>) {
        buffer.enqueueAll(samples)
    }

    suspend fun flush() {
        buffer.flush()
    }
}
