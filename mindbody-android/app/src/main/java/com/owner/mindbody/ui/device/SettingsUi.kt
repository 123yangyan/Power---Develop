package com.owner.mindbody.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
    ) {
        TopAppBar(
            title = { Text(text = title, style = CardTitle) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MindBodyColors.OnBackground,
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MindBodyColors.Background,
                titleContentColor = MindBodyColors.OnBackground,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        style = StatLabel.copy(
            color = MindBodyColors.OnBackgroundSecondary,
            fontSize = 12.sp,
        ),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = MindBodyColors.StatCellBorder.copy(alpha = 0.5f),
        thickness = 0.5.dp,
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    rightContent: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = StatValue.copy(fontSize = CardTitle.fontSize))
            subtitle?.let {
                Text(
                    text = it,
                    style = StatLabel,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        rightContent()
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MindBodyColors.PrimaryIndigo,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column {
                Text(text = title, style = StatValue.copy(fontSize = CardTitle.fontSize))
                subtitle?.let {
                    Text(
                        text = it,
                        style = StatLabel,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MindBodyColors.OnBackgroundSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun StatusBadge(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(MindBodyShapes.Badge)
            .background(
                if (active) MindBodyColors.EmeraldSurface
                else MindBodyColors.StatCellBg,
            )
            .border(
                1.dp,
                if (active) MindBodyColors.Emerald.copy(alpha = 0.25f)
                else MindBodyColors.StatCellBorder,
                MindBodyShapes.Badge,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = StatLabel.copy(
                color = if (active) MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary,
            ),
        )
    }
}

fun formatScheduleHour(hour: Int): String = "%02d:00".format(hour.coerceIn(0, 23))

fun connectionModeLabel(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.PERSISTENT -> "常连接监测流"
    ConnectionMode.ON_DEMAND -> "心情关联秒捕"
}

fun bleCollectionSummary(
    mode: ConnectionMode,
    nightlyEnabled: Boolean,
    bedtimeHour: Int,
    wakeHour: Int,
): String {
    val schedule = if (nightlyEnabled) {
        "夜间 ${formatScheduleHour(bedtimeHour)}–${formatScheduleHour(wakeHour)} 断联"
    } else {
        "保持常连"
    }
    return "${connectionModeLabel(mode)} · $schedule"
}

fun moodReminderSummary(
    notificationsEnabled: Boolean,
    intervalMinutes: Int,
): String = if (notificationsEnabled) {
    "已开启 · 每 $intervalMinutes 分钟"
} else {
    "通知已关闭"
}

fun keepAliveSummary(batteryExempt: Boolean): String = if (batteryExempt) {
    "电池优化已豁免"
} else {
    "建议配置保活权限"
}
