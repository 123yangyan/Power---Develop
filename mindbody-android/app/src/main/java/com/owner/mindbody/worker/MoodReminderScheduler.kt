package com.owner.mindbody.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MoodReminderScheduler {

    private const val WORK_NAME = "mood_reminder_periodic"
    private const val TEST_WORK_NAME = "mood_reminder_test"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<MoodReminderWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    fun scheduleTestReminder(context: Context, delaySeconds: Int) {
        val sec = delaySeconds.coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<MoodReminderTestWorker>()
            .setInitialDelay(sec.toLong(), TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            TEST_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
