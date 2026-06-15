package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SkinTemp247SampleEntity
import kotlinx.coroutines.flow.Flow

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

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<SkinTemp247SampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    suspend fun flush() {
        buffer.flush()
    }
}
