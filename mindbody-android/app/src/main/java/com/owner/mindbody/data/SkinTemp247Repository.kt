package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SkinTemp247SampleEntity

class SkinTemp247Repository(private val database: AppDatabase) {

    private val dao = database.skinTemp247SampleDao()
    private val buffer = EntitySampleBuffer(
        insertBatch = { samples: List<SkinTemp247SampleEntity> ->
            dao.insertAll(samples)
        }
    )

    suspend fun saveAll(samples: List<SkinTemp247SampleEntity>) {
        buffer.enqueueAll(samples)
    }

    suspend fun flush() {
        buffer.flush()
    }
}
