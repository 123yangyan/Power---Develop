package com.owner.mindbody.data

import com.owner.mindbody.data.local.ActivityDaySummaryEntity
import com.owner.mindbody.data.local.AppDatabase

class ActivityDayRepository(private val database: AppDatabase) {

    private val dao = database.activityDaySummaryDao()

    suspend fun upsert(entity: ActivityDaySummaryEntity) {
        dao.upsert(entity)
    }

    suspend fun upsertAll(entities: List<ActivityDaySummaryEntity>) {
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    suspend fun getUnsynced(limit: Int = 500) = dao.getUnsynced(limit)
}
