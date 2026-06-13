package com.timedrecorder.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.datastore.UserPreferences
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.SectionHeader
import com.timedrecorder.core.model.AudioBitrate
import com.timedrecorder.core.model.HomeCardId
import com.timedrecorder.core.model.ThemeMode

/** 设置页 — PRD §9.10，V1.1 新增 T6/T7/T8/T12 */
@Composable
fun SettingsRoute(
    onNavigateToDiagnostic: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        SettingsUiState.Loading -> {
            Scaffold(
                topBar = { RecorderTopAppBar(title = "设置") },
                containerColor = MaterialTheme.colorScheme.background,
            ) { Text("加载中…", modifier = Modifier.padding(it).padding(16.dp)) }
        }
        is SettingsUiState.Success -> {
            SettingsScreen(
                preferences = state.preferences,
                onBaseUrlChange = viewModel::updateBaseUrl,
                onApiKeyChange = viewModel::updateApiKey,
                onSliceDurationChange = viewModel::updateSliceDuration,
                onRetentionChange = viewModel::updateRetentionDays,
                onPollChange = viewModel::updatePollSettings,
                onWifiOnlyChange = viewModel::updateWifiOnly,
                onNotificationChange = viewModel::updateNotifications,
                onAlertChange = viewModel::updateAlerts,
                onThemeModeChange = viewModel::updateThemeMode,
                onAudioBitrateChange = viewModel::updateAudioBitrate,
                onQuietModeChange = viewModel::updateQuietModeEnabled,
                onQuietStartChange = viewModel::updateQuietStartMinutes,
                onQuietEndChange = viewModel::updateQuietEndMinutes,
                onMoveCardUp = viewModel::moveCardUp,
                onMoveCardDown = viewModel::moveCardDown,
                onNavigateToDiagnostic = onNavigateToDiagnostic,
                onNavigateToAbout = onNavigateToAbout,
                modifier = modifier,
            )
        }
    }
}

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSliceDurationChange: (Int) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onPollChange: (Int, Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onAlertChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onAudioBitrateChange: (AudioBitrate) -> Unit,
    onQuietModeChange: (Boolean) -> Unit,
    onQuietStartChange: (Int) -> Unit,
    onQuietEndChange: (Int) -> Unit,
    onMoveCardUp: (HomeCardId) -> Unit,
    onMoveCardDown: (HomeCardId) -> Unit,
    onNavigateToDiagnostic: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var baseUrl by remember(preferences.baseUrl) { mutableStateOf(preferences.baseUrl) }
    var apiKey by remember(preferences.apiKey) { mutableStateOf(preferences.apiKey) }

    Scaffold(
        topBar = { RecorderTopAppBar(title = "设置") },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // ---- 服务器配置 ----
            SectionHeader("服务器配置")
            SettingsCard {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（必填）") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { onBaseUrlChange(baseUrl) }) { Text("保存") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key（可选）") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = { onApiKeyChange(apiKey) }) { Text("保存") }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            // ---- 录音参数 ----
            SectionHeader("录音参数")
            SettingsCard {
                Text("切片时长", style = MaterialTheme.typography.bodyMedium)
                ChoiceChips(
                    options = listOf(1, 5, 10),
                    selected = preferences.sliceDurationMinutes,
                    labelOf = { "$it 分钟" },
                    onSelect = onSliceDurationChange,
                )
                Text(
                    "切片时长仅对定时值守任务生效，手动录音整段上传",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "文件保留天数",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ChoiceChips(
                    options = listOf(1, 3, 7, 15, 30),
                    selected = preferences.retentionDays,
                    labelOf = { "$it 天" },
                    onSelect = onRetentionChange,
                )
            }

            // ---- T7：录音质量 ----
            SectionHeader("录音质量")
            SettingsCard {
                Text(
                    "调整录音比特率（影响音频清晰度与文件大小）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "修改仅对新建录音生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                AudioBitrate.entries.forEach { bitrate ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAudioBitrateChange(bitrate) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preferences.audioBitrate == bitrate,
                            onClick = { onAudioBitrateChange(bitrate) },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(bitrate.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "约 ${bitrate.estimateMbPerHour.toInt()} MB / 小时",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- 结果轮询 ----
            SectionHeader("结果轮询")
            SettingsCard {
                Text(
                    "每隔 ${preferences.pollIntervalSeconds} 秒查询一次，最多 ${preferences.pollMaxAttempts} 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 通知与网络 ----
            SectionHeader("通知与网络")
            SettingsCard {
                SettingSwitchRow("仅 Wi-Fi 上传", preferences.wifiOnlyUpload, onWifiOnlyChange)
                SettingSwitchRow("通知总开关", preferences.notificationEnabled, onNotificationChange)
                SettingSwitchRow("异常提醒", preferences.alertEnabled, onAlertChange)
            }

            // ---- T8：免打扰时段 ----
            SectionHeader("免打扰时段")
            SettingsCard {
                SettingSwitchRow("启用免打扰", preferences.quietModeEnabled, onQuietModeChange)
                if (preferences.quietModeEnabled) {
                    Text(
                        "静音时段内不推送系统通知，但消息中心仍可查看",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                    QuietTimeRow(
                        label = "开始时间",
                        minutes = preferences.quietStartMinutes,
                        onMinutesChange = onQuietStartChange,
                    )
                    QuietTimeRow(
                        label = "结束时间",
                        minutes = preferences.quietEndMinutes,
                        onMinutesChange = onQuietEndChange,
                    )
                }
            }

            // ---- T6：外观主题 ----
            SectionHeader("外观主题")
            SettingsCard {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeModeChange(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                        )
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // ---- T12：首页卡片排序 ----
            SectionHeader("首页卡片顺序")
            SettingsCard {
                Text(
                    "调整首页各功能区的显示顺序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                preferences.homeCardOrder.forEachIndexed { idx, cardId ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${idx + 1}. ${cardId.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { onMoveCardUp(cardId) },
                            enabled = idx > 0,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            onClick = { onMoveCardDown(cardId) },
                            enabled = idx < preferences.homeCardOrder.lastIndex,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                        }
                    }
                }
            }

            // ---- 其他 ----
            SectionHeader("其他")
            SettingsCard {
                SettingNavRow(Icons.Filled.BugReport, "日志 / 诊断", onNavigateToDiagnostic)
                SettingNavRow(Icons.Filled.Info, "关于 / 隐私", onNavigateToAbout)
            }
        }
    }
}

/** 静音时间行：显示当前时间值并提供加减按钮 */
@Composable
private fun QuietTimeRow(label: String, minutes: Int, onMinutesChange: (Int) -> Unit) {
    val hour = minutes / 60
    val minute = minutes % 60
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onMinutesChange(((minutes - 30) + 1440) % 1440) }) {
            Text("−30m", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = { onMinutesChange((minutes + 30) % 1440) }) {
            Text("+30m", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<Int>,
    selected: Int,
    labelOf: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingNavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
