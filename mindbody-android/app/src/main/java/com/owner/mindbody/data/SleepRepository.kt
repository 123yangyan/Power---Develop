package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.SleepSessionEntity

class SleepRepository(private val database: AppDatabase) {

    private val dao = database.sleepSessionDao()

    suspend fun upsertAll(entities: List<SleepSessionEntity>) {
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    suspend fun getUnsynced(limit: Int = 500) = dao.getUnsynced(limit)
}
