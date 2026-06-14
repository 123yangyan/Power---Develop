package com.owner.mindbody.ui.mood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

enum class RecordViewportVariant {
    PAGE, POPUP, MODAL
}

/**
 * 记录页 / 弹窗共用布局。
 * PAGE：场景 A（ActorStage 极速指认）/ 场景 B（键盘沉浸倾诉）二分法。
 * MODAL：历史编辑弹窗 —— 紧凑角色网格。
 */
@Composable
fun MoodRecordViewport(
    variant: RecordViewportVariant,
    dateLabel: String,
    dailyIndexLabel: String?,
    lastRecordLabel: String,
    selectedRoleId: String?,
    onSelectRole: (EmotionRole) -> Unit,
    diaryText: String,
    onDiaryChange: (String) -> Unit,
    hasCoordSelection: Boolean,
    error: String?,
    saving: Boolean,
    saveSuccess: Boolean,
    isEdit: Boolean = false,
    onSave: () -> Unit,
    onCancel: (() -> Unit)? = null,
    onStageRoleTap: ((EmotionRole) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isPopup = variant == RecordViewportVariant.POPUP
    val isPage = variant == RecordViewportVariant.PAGE
    val saveLabel = when {
        saving -> "保存中…"
        saveSuccess -> "已收纳"
        isEdit -> "保存修改"
        else -> "保存记录"
    }

    if (isPage && !isEdit) {
        val diaryFocusRequester = remember { FocusRequester() }
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        val keyboardVisible = imeBottom > 0

        var isWritingMode by remember { mutableStateOf(false) }
        var showPickerSheet by remember { mutableStateOf(false) }
        var refocusDiaryAfterPicker by remember { mutableStateOf(false) }

        val stageRoles = remember { EmotionRoles.recordPageStageLineup() }
        val selectedRole = remember(selectedRoleId) { EmotionRoles.findById(selectedRoleId) }
        val sceneB = isWritingMode || keyboardVisible

        LaunchedEffect(isWritingMode) {
            if (isWritingMode) {
                diaryFocusRequester.requestFocusAfterLayout()
            }
        }

        LaunchedEffect(refocusDiaryAfterPicker) {
            if (refocusDiaryAfterPicker) {
                diaryFocusRequester.requestFocusAfterLayout()
                refocusDiaryAfterPicker = false
            }
        }

        LaunchedEffect(saveSuccess) {
            if (saveSuccess) {
                isWritingMode = false
            }
        }

        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!sceneB) {
                Text(text = dateLabel, style = CardTitle)
                dailyIndexLabel?.let {
                    Text(it, fontSize = 13.sp, color = MindBodyColors.PrimaryIndigo)
                }

                ActorStage(
                    roles = stageRoles,
                    selectedRoleId = selectedRoleId,
                    onSelectRole = { role ->
                        onStageRoleTap?.invoke(role) ?: onSelectRole(role)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = { showPickerSheet = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "展开查看全部 ${EmotionRoles.all.size} 位情绪角色",
                        fontSize = 13.sp,
                        color = MindBodyColors.OnBackgroundSecondary
                    )
                }

                PremiumCard(
                    contentPadding = 16.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isWritingMode = true }
                ) {
                    Text(
                        text = "✍️ 想倾诉更多？点击这里写下日记…",
                        fontSize = 14.sp,
                        color = MindBodyColors.OnBackgroundSecondary
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = dateLabel, style = CardTitle.copy(fontSize = 15.sp))
                    dailyIndexLabel?.let {
                        Text(it, fontSize = 12.sp, color = MindBodyColors.PrimaryIndigo)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 120.dp)
                    ) {
                        DiaryInput(
                            value = diaryText,
                            onValueChange = onDiaryChange,
                            placeholder = "写下此刻在做什么、感受如何…",
                            minLines = 6,
                            scrollable = false,
                            fillHeight = true,
                            autoFocus = false,
                            focusRequester = diaryFocusRequester,
                            selectedRole = selectedRole,
                            onFocusChanged = { focused -> if (focused) isWritingMode = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    EmotionCapsuleToolbar(
                        roles = stageRoles,
                        selectedRoleId = selectedRoleId,
                        onSelectRole = onSelectRole,
                        showExpandAffordance = true,
                        onExpandClick = { showPickerSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (error != null) {
                        Text(error, color = MindBodyColors.HeartRed, fontSize = 14.sp)
                    }

                    Button(
                        onClick = onSave,
                        enabled = !saving && hasCoordSelection,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo)
                    ) {
                        Text(
                            when {
                                saving -> "保存中…"
                                saveSuccess -> "已收纳"
                                !hasCoordSelection -> "请先选择角色"
                                else -> saveLabel
                            }
                        )
                    }

                    if (!keyboardVisible) {
                        Text(
                            text = "关联心率标注为「估计关联」，非医疗诊断",
                            fontSize = 11.sp,
                            color = MindBodyColors.OnBackgroundSecondary
                        )
                    }
                }
            }
        }

        EmotionRolePickerSheet(
            visible = showPickerSheet,
            selectedRoleId = selectedRoleId,
            onSelectRole = { role ->
                onSelectRole(role)
                if (isWritingMode || keyboardVisible) {
                    refocusDiaryAfterPicker = true
                }
            },
            onDismiss = { showPickerSheet = false }
        )

        return
    }

    // MODAL / POPUP / 编辑模式：可滚动紧凑布局
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isPopup) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isEdit) {
            Text(text = dateLabel, style = CardTitle, modifier = Modifier.padding(bottom = 2.dp))
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
            KeyboardAwareRoleSelector(
                selectedRoleId = selectedRoleId,
                onSelectRole = onSelectRole,
                layout = RoleSelectorLayout.EditModal
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
                autoFocus = false,
                selectedRole = EmotionRoles.findById(selectedRoleId)
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
                enabled = !saving && hasCoordSelection,
                modifier = Modifier.weight(1f),
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("今日已记录 $todayEntryCount 条", fontSize = 13.sp, color = MindBodyColors.OnBackgroundSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSwitchRow("启用通知", notificationsEnabled, onNotificationsChange)
                SettingsSwitchRow("强弹窗（锁屏全屏探查 + 前台 Bottom Sheet）", strongPopup, onStrongPopupChange)
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
