package com.timedrecorder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * T11：桌面小组件，使用 Glance API 以 Compose 风格编写。
 *
 * 注意：Widget 内只能使用 Glance 的组件子集（如 GlanceText、GlanceRow 等），
 * 不能直接使用标准 Compose 组件。
 *
 * 显示内容：录音状态指示点 + 今日任务数 + 未读消息数
 */
class RecorderWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
            val isRecording = prefs[KEY_IS_RECORDING] ?: false
            val todayTaskCount = prefs[KEY_TODAY_TASK_COUNT] ?: 0
            val unreadCount = prefs[KEY_UNREAD_COUNT] ?: 0
            val elapsedSec = prefs[KEY_ELAPSED_SECONDS] ?: 0

            WidgetContent(
                isRecording = isRecording,
                todayTaskCount = todayTaskCount,
                unreadCount = unreadCount,
                elapsedSec = elapsedSec,
            )
        }
    }

    @Composable
    private fun WidgetContent(
        isRecording: Boolean,
        todayTaskCount: Int,
        unreadCount: Int,
        elapsedSec: Int,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1E1E2E)))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 应用名称
            Text(
                text = "定时录音助手",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFCDD6F4)),
                    fontSize = 12.sp,
                ),
            )

            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 录音状态指示
                val statusColor = if (isRecording) Color(0xFFF38BA8) else Color(0xFF6C7086)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isRecording) {
                            if (elapsedSec > 0) "● ${formatWidgetElapsed(elapsedSec)}" else "● 录音中"
                        } else {
                            "○ 空闲"
                        },
                        style = TextStyle(
                            color = ColorProvider(statusColor),
                            fontSize = 14.sp,
                            fontWeight = if (isRecording) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }

                Spacer(GlanceModifier.width(16.dp))

                // 今日任务数
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$todayTaskCount",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFA6E3A1)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "今日任务",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF6C7086)),
                            fontSize = 10.sp,
                        ),
                    )
                }

                Spacer(GlanceModifier.width(16.dp))

                // 未读消息数
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$unreadCount",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFF9E2AF)),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = "未读消息",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF6C7086)),
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        /** 小组件状态存储键 */
        val KEY_IS_RECORDING = booleanPreferencesKey("widget_is_recording")
        val KEY_TODAY_TASK_COUNT = intPreferencesKey("widget_today_task_count")
        val KEY_UNREAD_COUNT = intPreferencesKey("widget_unread_count")
        val KEY_ELAPSED_SECONDS = intPreferencesKey("widget_elapsed_seconds")
    }
}

private fun formatWidgetElapsed(totalSec: Int): String {
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
