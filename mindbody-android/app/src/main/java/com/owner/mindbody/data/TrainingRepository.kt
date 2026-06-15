package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.TrainingSessionEntity
import kotlinx.coroutines.flow.Flow

class TrainingRepository(private val database: AppDatabase) {

    private val dao = database.trainingSessionDao()

    suspend fun upsert(entity: TrainingSessionEntity) {
        dao.upsert(entity)
    }

    suspend fun upsertAll(entities: List<TrainingSessionEntity>) {
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    suspend fun getAllDevicePaths(): List<String> = dao.getAllDevicePaths()

    suspend fun getUnsynced(limit: Int = 500) = dao.getUnsynced(limit)

    fun observeSessionsBetween(startMs: Long, endMs: Long): Flow<List<TrainingSessionEntity>> {
        return dao.observeSessionsBetween(startMs, endMs)
    }
}
