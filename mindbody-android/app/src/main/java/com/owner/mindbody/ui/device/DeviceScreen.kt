package com.owner.mindbody.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.sync.DeviceSyncStatus
import com.owner.mindbody.worker.MoodReminderDeliver
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.ui.mood.MoodRecordViewModel
import com.owner.mindbody.ui.mood.MoodSettingsSection
import com.owner.mindbody.BuildConfig
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.ui.components.BleModeRadioGroup
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.components.NarrativeCaption
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue
import com.owner.mindbody.util.PowerKeepAlive

@Composable
fun DeviceScreen(
    onNavigateToFtu: (String) -> Unit,
    onNavigateToDeveloperLog: () -> Unit,
    onNavigateToDeveloperStorage: () -> Unit,
    onNavigateToPpiUploadLog: () -> Unit,
    viewModel: DeviceViewModel = viewModel()
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedId by viewModel.connectedDeviceId.collectAsState()
    val scanned by viewModel.scannedDevices.collectAsState()
    val battery by viewModel.batteryLevel.collectAsState()
    val ftuDone by viewModel.ftuDone.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val savedId by viewModel.savedDeviceId.collectAsState()
    val mode by viewModel.connectionMode.collectAsState()
    val developerMode by viewModel.developerModeEnabled.collectAsState()
    val syncUrl by viewModel.syncBaseUrl.collectAsState()
    val syncKey by viewModel.syncApiKey.collectAsState()
    val syncOn by viewModel.syncEnabled.collectAsState()
    val syncLast by viewModel.lastSyncResult.collectAsState()
    val ntfyTopic by viewModel.ntfyTopic.collectAsState()
    val deviceSyncStatus by viewModel.deviceSyncStatus.collectAsState()
    val deviceSyncError by viewModel.deviceSyncError.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(
            eyebrow = "POLAR HARDWARE",
            title = "设备设置"
        )

        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MindBodyShapes.StatCell)
                            .background(
                                BrushLinearIndigo()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MindBodyColors.PrimaryIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(text = "Polar Loop 手环", style = StatValue)
                        Text(
                            text = deviceSubtitle(connectionState, connectedId ?: savedId),
                            style = StatLabel,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                StatusBadge(
                    text = if (connectionState == ConnectionState.CONNECTED) "ACTIVE" else "IDLE",
                    active = connectionState == ConnectionState.CONNECTED
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "硬件电量", style = StatLabel.copy(fontSize = CardTitle.fontSize * 0.85f))
                val level = battery // 局部变量避免 delegated property smart-cast 问题
                val batteryText = if (level != null) "$level%" else "—"
                val batteryColor = when {
                    level == null -> MindBodyColors.OnBackgroundSecondary
                    level <= 20 -> MindBodyColors.HeartRed
                    else -> MindBodyColors.Emerald
                }
                Text(
                    text = batteryText,
                    style = StatValue.copy(color = batteryColor, fontSize = CardTitle.fontSize)
                )
            }
            status?.let {
                Text(
                    text = it,
                    style = StatLabel,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (connectionState == ConnectionState.CONNECTED) {
                DeviceSyncStatusRow(
                    syncStatus = deviceSyncStatus,
                    errorMsg = deviceSyncError,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // ── 实时生理分析推流状态卡（仅 BLE 已连接时显示）────────────────────
        if (connectionState == ConnectionState.CONNECTED) {
            StreamAnalysisCard()
        }

        PremiumCard {
            Text(text = "BLE 采集频率策略", style = CardTitle)
            BleModeRadioGroup(
                selectedMode = mode,
                onModeSelected = viewModel::setConnectionMode,
                modifier = Modifier.padding(top = 14.dp)
            )
        }

        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "健康档案 (FTU)", style = CardTitle)
                if (connectionState == ConnectionState.CONNECTED && connectedId != null && !ftuDone) {
                    Text(
                        text = "去配置",
                        style = StatLabel.copy(color = MindBodyColors.PrimaryIndigo),
                        modifier = Modifier.clickable { onNavigateToFtu(connectedId!!) }
                    )
                }
            }
            FtuProfileGrid(ftuDone = ftuDone)
        }

        PremiumCard(contentPadding = 16.dp) {
            Text(text = "连接操作", style = CardTitle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.scan() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MindBodyColors.PrimaryIndigo
                    ),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("扫描", modifier = Modifier.padding(start = 4.dp))
                }
                if (!savedId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { viewModel.connectSaved() },
                        modifier = Modifier.weight(1f),
                        shape = MindBodyShapes.RadioOption
                    ) {
                        Text("连接已保存")
                    }
                }
            }
            if (connectionState == ConnectionState.CONNECTED && connectedId != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!ftuDone) {
                        Button(
                            onClick = { onNavigateToFtu(connectedId!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.HeartRed),
                            shape = MindBodyShapes.RadioOption
                        ) {
                            Text("完成首次配置")
                        }
                    }
                    OutlinedButton(onClick = { viewModel.disconnect() }, shape = MindBodyShapes.RadioOption) {
                        Text("断开")
                    }
                    OutlinedButton(onClick = { viewModel.refreshFtuStatus() }, shape = MindBodyShapes.RadioOption) {
                        Text("刷新")
                    }
                }
            }
        }

        BackgroundKeepAliveCard()

        PairingTipsCard()

        DeviceMoodReminderSection()

        Text(text = "扫描结果", style = CardTitle)
        scanned.forEach { device ->
            DeviceItem(device = device, onConnect = { viewModel.connect(device.deviceId) })
        }

        if (developerMode) {
            PremiumCard(contentPadding = 16.dp) {
                Text(text = "开发者选项", style = CardTitle)
                Text(
                    text = "查看 BLE 运行日志，或检查 Room 各表是否已成功落库。",
                    style = StatLabel,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToDeveloperLog,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                        shape = MindBodyShapes.RadioOption
                    ) {
                        Text("运行日志")
                    }
                    Button(
                        onClick = onNavigateToDeveloperStorage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.Emerald),
                        shape = MindBodyShapes.RadioOption
                    ) {
                        Text("storage 看板")
                    }
                }
                Button(
                    onClick = onNavigateToPpiUploadLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.Amber),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("PPI 上传日志")
                }
                // 云端同步设置
                Text(
                    text = "云端同步",
                    style = CardTitle,
                    modifier = Modifier.padding(top = 16.dp)
                )
                OutlinedTextField(
                    value = syncUrl,
                    onValueChange = { viewModel.setSyncBaseUrl(it) },
                    label = { Text("Server URL", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(top = 4.dp),
                    textStyle = StatLabel.copy(fontSize = 12.sp)
                )
                OutlinedTextField(
                    value = syncKey,
                    onValueChange = { viewModel.setSyncApiKey(it) },
                    label = { Text("API Key", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(top = 4.dp),
                    textStyle = StatLabel.copy(fontSize = 12.sp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自动同步 (每 2h)", style = StatLabel)
                    Switch(
                        checked = syncOn,
                        onCheckedChange = { viewModel.setSyncEnabled(it) }
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.triggerSyncNow() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.Emerald),
                        shape = MindBodyShapes.RadioOption
                    ) {
                        Text("立即同步")
                    }
                }
                if (syncLast.isNotBlank()) {
                    Text(
                        text = syncLast,
                        style = StatLabel,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (ntfyTopic.isNotBlank()) {
                    Text(
                        text = "ntfy 推送 Topic",
                        style = CardTitle,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ntfyTopic,
                            style = StatLabel.copy(fontSize = 12.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.copyNtfyTopicToClipboard(context) }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制 ntfy topic",
                                tint = MindBodyColors.PrimaryIndigo
                            )
                        }
                    }
                    Text(
                        text = "在 ntfy App（F-Droid 版 WebSocket 模式）中订阅此 Topic",
                        style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Text(
            text = "MindBody v${BuildConfig.VERSION_NAME} · Polar SDK ${viewModel.sdkVersion}",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp)
                .clickable { viewModel.onVersionAreaTap() }
        )
    }
}

@Composable
private fun DeviceMoodReminderSection(
    viewModel: MoodRecordViewModel = viewModel()
) {
    val reminderInterval by viewModel.reminderIntervalMinutes.collectAsState()
    val quietStart by viewModel.quietStart.collectAsState()
    val quietEnd by viewModel.quietEnd.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val strongPopup by viewModel.strongPopup.collectAsState()
    val todayCount by viewModel.todayEntryCount.collectAsState()

    var showSettings by remember { mutableStateOf(true) }
    var intervalInput by remember(reminderInterval) { mutableStateOf(reminderInterval.toString()) }
    var quietStartInput by remember(quietStart) { mutableStateOf(quietStart) }
    var quietEndInput by remember(quietEnd) { mutableStateOf(quietEnd) }

    val context = LocalContext.current
    val showFsiHint = remember {
        !MoodReminderDeliver.canUseFullScreenIntent(context)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MoodSettingsSection(
            showSettings = showSettings,
            onShowSettingsChange = { showSettings = it },
            notificationsEnabled = notificationsEnabled,
            onNotificationsChange = viewModel::setNotificationsEnabled,
            strongPopup = strongPopup,
            onStrongPopupChange = viewModel::setStrongPopup,
            intervalInput = intervalInput,
            onIntervalInputChange = { intervalInput = it },
            onApplyInterval = {
                viewModel.setReminderInterval(
                    intervalInput.toIntOrNull() ?: MoodPreferences.DEFAULT_REMINDER_INTERVAL_MINUTES
                )
            },
            quietStartInput = quietStartInput,
            onQuietStartChange = { quietStartInput = it },
            quietEndInput = quietEndInput,
            onQuietEndChange = { quietEndInput = it },
            onApplyQuietHours = { viewModel.setQuietHours(quietStartInput, quietEndInput) },
            todayEntryCount = todayCount,
            onTestReminder30s = { viewModel.scheduleTestReminder(30) },
            onTestReminder60s = { viewModel.scheduleTestReminder(60) }
        )
        if (showFsiHint) {
            Text(
                text = MoodReminderDeliver.FULL_SCREEN_INTENT_HINT,
                fontSize = 12.sp,
                color = MindBodyColors.OnBackgroundSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun FtuProfileGrid(ftuDone: Boolean) {
    Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FtuCell(label = "性别 & 身高", value = if (ftuDone) "已写入手环" else "待配置")
            FtuCell(label = "体重 & 年龄", value = if (ftuDone) "已写入手环" else "待配置")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FtuCell(
                label = "静息率基线",
                value = if (ftuDone) "已配置" else "--",
                valueColor = if (ftuDone) MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary
            )
            FtuCell(label = "本地机制", value = "Room Clean (24h)")
        }
    }
}

@Composable
private fun RowScope.FtuCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MindBodyColors.OnBackground
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(MindBodyShapes.StatCell)
            .background(MindBodyColors.StatCellBg)
            .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.08f), MindBodyShapes.StatCell)
            .padding(12.dp)
    ) {
        Text(text = label.uppercase(), style = StatLabel)
        Text(
            text = value,
            style = StatValue.copy(fontSize = CardTitle.fontSize, color = valueColor),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(MindBodyShapes.Badge)
            .background(
                if (active) MindBodyColors.EmeraldSurface
                else MindBodyColors.StatCellBg
            )
            .border(
                1.dp,
                if (active) MindBodyColors.Emerald.copy(alpha = 0.25f)
                else MindBodyColors.StatCellBorder,
                MindBodyShapes.Badge
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = StatLabel.copy(
                color = if (active) MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary
            )
        )
    }
}

@Composable
private fun DeviceItem(device: ScannedDevice, onConnect: () -> Unit) {
    PremiumCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onConnect),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(device.name, style = StatValue)
                Text(device.deviceId, style = StatLabel, modifier = Modifier.padding(top = 2.dp))
            }
            Text("RSSI ${device.rssi}", style = StatLabel.copy(color = MindBodyColors.PrimaryIndigo))
        }
    }
}

