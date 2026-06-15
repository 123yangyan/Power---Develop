package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.Ppi247SampleEntity
import kotlinx.coroutines.flow.Flow

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

    fun observeBetween(startMs: Long, endMs: Long): Flow<List<Ppi247SampleEntity>> {
        return dao.observeBetween(startMs, endMs)
    }

    suspend fun flush() {
        buffer.flush()
    }
}
