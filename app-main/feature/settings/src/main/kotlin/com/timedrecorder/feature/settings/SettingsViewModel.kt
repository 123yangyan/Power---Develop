package com.timedrecorder.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.timedrecorder.core.data.repository.LogRepository
import com.timedrecorder.core.data.status.RecordingStateHolder
import com.timedrecorder.core.datastore.PreferencesDataSource
import com.timedrecorder.core.datastore.UserPreferences
import com.timedrecorder.core.model.AudioBitrate
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.HomeCardId
import com.timedrecorder.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = preferencesDataSource.userPreferences
        .map { SettingsUiState.Success(it) as SettingsUiState }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState.Loading)

    fun updateBaseUrl(url: String) = viewModelScope.launch { preferencesDataSource.updateBaseUrl(url) }
    fun updateApiKey(key: String) = viewModelScope.launch { preferencesDataSource.updateApiKey(key) }
    fun updateSliceDuration(minutes: Int) = viewModelScope.launch { preferencesDataSource.updateSliceDuration(minutes) }
    fun updateRetentionDays(days: Int) = viewModelScope.launch { preferencesDataSource.updateRetentionDays(days) }
    fun updatePollSettings(interval: Int, maxAttempts: Int) = viewModelScope.launch {
        preferencesDataSource.updatePollSettings(interval, maxAttempts)
    }
    fun updateWifiOnly(enabled: Boolean) = viewModelScope.launch { preferencesDataSource.updateWifiOnlyUpload(enabled) }
    fun updateNotifications(enabled: Boolean) = viewModelScope.launch { preferencesDataSource.updateNotificationEnabled(enabled) }
    fun updateAlerts(enabled: Boolean) = viewModelScope.launch { preferencesDataSource.updateAlertEnabled(enabled) }

    // ---- T6：主题模式 ----
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { preferencesDataSource.updateThemeMode(mode) }

    // ---- T7：录音质量 ----
    fun updateAudioBitrate(bitrate: AudioBitrate) = viewModelScope.launch { preferencesDataSource.updateAudioBitrate(bitrate) }

    // ---- T8：静音时段 ----
    fun updateQuietModeEnabled(enabled: Boolean) = viewModelScope.launch { preferencesDataSource.updateQuietModeEnabled(enabled) }
    fun updateQuietStartMinutes(minutes: Int) = viewModelScope.launch { preferencesDataSource.updateQuietStartMinutes(minutes) }
    fun updateQuietEndMinutes(minutes: Int) = viewModelScope.launch { preferencesDataSource.updateQuietEndMinutes(minutes) }

    // ---- T12：首页卡片排序 ----
    fun moveCardUp(cardId: HomeCardId) = viewModelScope.launch {
        val current = (uiState.value as? SettingsUiState.Success)?.preferences?.homeCardOrder ?: return@launch
        val idx = current.indexOf(cardId)
        if (idx > 0) {
            val newOrder = current.toMutableList().also { it.swap(idx, idx - 1) }
            preferencesDataSource.updateHomeCardOrder(newOrder)
        }
    }

    fun moveCardDown(cardId: HomeCardId) = viewModelScope.launch {
        val current = (uiState.value as? SettingsUiState.Success)?.preferences?.homeCardOrder ?: return@launch
        val idx = current.indexOf(cardId)
        if (idx < current.lastIndex) {
            val newOrder = current.toMutableList().also { it.swap(idx, idx + 1) }
            preferencesDataSource.updateHomeCardOrder(newOrder)
        }
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) { val tmp = this[i]; this[i] = this[j]; this[j] = tmp }
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    logRepository: LogRepository,
    recordingStateHolder: RecordingStateHolder,
) : ViewModel() {

    var hasRecordPermission: Boolean = false
    var hasNotificationPermission: Boolean = false

    val uiState: StateFlow<DiagnosticUiState> = combine(
        recordingStateHolder.state,
        logRepository.observeRecentLogs(50),
    ) { state, logs ->
        DiagnosticUiState.Success(
            recordingState = state,
            logs = logs,
            hasRecordPermission = hasRecordPermission,
            hasNotificationPermission = hasNotificationPermission,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticUiState.Loading)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesDataSource: PreferencesDataSource,
    private val recordingScheduler: com.timedrecorder.core.data.scheduler.RecordingScheduler,
) : ViewModel() {

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesDataSource.setOnboardingCompleted(true)
            recordingScheduler.rescheduleAllAlarms()
        }
    }
}
