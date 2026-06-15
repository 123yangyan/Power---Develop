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

/**
 * 通知栏「稍后」快捷操作：写入逃避记录并取消横幅，无需打开 Activity。
 */
class MoodReminderSnoozeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_SNOOZE) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? MindBodyApplication ?: return@launch
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
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.owner.mindbody.action.MOOD_REMINDER_SNOOZE"
    }
}
