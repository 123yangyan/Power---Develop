package com.owner.mindbody.ui.mood

/**
 * 由坐标推导象限名称，与 emotion entryParse 一致。
 */
fun getQuadrantLabel(coordX: Int, coordY: Int): String {
    return when {
        coordX > 0 && coordY > 0 -> "攻坚区"
        coordX > 0 && coordY <= 0 -> "心流区"
        coordX <= 0 && coordY <= 0 -> "机械区"
        else -> "内耗陷阱"
    }
}

/** 四象限 id，对齐 emotion dayAnalytics.assignQuadrant */
fun assignQuadrant(coordX: Int, coordY: Int): String {
    return when {
        coordX <= 0 && coordY > 0 -> "tl"
        coordX > 0 && coordY > 0 -> "tr"
        coordX <= 0 && coordY <= 0 -> "bl"
        else -> "br"
    }
}

/** 耗能强度 1–9，对齐 emotion entryParse / CoordMiniBadge */
fun coordIntensity(coordY: Int): Int {
    return (coordY + 5).coerceIn(1, 9)
}

/** 历史卡片极性，对齐 historyRowPreview */
enum class MoodPolarity {
    POSITIVE, NEGATIVE, NEUTRAL
}

fun moodPolarity(coordX: Int, coordY: Int): MoodPolarity {
    return when (assignQuadrant(coordX, coordY)) {
        "br", "tr" -> MoodPolarity.POSITIVE
        "tl" -> MoodPolarity.NEGATIVE
        else -> MoodPolarity.NEUTRAL
    }
}

/** 格式化坐标显示，如 (+2, -1) */
fun formatCoord(coordX: Int, coordY: Int): String {
    val xStr = if (coordX > 0) "+$coordX" else coordX.toString()
    val yStr = if (coordY > 0) "+$coordY" else coordY.toString()
    return "($xStr, $yStr)"
}
