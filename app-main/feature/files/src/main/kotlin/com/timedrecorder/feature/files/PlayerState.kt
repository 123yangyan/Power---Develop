package com.timedrecorder.feature.files

/**
 * T3：本地音频播放器状态机。
 * 状态流转：Idle → Preparing → Playing ⇄ Paused → Completed
 *                                              ↓
 *                                            Error
 */
sealed class PlayerState {
    /** 初始状态，未加载任何文件 */
    data object Idle : PlayerState()

    /** 正在准备（prepareAsync 中，不可操作） */
    data object Preparing : PlayerState()

    /** 播放中 */
    data object Playing : PlayerState()

    /** 已暂停 */
    data object Paused : PlayerState()

    /** 播放完毕 */
    data object Completed : PlayerState()

    /** 出错 */
    data class Error(val message: String) : PlayerState()
}
