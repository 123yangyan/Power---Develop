package com.owner.mindbody.util

import android.app.KeyguardManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * 判断 App 是否处于前台且已解锁，用于探查弹窗 soft dismiss 分流。
 */
object AppForegroundHelper {

    fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    fun isKeyguardLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        return keyguardManager?.isKeyguardLocked == true
    }

    /** 前台且已解锁：允许软关闭，不写逃避记录 */
    fun canSoftDismissProbe(context: Context): Boolean {
        return isAppInForeground() && !isKeyguardLocked(context)
    }
}
