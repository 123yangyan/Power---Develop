package com.timedrecorder.core.model

/**
 * 风险等级，与服务端 JSON 枚举一致（小写字符串）。
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    ;

    /** 转为服务端约定的小写字符串 */
    fun toApiValue(): String = name.lowercase()

    companion object {
        fun fromApiValue(value: String?): RiskLevel? =
            value?.uppercase()?.let { runCatching { valueOf(it) }.getOrNull() }
    }
}
