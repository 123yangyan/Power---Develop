package com.owner.mindbody.ui.physio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owner.mindbody.data.LlmFeedbackEntry
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCaption
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel

/**
 * LLM 反馈历史条目卡片。
 *
 * 展示：状态 Badge + 焦虑分、时间戳、完整 LLM 消息（可展开/折叠）、用户响应标签。
 */
@Composable
fun FeedbackHistoryCard(
    entry: LlmFeedbackEntry,
    onRecordNow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stateToken = StateColors.of(entry.stateLabel)
    var expanded by remember { mutableStateOf(false) }

    NarrativeCard(
        modifier = modifier,
        accentColor = stateToken.accentColor,
        badgeLabel = stateToken.zhLabel
    ) {
        // 时间戳 + 焦虑分行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NarrativeCaption(
                text = formatHistoryTime(entry.timestampMs)
            )
            if (entry.stateLabel != "baseline_building") {
                Box(
                    modifier = Modifier
                        .clip(MindBodyShapes.Badge)
                        .background(stateToken.accentColor.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "焦虑指数 ${entry.anxietyScore.toInt()}",
                        style = StatLabel.copy(
                            color = stateToken.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 消息正文（默认 3 行，可展开）
        NarrativeBody(
            text = entry.message,
            modifier = if (!expanded) Modifier else Modifier
        )
        if (!expanded && entry.message.length > 120) {
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "展开全文",
                    style = StatLabel.copy(
                        color = stateToken.accentColor,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 用户响应区域
        val response = entry.userResponse
        if (response != null) {
            Box(
                modifier = Modifier
                    .clip(MindBodyShapes.DataCard)
                    .background(MindBodyColors.StatCellBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                NarrativeCaption(
                    text = when (response) {
                        "recorded" -> "✓ 已记录心情"
                        "deferred" -> "○ 已推迟"
                        "dismissed" -> "× 已忽略"
                        else -> response
                    }
                )
            }
        } else if (onRecordNow != null) {
            TextButton(onClick = onRecordNow) {
                Text(
                    text = "现在记录心情 →",
                    style = StatLabel.copy(
                        color = stateToken.accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

private fun formatHistoryTime(tsMs: Long): String {
    val diffMs = System.currentTimeMillis() - tsMs
    return when {
        diffMs < 60_000L -> "刚刚"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} 分钟前"
        diffMs < 86_400_000L -> "${diffMs / 3_600_000L} 小时前"
        diffMs < 7 * 86_400_000L -> "${diffMs / 86_400_000L} 天前"
        else -> {
            val date = java.util.Date(tsMs)
            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(date)
        }
    }
}
