package com.timedrecorder.core.data.scheduler

/**
 * T1：上传任务调度接口，由 sync 模块的 WorkScheduler 实现。
 * feature/files 通过此接口触发批量重试，不直接依赖 sync 模块。
 */
interface UploadScheduler {
    /** 将所有失败/待上传文件重新加入上传队列 */
    fun enqueuePendingUploads()

    /** 将单个文件加入上传队列（上传成功后会自动触发结果轮询） */
    fun enqueueUpload(fileId: Long)

    /** 取消指定文件的上传与结果轮询后台任务 */
    fun cancelFileWork(localFileId: Long)

    /** 上传成功后启动结果轮询（读取用户配置的间隔与次数） */
    fun enqueuePoll(serverFileId: String, localFileId: Long)
}
