package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SkinTempSampleEntity
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

    suspend fun flush() {
        buffer.flush()
    }
}
