package com.timedrecorder.core.model

/**
 * 音频文件上传状态，对应 PRD §9.4。
 */
enum class UploadStatus {
    /** 待上传 */
    PENDING,
    /** 上传中 */
    UPLOADING,
    /** 上传成功 */
    SUCCESS,
    /** 上传失败 */
    FAILED,
    /** 重试中 */
    RETRYING,
}
