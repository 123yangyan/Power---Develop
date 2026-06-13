package com.timedrecorder.core.model

/** T12：首页各功能卡片的唯一标识符，用于持久化用户自定义排序 */
enum class HomeCardId(val displayName: String) {
    RECORDING_STATUS("录音状态"),
    TODAY_SCHEDULE("今日计划"),
    RECENT_UPLOADS("最近上传"),
    RECENT_RESULTS("最近结果"),
    RECENT_MESSAGES("最近消息"),
}
