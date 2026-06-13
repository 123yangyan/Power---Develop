package com.owner.mindbody.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

@Composable
fun BleModeRadioGroup(
    selectedMode: ConnectionMode,
    onModeSelected: (ConnectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BleModeOption(
            title = "常连接监测流 (默认)",
            description = "保持高频数据不中断，意外断连 3 秒自动重捕，全天不漏点。",
            selected = selectedMode == ConnectionMode.PERSISTENT,
            onClick = { onModeSelected(ConnectionMode.PERSISTENT) }
        )
        BleModeOption(
            title = "心情关联秒捕 (省电)",
            description = "仅在手写心情时唤醒蓝牙采集 5 秒瞬间心率快照，降耗 90%。",
            selected = selectedMode == ConnectionMode.ON_DEMAND,
            onClick = { onModeSelected(ConnectionMode.ON_DEMAND) }
        )
    }
}

@Composable
private fun BleModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.RadioOption)
            .background(
                if (selected) MindBodyColors.PrimaryIndigoLight.copy(alpha = 0.25f)
                else MindBodyColors.StatCellBg
            )
            .border(
                width = 1.dp,
                color = if (selected) MindBodyColors.PrimaryIndigo.copy(alpha = 0.25f)
                else MindBodyColors.StatCellBorder,
                shape = MindBodyShapes.RadioOption
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) MindBodyColors.PrimaryIndigo else MindBodyColors.OnBackgroundSecondary,
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MindBodyColors.PrimaryIndigo
                    else MindBodyColors.Background.copy(alpha = 0f)
                )
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = CardTitle.copy(
                    fontSize = CardTitle.fontSize,
                    letterSpacing = 0.sp,
                    color = MindBodyColors.OnBackground
                )
            )
            Text(text = description, style = StatLabel)
        }
    }
}
