package com.owner.mindbody

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.device.AutoConnectEffect
import com.owner.mindbody.ui.mood.EmotionRoles
import com.owner.mindbody.ui.mood.MobileCheckInDrawer
import com.owner.mindbody.ui.mood.MoodRecordViewModel
import com.owner.mindbody.ui.theme.MindBodyTheme
import com.owner.mindbody.worker.MoodReminderWorker

/**
 * 场景 A：定时探查窗口 —— 锁屏可显示 + FullScreenIntent 拉起 Bottom Sheet。
 */
class MoodCheckInActivity : ComponentActivity() {

    private val softDismiss: Boolean
        get() = intent.getBooleanExtra(EXTRA_SOFT_DISMISS, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableLockScreenDisplay()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationManagerCompat.from(this).cancel(MoodReminderWorker.NOTIFICATION_ID)

        setContent {
            MindBodyTheme {
                AutoConnectEffect()
                CheckInContent(
                    softDismiss = softDismiss,
                    onFinish = { finish() },
                    onOpenJournaling = { openJournalingWithKeyguardDismiss() }
                )
            }
        }
    }

    private fun enableLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun openJournalingWithKeyguardDismiss() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            navigateToJournaling()
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                navigateToJournaling()
            }
        } else {
            navigateToJournaling()
        }
    }

    private fun navigateToJournaling() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val unlocked = keyguardManager?.isKeyguardLocked != true

        if (unlocked && (softDismiss || !isTaskRoot)) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.ROUTE_MOOD_RECORD)
                }
            )
            finish()
            return
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.ROUTE_MOOD_RECORD)
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_OPEN_MOOD_RECORD = "open_mood_record"
        const val EXTRA_SOFT_DISMISS = "soft_dismiss"

        /**
         * 仅允许从前台 Activity context 调试使用。
         * WorkManager / 定时提醒等后台投递必须走 [com.owner.mindbody.worker.MoodReminderDeliver] 通知通道（BAL 合规）。
         */
        fun launch(context: Context, softDismiss: Boolean = false) {
            val intent = Intent(context, MoodCheckInActivity::class.java).apply {
                putExtra(EXTRA_SOFT_DISMISS, softDismiss)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun CheckInContent(
    softDismiss: Boolean,
    onFinish: () -> Unit,
    onOpenJournaling: () -> Unit
) {
    val viewModel: MoodRecordViewModel = viewModel()
    val saving by viewModel.saving.collectAsState()
    val dailyIndexLabel by viewModel.dailyIndexLabel.collectAsState()

    val priorityRoles = remember { EmotionRoles.priorityDock() }
    val overflowRoles = remember(priorityRoles) { EmotionRoles.dockOverflow(priorityRoles) }

    val onSnooze = { viewModel.recordSnooze(onFinish) }
    BackHandler {
        if (softDismiss) onFinish() else onSnooze()
    }

    MobileCheckInDrawer(
        modifier = Modifier.fillMaxSize(),
        dateLabel = viewModel.dateLabel,
        dailyIndexLabel = dailyIndexLabel,
        priorityRoles = priorityRoles,
        overflowRoles = overflowRoles,
        saving = saving,
        onCaptureRole = { role ->
            viewModel.quickCaptureRole(role, onFinish)
        },
        onSnooze = onSnooze,
        onOpenJournaling = onOpenJournaling,
        softDismiss = softDismiss,
        onSoftDismiss = onFinish
    )
}
