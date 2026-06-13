package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.Ppi247SampleEntity

class Ppi247Repository(private val database: AppDatabase) {

    private val dao = database.ppi247SampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<Ppi247SampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveAll(samples: List<Ppi247SampleEntity>) {
        buffer.enqueueAll(samples)
    }

    suspend fun flush() {
        buffer.flush()
    }
}
