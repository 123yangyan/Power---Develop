package com.owner.mindbody.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.DevicePreferences
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.util.AppLogger
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * 夜间自动断开 BLE、晨间自动重连。
 * 链式 OneTimeWork 调度：默认 23:00 断开 → 07:00 重连 → 循环（可在设备设置页修改或关闭）。
 */
class BleSchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MindBodyApplication ?: return Result.failure()
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()

        return try {
            val prefs = app.devicePreferences
            if (!prefs.bleNightlyScheduleEnabled.first()) {
                AppLogger.i(TAG, "Nightly schedule disabled, skip action=$action")
                return Result.success()
            }
            when (action) {
                ACTION_DISCONNECT -> {
                    val wakeHour = prefs.wakeHour.first()
                    val state = app.polarBleManager.connectionState.value
                    if (state == ConnectionState.CONNECTED) {
                        AppLogger.i(TAG, "Scheduled disconnect at bedtime")
                        app.polarBleManager.disconnect()
                    } else {
                        AppLogger.d(TAG, "Scheduled disconnect skipped (state=$state)")
                    }
                    scheduleNext(
                        applicationContext,
                        ACTION_RECONNECT,
                        wakeHour,
                        ExistingWorkPolicy.REPLACE
                    )
                }
                ACTION_RECONNECT -> {
                    val bedtimeHour = prefs.bedtimeHour.first()
                    AppLogger.i(TAG, "Scheduled reconnect at wake time")
                    app.polarBleManager.tryAutoConnectSavedDevice(force = true)
                    scheduleNext(
                        applicationContext,
                        ACTION_DISCONNECT,
                        bedtimeHour,
                        ExistingWorkPolicy.REPLACE
                    )
                }
                else -> {
                    AppLogger.w(TAG, "Unknown action: $action")
                    return Result.failure()
                }
            }
            Result.success()
        } catch (e: Exception) {
            AppLogger.e(TAG, "BleScheduler failed action=$action", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BleSchedulerWorker"
        private const val UNIQUE_WORK_NAME = "ble_nightly_scheduler"

        const val ACTION_DISCONNECT = "disconnect"
        const val ACTION_RECONNECT = "reconnect"

        const val DEFAULT_BEDTIME_HOUR = DevicePreferences.DEFAULT_BEDTIME_HOUR
        const val DEFAULT_WAKE_HOUR = DevicePreferences.DEFAULT_WAKE_HOUR

        private const val KEY_ACTION = "action"

        /**
         * 按用户偏好与当前时刻，排程最近一次的断联或重连任务。
         * 应用冷启动时用 KEEP 避免覆盖已排队的下一次任务；用户改时间或开关时用 REPLACE。
         */
        suspend fun scheduleFromPreferences(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val app = context.applicationContext as? MindBodyApplication ?: return
            val prefs = app.devicePreferences
            if (!prefs.bleNightlyScheduleEnabled.first()) {
                if (policy == ExistingWorkPolicy.REPLACE) {
                    cancel(context)
                }
                return
            }
            val bedtimeHour = prefs.bedtimeHour.first()
            val wakeHour = prefs.wakeHour.first()
            val (action, targetHour) = nextScheduledEvent(bedtimeHour, wakeHour)
            scheduleNext(context, action, targetHour, policy)
        }

        /**
         * 启动或续接调度链。应用启动时用 KEEP 避免覆盖已排队的下一次任务。
         */
        fun scheduleNext(
            context: Context,
            action: String,
            targetHour: Int = hourForAction(action),
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
        ) {
            val delayMs = delayUntilNextHour(targetHour)
            val request = OneTimeWorkRequestBuilder<BleSchedulerWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ACTION, action)
                        .build()
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                policy,
                request
            )
            AppLogger.i(
                TAG,
                "Scheduled $action at hour=$targetHour delayMs=$delayMs policy=$policy"
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        /** 根据当前时刻，返回下一次应执行的 action 与目标整点。 */
        internal fun nextScheduledEvent(
            bedtimeHour: Int,
            wakeHour: Int,
            now: ZonedDateTime = ZonedDateTime.now()
        ): Pair<String, Int> {
            val disconnectDelay = delayUntilNextHour(bedtimeHour, now)
            val reconnectDelay = delayUntilNextHour(wakeHour, now)
            return if (disconnectDelay <= reconnectDelay) {
                ACTION_DISCONNECT to bedtimeHour
            } else {
                ACTION_RECONNECT to wakeHour
            }
        }

        private fun hourForAction(action: String): Int = when (action) {
            ACTION_DISCONNECT -> DEFAULT_BEDTIME_HOUR
            ACTION_RECONNECT -> DEFAULT_WAKE_HOUR
            else -> DEFAULT_BEDTIME_HOUR
        }

        /** 计算到下一目标整点（本地时区）的毫秒延迟。 */
        internal fun delayUntilNextHour(
            hour: Int,
            now: ZonedDateTime = ZonedDateTime.now()
        ): Long {
            var target = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!target.isAfter(now)) {
                target = target.plusDays(1)
            }
            return Duration.between(now, target).toMillis().coerceAtLeast(0L)
        }
    }
}
