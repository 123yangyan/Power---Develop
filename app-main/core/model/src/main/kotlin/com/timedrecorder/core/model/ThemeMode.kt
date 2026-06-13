package com.timedrecorder.core.model

/** T6：主题模式枚举，支持跟随系统 / 浅色 / 深色三选一 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}
