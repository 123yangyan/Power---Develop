package com.timedrecorder.feature.settings

import com.timedrecorder.core.datastore.UserPreferences
import com.timedrecorder.core.model.AppLogEntry
import com.timedrecorder.core.model.RecordingState

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Success(val preferences: UserPreferences) : SettingsUiState
}

sealed interface DiagnosticUiState {
    data object Loading : DiagnosticUiState
    data class Success(
        val recordingState: RecordingState,
        val logs: List<AppLogEntry>,
        val hasRecordPermission: Boolean,
        val hasNotificationPermission: Boolean,
    ) : DiagnosticUiState
}
