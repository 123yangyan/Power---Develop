package com.timedrecorder.core.model

/** 消息中心消息类型 */
enum class MessageType {
    /** 异常/关键词提醒 */
    ALERT,
    /** 普通系统消息 */
    SYSTEM,
    /** 上传/处理状态通知 */
    STATUS,
}