@Composable
private fun BackgroundKeepAliveCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryExempt by remember { mutableStateOf(PowerKeepAlive.isIgnoringBatteryOptimizations(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryExempt = PowerKeepAlive.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    PremiumCard(contentPadding = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "后台保活", style = CardTitle)
            StatusBadge(
                text = if (batteryExempt) "已豁免" else "未豁免",
                active = batteryExempt
            )
        }
        Text(
            text = "息屏后需保持 BLE 连接与 PPI 推流。小米 HyperOS 请完成以下设置：",
            style = StatLabel,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "开启本应用「自启动」",
                "省电策略设为「无限制」",
                "多任务界面锁定本应用卡片",
                "开启「锁屏显示」与「后台弹出界面」权限"
            ).forEach { tip ->
                Text("• $tip", style = StatLabel)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!batteryExempt) {
                Button(
                    onClick = { PowerKeepAlive.requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("设为无限制")
                }
            }
            OutlinedButton(
                onClick = { PowerKeepAlive.openAutoStartSettings(context) },
                modifier = Modifier.weight(1f),
                shape = MindBodyShapes.RadioOption
            ) {
                Text("打开自启动")
            }
        }
    }
}

@Composable
private fun PairingTipsCard() {
    PremiumCard(contentPadding = 16.dp) {
        Text(text = "配对提示", style = CardTitle)
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "手机与手环距离请在 1 米以内",
                "Loop 只绑定一台手机，换机需恢复出厂设置",
                "首次使用必须完成 FTU 配置才能正常工作",
                "手环 LED 闪烁「搜索」表示等待首次配置"
            ).forEach { tip ->
                Text("• $tip", style = StatLabel)
            }
        }
    }
}

