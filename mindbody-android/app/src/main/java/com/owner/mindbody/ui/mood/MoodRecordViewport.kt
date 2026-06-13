package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

enum class RecordViewportVariant {
    PAGE, POPUP, MODAL
}

/**
 * 记录页 / 弹窗共用布局，对齐 emotion RecordViewportForm.tsx。
 * 手机端：上坐标、下日记（保留 v3.7 信息架构）。
 */
@Composable
fun MoodRecordViewport(
    variant: RecordViewportVariant,
    dateLabel: String,
    dailyIndexLabel: String?,
    lastRecordLabel: String,
    coordX: Int,
    coordY: Int,
    hasCoordSelection: Boolean,
    onPickCoord: (Int, Int) -> Unit,
    diaryText: String,
    onDiaryChange: (String) -> Unit,
    error: String?,
    saving: Boolean,
    saveSuccess: Boolean,
    isEdit: Boolean = false,
    onSave: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPopup = variant == RecordViewportVariant.POPUP
    val saveLabel = when {
        saving -> "保存中…"
        saveSuccess -> "已收纳"
        isEdit -> "保存修改"
        else -> "保存记录"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isPopup) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isEdit) {
            Text(
                text = dateLabel,
                style = CardTitle,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            dailyIndexLabel?.let {
                Text(it, fontSize = 13.sp, color = MindBodyColors.PrimaryIndigo)
            }
            Text(
                text = lastRecordLabel,
                fontSize = 12.sp,
                color = MindBodyColors.OnBackgroundSecondary
            )
        }

        PremiumCard(contentPadding = 14.dp) {
            Text("点选任务价值与耗能坐标", style = CardTitle, modifier = Modifier.padding(bottom = 8.dp))
            ValueEnergyGrid(
                coordX = coordX,
                coordY = coordY,
                hasSelection = hasCoordSelection,
                onPick = onPickCoord
            )
        }

        PremiumCard(contentPadding = 14.dp) {
            Text("日记", style = CardTitle, modifier = Modifier.padding(bottom = 8.dp))
            DiaryInput(
                value = diaryText,
                onValueChange = onDiaryChange,
                placeholder = "写下此刻在做什么、感受如何…",
                minLines = if (isPopup) 4 else 5,
                scrollable = !isPopup,
                autoFocus = variant == RecordViewportVariant.PAGE && !isEdit
            )
        }

        if (error != null) {
            Text(error, color = MindBodyColors.HeartRed, fontSize = 14.sp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("稍后")
                }
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier.weight(if (onCancel != null) 1f else 1f),
                colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo)
            ) {
                Text(saveLabel)
            }
        }

        if (isPopup) {
            Text(
                text = "Esc / 稍后：记录逃避并 20 分钟后再次提醒",
                fontSize = 11.sp,
                color = MindBodyColors.OnBackgroundSecondary
            )
        } else if (!isEdit) {
            Text(
                text = "关联心率标注为「估计关联」，非医疗诊断",
                fontSize = 12.sp,
                color = MindBodyColors.OnBackgroundSecondary
            )
        }
    }
}

@Composable
fun MoodSettingsSection(
    showSettings: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    strongPopup: Boolean,
    onStrongPopupChange: (Boolean) -> Unit,
    intervalInput: String,
    onIntervalInputChange: (String) -> Unit,
    onApplyInterval: () -> Unit,
    quietStartInput: String,
    onQuietStartChange: (String) -> Unit,
    quietEndInput: String,
    onQuietEndChange: (String) -> Unit,
    onApplyQuietHours: () -> Unit,
    todayEntryCount: Int,
    onTestReminder30s: () -> Unit,
    onTestReminder60s: () -> Unit,
    modifier: Modifier = Modifier
) {
    PremiumCard(contentPadding = 16.dp, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("提醒设置", style = CardTitle)
            Switch(checked = showSettings, onCheckedChange = onShowSettingsChange)
        }
        if (showSettings) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("今日已记录 $todayEntryCount 条", fontSize = 13.sp, color = MindBodyColors.OnBackgroundSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSwitchRow("启用通知", notificationsEnabled, onNotificationsChange)
            SettingsSwitchRow("强弹窗（全屏记录）", strongPopup, onStrongPopupChange)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = intervalInput,
                onValueChange = { onIntervalInputChange(it.filter { c -> c.isDigit() }) },
                label = { Text("提醒间隔（分钟，1–1440）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = onApplyInterval, modifier = Modifier.padding(top = 8.dp)) {
                Text("应用间隔")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = quietStartInput,
                onValueChange = onQuietStartChange,
                label = { Text("静默开始（HH:mm）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = quietEndInput,
                onValueChange = onQuietEndChange,
                label = { Text("静默结束（HH:mm）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = onApplyQuietHours, modifier = Modifier.padding(top = 8.dp)) {
                Text("应用静默时段")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onTestReminder30s) { Text("测试提醒 30s") }
                TextButton(onClick = onTestReminder60s) { Text("测试提醒 60s") }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
