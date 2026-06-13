package com.timedrecorder.feature.recording

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector
import com.timedrecorder.core.model.RecordingScenario

/** 场景图标映射（UI 层，避免 core:model 依赖 Compose） */
fun RecordingScenario.icon(): ImageVector = when (this) {
    RecordingScenario.QUICK_NOTE -> Icons.Filled.Edit
    RecordingScenario.MEETING -> Icons.Filled.Groups
    RecordingScenario.LECTURE -> Icons.Filled.School
    RecordingScenario.TIMED_GUARD -> Icons.Filled.Schedule
}