@Composable
private fun BrushLinearIndigo(): androidx.compose.ui.graphics.Brush {
    return androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            MindBodyColors.HeroGradientStart,
            MindBodyColors.HeroGradientMid
        )
    )
}

private fun deviceSubtitle(state: ConnectionState, deviceId: String?): String {
    val id = deviceId ?: "未绑定"
    return when (state) {
        ConnectionState.CONNECTED -> "ID: $id • CONNECTED"
        ConnectionState.CONNECTING -> "ID: $id • CONNECTING"
        else -> "ID: $id • DISCONNECTED"
    }
}

@Composable
fun DeviceSyncStatusRow(
    syncStatus: DeviceSyncStatus,
    errorMsg: String?,
    modifier: Modifier = Modifier
) {
    val (icon, tint, label) = when (syncStatus) {
        DeviceSyncStatus.SYNCING -> Triple(
            Icons.Default.Sync,
            MindBodyColors.PrimaryIndigo,
            "正在拉取设备数据…"
        )
        DeviceSyncStatus.SUCCESS -> Triple(
            Icons.Default.CheckCircle,
            MindBodyColors.Emerald,
            "设备数据已同步"
        )
        DeviceSyncStatus.FAILED -> Triple(
            Icons.Default.Warning,
            MindBodyColors.HeartRed,
            errorMsg?.take(60) ?: "设备同步失败"
        )
        DeviceSyncStatus.IDLE -> Triple(
            Icons.Default.History,
            MindBodyColors.OnBackgroundSecondary,
            "等待同步"
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = StatLabel.copy(color = tint)
        )
    }
}

