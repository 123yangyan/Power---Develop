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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.worker.MoodReminderDeliver
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.ui.mood.MoodRecordViewModel
import com.owner.mindbody.ui.mood.MoodSettingsSection
import com.owner.mindbody.BuildConfig
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import com.owner.mindbody.ui.components.BleModeRadioGroup
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue

@Composable
fun DeviceScreen(
    onNavigateToFtu: (String) -> Unit,
    onNavigateToDeveloperLog: () -> Unit,
    onNavigateToDeveloperStorage: () -> Unit,
    viewModel: DeviceViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedId by viewModel.connectedDeviceId.collectAsState()
    val scanned by viewModel.scannedDevices.collectAsState()
    val battery by viewModel.batteryLevel.collectAsState()
    val ftuDone by viewModel.ftuDone.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val savedId by viewModel.savedDeviceId.collectAsState()
    val mode by viewModel.connectionMode.collectAsState()
    val developerMode by viewModel.developerModeEnabled.collectAsState()

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
            if (battery != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "硬件电量", style = StatLabel.copy(fontSize = CardTitle.fontSize * 0.85f))
                    Text(
                        text = "$battery%",
                        style = StatValue.copy(color = MindBodyColors.Emerald, fontSize = CardTitle.fontSize)
                    )
                }
            }
            status?.let {
                Text(
                    text = it,
                    style = StatLabel,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
