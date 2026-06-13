package com.owner.mindbody.data.sync

/**
 * 所有“未来要上传云端”的 DAO 都应实现这个契约。
 *
 * 这样后续新增心情记录、AI 指导、设备事件等数据时，同步模块不用关心具体表名，
 * 只需要按统一接口拿“未同步数据”、标记“已同步”或“失败”。
 */
interface SyncableDao<T> {
    suspend fun getUnsynced(limit: Int): List<T>

    suspend fun markSynced(ids: List<Long>, remoteId: String?)

    suspend fun markFailed(ids: List<Long>)
}
