package com.timedrecorder.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * T11：桌面小组件接收器，注册入口。
 * 在 AndroidManifest.xml 中声明，系统通过此 Receiver 与 Widget 通信。
 */
class RecorderWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecorderWidget()
}
