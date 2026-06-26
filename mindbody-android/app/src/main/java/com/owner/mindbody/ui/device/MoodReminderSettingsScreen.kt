package com.owner.mindbody.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.mood.MoodRecordViewModel
import com.owner.mindbody.worker.MoodReminderDeliver

@Composable
fun MoodReminderSettingsScreen(
    onBack: () -> Unit,
    viewModel: MoodRecordViewModel = viewModel(),
) {
    val reminderInterval by viewModel.reminderIntervalMinutes.collectAsState()
    val quietStart by viewModel.quietStart.collectAsState()
    val quietEnd by viewModel.quietEnd.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val strongPopup by viewModel.strongPopup.collectAsState()
    val todayCount by viewModel.todayEntryCount.collectAsState()

    var intervalInput by remember(reminderInterval) { mutableStateOf(reminderInterval.toString()) }
    var quietStartInput by remember(quietStart) { mutableStateOf(quietStart) }
    var quietEndInput by remember(quietEnd) { mutableStateOf(quietEnd) }

    val context = LocalContext.current
    val showFsiHint = remember {
        !MoodReminderDeliver.canUseFullScreenIntent(context)
    }

    SettingsSubScreenScaffold(title = "心情提醒", onBack = onBack) {
        PremiumCard(contentPadding = 0.dp) {
            SettingsRow(
                title = "启用通知",
                subtitle = "今日已记录 $todayCount 条",
                rightContent = {
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = viewModel::setNotificationsEnabled,
                    )
                },
            )
            SettingsDivider()
            SettingsRow(
                title = "强弹窗",
                subtitle = "锁屏全屏探查 + 前台 Bottom Sheet",
                rightContent = {
                    Switch(
                        checked = strongPopup,
                        onCheckedChange = viewModel::setStrongPopup,
                    )
                },
            )
        }

        SettingsSectionLabel("提醒间隔")
        PremiumCard {
            OutlinedTextField(
                value = intervalInput,
                onValueChange = { intervalInput = it.filter { c -> c.isDigit() } },
                label = { Text("间隔（分钟，1–1440）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    viewModel.setReminderInterval(
                        intervalInput.toIntOrNull()
                            ?: MoodPreferences.DEFAULT_REMINDER_INTERVAL_MINUTES,
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("应用间隔")
            }
        }

        SettingsSectionLabel("静默时段")
        PremiumCard {
            OutlinedTextField(
                value = quietStartInput,
                onValueChange = { quietStartInput = it },
                label = { Text("静默开始（HH:mm）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = quietEndInput,
                onValueChange = { quietEndInput = it },
                label = { Text("静默结束（HH:mm）") },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = { viewModel.setQuietHours(quietStartInput, quietEndInput) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("应用静默时段")
            }
        }

        SettingsSectionLabel("测试")
        PremiumCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { viewModel.scheduleTestReminder(30) }) {
                    Text("30 秒后提醒")
                }
                TextButton(onClick = { viewModel.scheduleTestReminder(60) }) {
                    Text("60 秒后提醒")
                }
            }
        }

        if (showFsiHint) {
            Text(
                text = MoodReminderDeliver.FULL_SCREEN_INTENT_HINT,
                fontSize = 12.sp,
                color = MindBodyColors.OnBackgroundSecondary,
            )
        }
    }
}
