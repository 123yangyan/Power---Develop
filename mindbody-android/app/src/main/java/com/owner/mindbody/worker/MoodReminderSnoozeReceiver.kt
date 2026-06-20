package com.owner.mindbody.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.ui.mood.MoodCheckInConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 通知栏「稍后」快捷操作：写入逃避记录并取消横幅，无需打开 Activity。
 * 异步操作有 10 秒超时保护，防止 BroadcastReceiver 被系统强制回收前未能完成。
 */
class MoodReminderSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SNOOZE) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(10_000L) {
                    val app = context.applicationContext as? MindBodyApplication ?: return@withTimeout
                    val moodPrefs = app.moodPreferences
                    app.storage.mood.insert(
                        fact = MoodCheckInConstants.AVOIDANCE_FACT,
                        coordX = 0,
                        coordY = 0,
                        occurredAt = System.currentTimeMillis(),
                        hrAtEntry = null
                    )
                    moodPrefs.incrementSnoozeCount(moodPrefs.todayDateKey())
                    moodPrefs.setLastReminderAt(System.currentTimeMillis())
                    MoodReminderScheduler.scheduleNextExact(context, moodPrefs.getEffectiveIntervalMs())
                    NotificationManagerCompat.from(context).cancel(MoodReminderWorker.NOTIFICATION_ID)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.owner.mindbody.action.MOOD_REMINDER_SNOOZE"
    }
}
