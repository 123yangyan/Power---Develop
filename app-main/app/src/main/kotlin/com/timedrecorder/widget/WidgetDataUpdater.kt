package com.timedrecorder.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.timedrecorder.core.data.repository.MessageRepository
import com.timedrecorder.core.data.repository.ScheduleRepository
import com.timedrecorder.core.data.status.RecordingStateHolder
import com.timedrecorder.core.model.RecordingState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * T11：监听录音状态、任务数、未读消息数，同步更新桌面小组件。
 */
@Singleton
class WidgetDataUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingStateHolder: RecordingStateHolder,
    private val scheduleRepository: ScheduleRepository,
    private val messageRepository: MessageRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun beginObserving() {
        scope.launch {
            combine(
                recordingStateHolder.state,
                scheduleRepository.observeEnabledTasks(),
                messageRepository.observeRecentMessages(100),
            ) { state, tasks, messages ->
                Triple(
                    isActiveSession(state),
                    tasks.size,
                    messages.count { !it.isRead },
                )
            }.collect { (isActive, taskCount, unreadCount) ->
                val elapsedSec = (recordingStateHolder.currentDisplayElapsedMs() / 1000).toInt()
                updateWidget(isActive, taskCount, unreadCount, elapsedSec)
            }
        }
    }

    private fun isActiveSession(state: RecordingState): Boolean {
        return state in setOf(
            RecordingState.RECORDING,
            RecordingState.PAUSED,
            RecordingState.SLICING,
            RecordingState.UPLOADING,
        )
    }

    private suspend fun updateWidget(
        isActive: Boolean,
        taskCount: Int,
        unreadCount: Int,
        elapsedSec: Int,
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(RecorderWidget::class.java)
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
                prefs[RecorderWidget.KEY_IS_RECORDING] = isActive
                prefs[RecorderWidget.KEY_TODAY_TASK_COUNT] = taskCount
                prefs[RecorderWidget.KEY_UNREAD_COUNT] = unreadCount
                prefs[RecorderWidget.KEY_ELAPSED_SECONDS] = elapsedSec
            }
            RecorderWidget().update(context, glanceId)
        }
    }
}
