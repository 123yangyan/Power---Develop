package com.owner.mindbody.data

import com.owner.mindbody.data.local.AppDatabase
import com.owner.mindbody.data.local.NightlyRechargeEntity

class NightlyRechargeRepository(private val database: AppDatabase) {

    private val dao = database.nightlyRechargeDao()

    suspend fun upsertAll(entities: List<NightlyRechargeEntity>) {
        if (entities.isNotEmpty()) dao.upsertAll(entities)
    }

    suspend fun getUnsynced(limit: Int = 500) = dao.getUnsynced(limit)

    suspend fun markSynced(dates: List<String>, remoteId: String? = null) {
        if (dates.isNotEmpty()) dao.markSynced(dates, remoteId)
    }
}
