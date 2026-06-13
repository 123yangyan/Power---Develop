package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.MoodEntryEntity
import com.owner.mindbody.data.local.SyncState
import com.owner.mindbody.data.local.SyncMeta
import kotlinx.coroutines.flow.Flow

class MoodRepository(private val database: AppDatabase) {

    private val dao = database.moodEntryDao()

    fun observeAll(): Flow<List<MoodEntryEntity>> = dao.observeAll()

    suspend fun getAll(): List<MoodEntryEntity> = dao.getAll()

    suspend fun getById(id: Long): MoodEntryEntity? = dao.getById(id)

    suspend fun insert(
        fact: String,
        coordX: Int,
        coordY: Int,
        occurredAt: Long = System.currentTimeMillis(),
        hrAtEntry: Int? = null
    ): Long {
        val now = System.currentTimeMillis()
        val entity = MoodEntryEntity(
            fact = fact,
            coordX = coordX,
            coordY = coordY,
            occurredAt = occurredAt,
            hrAtEntry = hrAtEntry,
            sync = SyncMeta(createdAt = now, updatedAt = now)
        )
        return dao.insert(entity)
    }

    suspend fun update(
        id: Long,
        fact: String,
        coordX: Int,
        coordY: Int,
        occurredAt: Long,
        hrAtEntry: Int?
    ): MoodEntryEntity? {
        val existing = dao.getById(id) ?: return null
        val updated = existing.copy(
            fact = fact,
            coordX = coordX,
            coordY = coordY,
            occurredAt = occurredAt,
            hrAtEntry = hrAtEntry,
            sync = existing.sync.copy(
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING.name
            )
        )
        dao.update(updated)
        return updated
    }

    suspend fun delete(id: Long): Boolean {
        dao.deleteById(id)
        return true
    }

    suspend fun deleteMany(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        dao.deleteByIds(ids)
        return ids.size
    }

    suspend fun getUnsynced(limit: Int): List<MoodEntryEntity> = dao.getUnsynced(limit)

    suspend fun markSynced(ids: List<Long>, remoteId: String? = null) {
        dao.markSynced(ids, remoteId)
    }

    suspend fun markFailed(ids: List<Long>) {
        dao.markFailed(ids)
    }
}