// ── 实时生理分析推流状态卡 ─────────────────────────────────────────────────────

@Composable
private fun StreamAnalysisCard() {
    val context = LocalContext.current
    val storage = (context.applicationContext as MindBodyApplication).storage
    val physioState by storage.latestPhysioState.collectAsState(initial = null)

    val stateLabel = physioState?.stateLabel ?: "baseline_building"
    val accentColor = when (stateLabel) {
        "calm" -> MindBodyColors.CalmTeal
        "normal" -> MindBodyColors.PrimaryIndigo
        "elevated" -> MindBodyColors.StressAmber
        "anxious" -> MindBodyColors.AnxietyRose
        "high_anxiety" -> MindBodyColors.HighAlertRed
        else -> MindBodyColors.OceanBlue
    }
    val zhLabel = when (stateLabel) {
        "calm" -> "平静"
        "normal" -> "正常"
        "elevated" -> "应激升高"
        "anxious" -> "疑似焦虑"
        "high_anxiety" -> "高度焦虑"
        else -> "正在建立基线"
    }

    val windowCount = physioState?.baselineWindowCount ?: 0
    val lastStreamTs = physioState?.lastStreamTs

    NarrativeCard(
        accentColor = accentColor,
        badgeLabel = "实时生理分析"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NarrativeCaption(text = "当前状态")
            Text(
                text = zhLabel,
                style = StatLabel.copy(color = accentColor, fontSize = 12.sp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NarrativeCaption(text = "基线窗口")
            Text(
                text = "$windowCount / 50",
                style = StatLabel.copy(
                    color = MindBodyColors.OnBackground,
                    fontSize = 12.sp
                )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NarrativeCaption(text = "最近推流")
            Text(
                text = lastStreamTs?.let { formatStreamTime(it) } ?: "等待首次推流",
                style = StatLabel.copy(
                    color = if (lastStreamTs != null) MindBodyColors.Emerald
                    else MindBodyColors.OnBackgroundSecondary,
                    fontSize = 12.sp
                )
            )
        }
    }
}

private fun formatStreamTime(tsMs: Long): String {
    val diffMs = System.currentTimeMillis() - tsMs
    return when {
        diffMs < 60_000L -> "刚刚"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} 分钟前"
        else -> "${diffMs / 3_600_000L} 小时前"
    }
}
