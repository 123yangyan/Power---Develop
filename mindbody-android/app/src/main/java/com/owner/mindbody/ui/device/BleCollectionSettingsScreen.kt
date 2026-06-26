package com.owner.mindbody.ui.device

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.BleModeRadioGroup
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.StatLabel

@Composable
fun BleCollectionSettingsScreen(
    onBack: () -> Unit,
    viewModel: DeviceViewModel = viewModel(),
) {
    val mode by viewModel.connectionMode.collectAsState()
    val bedtimeHour by viewModel.bedtimeHour.collectAsState()
    val wakeHour by viewModel.wakeHour.collectAsState()
    val nightlyEnabled by viewModel.bleNightlyScheduleEnabled.collectAsState()
    val context = LocalContext.current

    SettingsSubScreenScaffold(title = "采集策略", onBack = onBack) {
        SettingsSectionLabel("BLE 采集模式")
        PremiumCard {
            Text(text = "选择数据采集方式", style = CardTitle)
            BleModeRadioGroup(
                selectedMode = mode,
                onModeSelected = viewModel::setConnectionMode,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        SettingsSectionLabel("夜间调度")
        PremiumCard(contentPadding = 0.dp) {
            SettingsRow(
                title = "夜间自动断联",
                subtitle = if (nightlyEnabled) {
                    "每日 ${formatScheduleHour(bedtimeHour)} 断联，${formatScheduleHour(wakeHour)} 重连"
                } else {
                    "已关闭，设备将保持常连"
                },
                rightContent = {
                    Switch(
                        checked = nightlyEnabled,
                        onCheckedChange = viewModel::setBleNightlyScheduleEnabled,
                    )
                },
            )
            if (nightlyEnabled) {
                SettingsDivider()
                SettingsActionRow(
                    title = "断联时间",
                    subtitle = formatScheduleHour(bedtimeHour),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, _ -> viewModel.setBedtimeHour(hourOfDay) },
                            bedtimeHour,
                            0,
                            true,
                        ).show()
                    },
                )
                SettingsDivider()
                SettingsActionRow(
                    title = "重连时间",
                    subtitle = formatScheduleHour(wakeHour),
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, _ -> viewModel.setWakeHour(hourOfDay) },
                            wakeHour,
                            0,
                            true,
                        ).show()
                    },
                )
            }
        }

        Text(
            text = "常连接适合全天监测；按需模式仅在记录心情时唤醒蓝牙。夜间断联可让手环离线记录睡眠数据。",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary),
        )
    }
}
