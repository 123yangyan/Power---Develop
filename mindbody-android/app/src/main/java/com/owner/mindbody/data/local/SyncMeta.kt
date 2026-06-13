package com.owner.mindbody.data.local

/**
 * 所有需要长期保存、后续同步的数据实体都复用这套元信息。
 *
 * 初学者可以把它理解为每条数据的“档案标签”：
 * - createdAt / updatedAt：这条数据在手机本地创建和更新的时间
 * - syncState：是否已经同步到云端
 * - remoteId：云端返回的 ID，后续同步时用来对应同一条数据
 */
enum class SyncState {
    PENDING,
    SYNCED,
    FAILED
}

data class SyncMeta(
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncState: String = SyncState.PENDING.name,
    val remoteId: String? = null
)
