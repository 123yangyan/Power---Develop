package com.owner.mindbody.ui.device

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.polar.ConnectionState
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.util.CompanionDeviceHelper
import com.owner.mindbody.util.PowerKeepAlive

@Composable
fun KeepAliveSettingsScreen(
    onBack: () -> Unit,
    viewModel: DeviceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepAliveStatus by viewModel.keepAliveStatus.collectAsState()
    val companionAssociated by viewModel.companionAssociated.collectAsState()
    val associatingInProgress by viewModel.associatingInProgress.collectAsState()
    val connectedId by viewModel.connectedDeviceId.collectAsState()
    val savedId by viewModel.savedDeviceId.collectAsState()
    val connectedDeviceId = connectedId ?: savedId

    val activity = context as? Activity
    val associateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val deviceId = connectedId ?: savedId
        if (deviceId != null) {
            viewModel.onCompanionAssociationComplete(
                deviceId = deviceId,
                success = result.resultCode == Activity.RESULT_OK,
            )
        } else {
            viewModel.onCompanionAssociationComplete(deviceId = "", success = false)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshKeepAlive()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val batteryExempt = keepAliveStatus.batteryExempt
    val fgsActive = keepAliveStatus.foregroundRunning
    val bleActive = keepAliveStatus.bleState == ConnectionState.CONNECTED
    val heartbeatLabel = when {
        keepAliveStatus.lastHeartbeatMs <= 0L -> "等待中"
        keepAliveStatus.heartbeatFresh -> {
            val sec = ((System.currentTimeMillis() - keepAliveStatus.lastHeartbeatMs) / 1000)
                .coerceAtLeast(0)
            "${sec} 秒前"
        }
        else -> "已过期"
    }
    val companionSupported = CompanionDeviceHelper.isSupported()

    SettingsSubScreenScaffold(title = "后台保活", onBack = onBack) {
        SettingsSectionLabel("运行状态")
        PremiumCard(contentPadding = 0.dp) {
            SettingsRow(
                title = "电池优化",
                subtitle = if (batteryExempt) "已设为无限制" else "建议设为无限制",
                rightContent = {
                    StatusBadge(
                        text = if (batteryExempt) "已豁免" else "未豁免",
                        active = batteryExempt,
                    )
                },
            )
            SettingsDivider()
            KeepAliveStatusRow(
                label = "前台服务",
                value = if (fgsActive) "运行中" else "未运行",
                active = fgsActive,
            )
            SettingsDivider()
            KeepAliveStatusRow(
                label = "BLE",
                value = bleStateLabel(keepAliveStatus.bleState),
                active = bleActive,
            )
            SettingsDivider()
            KeepAliveStatusRow(
                label = "心跳",
                value = heartbeatLabel,
                active = keepAliveStatus.heartbeatFresh,
            )
            if (companionSupported) {
                SettingsDivider()
                KeepAliveStatusRow(
                    label = "伴随设备 (CDM)",
                    value = if (companionAssociated) "已关联" else "未关联",
                    active = companionAssociated,
                )
            }
        }

        SettingsSectionLabel("操作")
        PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!batteryExempt) {
                    Button(
                        onClick = { PowerKeepAlive.requestIgnoreBatteryOptimizations(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MindBodyColors.PrimaryIndigo,
                        ),
                        shape = MindBodyShapes.RadioOption,
                    ) {
                        Text("设为无限制")
                    }
                }
                if (companionSupported && !companionAssociated && !connectedDeviceId.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            val deviceId = connectedDeviceId
                            val hostActivity = activity
                            if (deviceId.isNullOrBlank() || hostActivity == null || associatingInProgress) {
                                return@OutlinedButton
                            }
                            viewModel.startCompanionAssociation(hostActivity, deviceId) { intentSender ->
                                associateLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build(),
                                )
                            }
                        },
                        enabled = !associatingInProgress,
                        modifier = Modifier.weight(1f),
                        shape = MindBodyShapes.RadioOption,
                    ) {
                        Text(if (associatingInProgress) "关联中…" else "关联伴随设备")
                    }
                }
                OutlinedButton(
                    onClick = { PowerKeepAlive.openAutoStartSettings(context) },
                    modifier = Modifier.weight(1f),
                    shape = MindBodyShapes.RadioOption,
                ) {
                    Text("打开自启动")
                }
            }
        }

        Text(
            text = "息屏后需保持 BLE 连接。小米 HyperOS 请开启自启动、省电无限制，并在多任务界面锁定本应用。",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
        )
    }
}

@Composable
private fun KeepAliveStatusRow(label: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = StatLabel)
        StatusBadge(text = value, active = active)
    }
}

private fun bleStateLabel(state: ConnectionState): String = when (state) {
    ConnectionState.CONNECTED -> "已连接"
    ConnectionState.CONNECTING -> "连接中"
    ConnectionState.BLE_OFF -> "蓝牙关闭"
    ConnectionState.DISCONNECTED -> "已断开"
}
