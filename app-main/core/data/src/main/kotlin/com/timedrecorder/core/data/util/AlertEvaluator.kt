package com.timedrecorder.core.data.util

import com.timedrecorder.core.model.RiskLevel
import com.timedrecorder.core.network.dto.AudioResultDto

/**
 * 通知触发规则判定，对应 PRD §7 第 4 点。
 * 满足任一条件即触发：alertFlag=true / riskLevel=medium|high / keywords 非空
 */
object AlertEvaluator {
    fun shouldNotify(dto: AudioResultDto): Boolean {
        if (dto.alertFlag) return true
        if (dto.keywords.isNotEmpty()) return true
        val risk = RiskLevel.fromApiValue(dto.riskLevel)
        return risk == RiskLevel.MEDIUM || risk == RiskLevel.HIGH
    }

    fun shouldNotify(alertFlag: Boolean, keywords: List<String>, riskLevel: RiskLevel?): Boolean {
        if (alertFlag) return true
        if (keywords.isNotEmpty()) return true
        return riskLevel == RiskLevel.MEDIUM || riskLevel == RiskLevel.HIGH
    }
}
