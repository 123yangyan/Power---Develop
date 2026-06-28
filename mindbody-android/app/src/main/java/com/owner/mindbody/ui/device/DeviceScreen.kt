package com.owner.mindbody.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.BuildConfig
import com.owner.mindbody.data.sync.DeviceSyncStatus
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.polar.ScannedDevice
import com.owner.mindbody.ui.components.BatteryDisplayStyle
import com.owner.mindbody.ui.components.BatteryLevelDisplay
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.mood.MoodRecordViewModel
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
    onNavigateToPpiUploadLog: () -> Unit,
    onNavigateToBleCollection: () -> Unit,
    onNavigateToMoodReminder: () -> Unit,
    onNavigateToKeepAlive: () -> Unit,
    viewModel: DeviceViewModel = viewModel(),
    moodViewModel: MoodRecordViewModel = viewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val connectedId by viewModel.connectedDeviceId.collectAsState()
    val scanned by viewModel.scannedDevices.collectAsState()
    val battery by viewModel.batteryLevel.collectAsState()
    val batteryUpdatedAt by viewModel.batteryLevelTimestamp.collectAsState()
    val chargeState by viewModel.chargeState.collectAsState()
    val ftuDone by viewModel.ftuDone.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val savedId by viewModel.savedDeviceId.collectAsState()
    val mode by viewModel.connectionMode.collectAsState()
    val bedtimeHour by viewModel.bedtimeHour.collectAsState()
    val wakeHour by viewModel.wakeHour.collectAsState()
    val bleNightlyScheduleEnabled by viewModel.bleNightlyScheduleEnabled.collectAsState()
    val developerMode by viewModel.developerModeEnabled.collectAsState()
    val syncUrl by viewModel.syncBaseUrl.collectAsState()
    val syncKey by viewModel.syncApiKey.collectAsState()
    val syncOn by viewModel.syncEnabled.collectAsState()
    val syncLast by viewModel.lastSyncResult.collectAsState()
    val ntfyTopic by viewModel.ntfyTopic.collectAsState()
    val deviceSyncStatus by viewModel.deviceSyncStatus.collectAsState()
    val deviceSyncError by viewModel.deviceSyncError.collectAsState()
    val keepAliveStatus by viewModel.keepAliveStatus.collectAsState()

    val notificationsEnabled by moodViewModel.notificationsEnabled.collectAsState()
    val reminderInterval by moodViewModel.reminderIntervalMinutes.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(
            eyebrow = "APP SETTINGS",
            title = "设置"
        )

        SettingsSectionLabel("POLAR 设备")
        PremiumCard(contentPadding = 0.dp) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Polar Loop 手环", style = StatValue)
                        Text(
                            text = deviceSubtitle(connectionState, connectedId ?: savedId),
                            style = StatLabel,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    StatusBadge(
                        text = if (connectionState == ConnectionState.CONNECTED) "已连接" else "未连接",
                        active = connectionState == ConnectionState.CONNECTED
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "电量", style = StatLabel)
                    BatteryLevelDisplay(
                        level = battery,
                        updatedAtMs = batteryUpdatedAt,
                        chargeState = chargeState,
                        style = BatteryDisplayStyle.Settings,
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
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        SettingsSectionLabel("连接")
        PremiumCard(contentPadding = 0.dp) {
            SettingsActionRow(
                title = "扫描设备",
                subtitle = "搜索附近 Polar 手环",
                leadingIcon = Icons.Default.Refresh,
                onClick = { viewModel.scan() }
            )
            if (!savedId.isNullOrBlank()) {
                SettingsDivider()
                SettingsActionRow(
                    title = "连接已保存",
                    subtitle = savedId,
                    onClick = { viewModel.connectSaved() }
                )
            }
            if (connectionState == ConnectionState.CONNECTED && connectedId != null) {
                SettingsDivider()
                SettingsActionRow(
                    title = "断开连接",
                    subtitle = "当前已连接 $connectedId",
                    onClick = { viewModel.disconnect() }
                )
            }
            SettingsDivider()
            SettingsActionRow(
                title = "健康档案 (FTU)",
                subtitle = if (ftuDone) "已完成首次配置" else "待配置性别、身高、体重等",
                onClick = {
                    val id = connectedId ?: savedId
                    if (id != null) onNavigateToFtu(id)
                },
                enabled = connectedId != null || savedId != null
            )
        }

        SettingsSectionLabel("偏好")
        PremiumCard(contentPadding = 0.dp) {
            SettingsActionRow(
                title = "采集策略",
                subtitle = bleCollectionSummary(
                    mode = mode,
                    nightlyEnabled = bleNightlyScheduleEnabled,
                    bedtimeHour = bedtimeHour,
                    wakeHour = wakeHour,
                ),
                onClick = onNavigateToBleCollection,
            )
            SettingsDivider()
            SettingsActionRow(
                title = "心情提醒",
                subtitle = moodReminderSummary(notificationsEnabled, reminderInterval),
                onClick = onNavigateToMoodReminder,
            )
            SettingsDivider()
            SettingsActionRow(
                title = "后台保活",
                subtitle = keepAliveSummary(keepAliveStatus.batteryExempt),
                onClick = onNavigateToKeepAlive,
            )
        }

        if (scanned.isNotEmpty()) {
            SettingsSectionLabel("扫描结果")
            scanned.forEach { device ->
                DeviceItem(device = device, onConnect = { viewModel.connect(device.deviceId) })
            }
        }

        if (developerMode) {
            SettingsSectionLabel("开发者选项")
            PremiumCard(contentPadding = 16.dp) {
                Text(text = "诊断与调试", style = CardTitle)
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
                Text(
                    text = "云端同步",
                    style = CardTitle,
                    modifier = Modifier.padding(top = 16.dp)
                )
                OutlinedTextField(
                    value = syncUrl,
                    onValueChange = { viewModel.setSyncBaseUrl(it) },
                    label = { Text("Server URL", fontSize = 11.sp) },
                    placeholder = { Text("http://120.26.204.190", fontSize = 11.sp) },
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
                Button(
                    onClick = { viewModel.triggerSyncNow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.Emerald),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("立即同步")
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
private fun DeviceItem(device: ScannedDevice, onConnect: () -> Unit) {
    PremiumCard(contentPadding = 0.dp) {
        SettingsActionRow(
            title = device.name,
            subtitle = "${device.deviceId} · RSSI ${device.rssi}",
            onClick = onConnect
        )
    }
}

private fun deviceSubtitle(state: ConnectionState, deviceId: String?): String {
    val id = deviceId ?: "未绑定"
    return when (state) {
        ConnectionState.CONNECTED -> id
        ConnectionState.CONNECTING -> "$id · 连接中"
        else -> id
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
