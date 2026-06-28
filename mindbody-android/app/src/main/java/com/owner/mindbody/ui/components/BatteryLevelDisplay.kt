package com.owner.mindbody.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue
import kotlinx.coroutines.delay

private const val STALE_WARNING_MS = 10 * 60 * 1000L
private const val STALE_RECONNECT_MS = 30 * 60 * 1000L
private const val REFRESH_INTERVAL_MS = 60 * 1000L

enum class BatteryDisplayStyle {
    /** 设置页：主值 + 副文案时效 */
    Settings,
    /** 心率页：单行紧凑 */
    Compact,
}

@Composable
fun BatteryLevelDisplay(
    level: Int?,
    updatedAtMs: Long,
    chargeState: ChargeState?,
    style: BatteryDisplayStyle,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(updatedAtMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(REFRESH_INTERVAL_MS)
        }
    }

    val ageMs = if (updatedAtMs > 0L) (nowMs - updatedAtMs).coerceAtLeast(0L) else -1L
    val isCharging = chargeState == ChargeState.CHARGING
    val levelColor = when {
        level == null -> MindBodyColors.OnBackgroundSecondary
        level <= 20 -> MindBodyColors.HeartRed
        else -> MindBodyColors.Emerald
    }
    val freshness = batteryFreshnessText(level, ageMs)
    val freshnessColor = when {
        level == null -> MindBodyColors.OnBackgroundSecondary
        ageMs >= STALE_RECONNECT_MS -> MindBodyColors.HeartRed
        ageMs >= STALE_WARNING_MS -> MindBodyColors.Amber
        else -> MindBodyColors.OnBackgroundSecondary
    }

    when (style) {
        BatteryDisplayStyle.Settings -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isCharging) {
                        Text(
                            text = "⚡",
                            style = StatValue.copy(fontSize = CardTitle.fontSize),
                        )
                    }
                    Text(
                        text = if (level != null) "$level%" else "—",
                        style = StatValue.copy(color = levelColor, fontSize = CardTitle.fontSize),
                    )
                }
                freshness?.let { text ->
                    Text(
                        text = text,
                        style = StatLabel.copy(color = freshnessColor, fontSize = 11.sp),
                    )
                }
            }
        }
        BatteryDisplayStyle.Compact -> {
            val freshnessSuffix = freshness?.let { " $it" }.orEmpty()
            val mainText = buildString {
                append("电量 ")
                append(level?.toString() ?: "--")
                append('%')
                if (isCharging) append(" ⚡")
                append(freshnessSuffix)
            }
            Text(
                text = mainText,
                modifier = modifier,
                style = StatLabel.copy(
                    color = if (ageMs >= STALE_WARNING_MS && level != null) freshnessColor else levelColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

private fun batteryFreshnessText(level: Int?, ageMs: Long): String? {
    if (level == null || ageMs < 0L) return null
    if (ageMs >= STALE_RECONNECT_MS) return "重连设备以刷新电量"
    if (ageMs >= STALE_WARNING_MS) return "⚠ 数据可能过时"
    val minutes = ageMs / 60_000L
    val suffix = when {
        minutes <= 0L -> "刚刚"
        minutes < 60L -> "${minutes}分钟前"
        else -> "${minutes / 60L}小时前"
    }
    return "($suffix)"
}
