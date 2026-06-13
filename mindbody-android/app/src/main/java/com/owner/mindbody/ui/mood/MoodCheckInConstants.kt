package com.owner.mindbody.ui.mood

/** Esc 稍后提醒时静默写入的「逃避记录」标识，对齐 emotion checkin.ts */
object MoodCheckInConstants {
    const val AVOIDANCE_FACT = "逃避记录"
    const val AVOIDANCE_THOUGHT = "不想面对此刻的状态"
    const val SNOOZE_INTERVAL_MS = 20 * 60 * 1000L
}

fun isAvoidanceEntry(fact: String): Boolean {
    return fact == MoodCheckInConstants.AVOIDANCE_FACT
}
