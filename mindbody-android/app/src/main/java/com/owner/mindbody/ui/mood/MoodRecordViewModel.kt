package com.owner.mindbody.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.data.local.MoodEntryEntity
import com.owner.mindbody.polar.ConnectionMode
import com.owner.mindbody.worker.MoodReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MoodRecordViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MindBodyApplication
    private val moodRepository = app.storage.mood
    private val hrRepository = app.storage.hr
    private val polarBleManager = app.polarBleManager
    private val devicePreferences = app.devicePreferences
    private val moodPreferences = app.moodPreferences

    private val zoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE")

    private val _coordX = MutableStateFlow(0)
    val coordX: StateFlow<Int> = _coordX.asStateFlow()

    private val _coordY = MutableStateFlow(0)
    val coordY: StateFlow<Int> = _coordY.asStateFlow()

    private val _hasCoordSelection = MutableStateFlow(false)
    val hasCoordSelection: StateFlow<Boolean> = _hasCoordSelection.asStateFlow()

    private val _diaryText = MutableStateFlow("")
    val diaryText: StateFlow<String> = _diaryText.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    val dateLabel: String = dateFormatter.format(java.time.LocalDate.now(zoneId))

    private val allEntries = moodRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyIndexLabel: StateFlow<String?> = allEntries.map { entries ->
        val meta = getNextDailyEntryIndex(entries, todayDateKey(zoneId), zoneId)
        formatDailyIndexLabel(meta)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastRecordTimeLabel: StateFlow<String> = allEntries.map { entries ->
        val latest = entries.firstOrNull { !isAvoidanceEntry(it.fact) }
            ?: entries.firstOrNull()
        if (latest == null) {
            "尚无记录"
        } else {
            "上次记录 ${formatEntryTime(latest.occurredAt)}"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "尚无记录")

    val todayEntryCount: StateFlow<Int> = allEntries.map { entries ->
        entriesOnDate(entries, todayDateKey(zoneId), zoneId).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val reminderIntervalMinutes = moodPreferences.reminderIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    val quietStart = moodPreferences.quietStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoodPreferences.DEFAULT_QUIET_START)

    val quietEnd = moodPreferences.quietEnd
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MoodPreferences.DEFAULT_QUIET_END)

    val notificationsEnabled = moodPreferences.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val strongPopup = moodPreferences.strongPopup
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun pickCoord(x: Int, y: Int) {
        _coordX.value = x
        _coordY.value = y
        _hasCoordSelection.value = true
        _error.value = null
    }

    fun setDiaryText(text: String) {
        _diaryText.value = text
    }

    fun resetFormAfterSave() {
        _diaryText.value = ""
        _coordX.value = 0
        _coordY.value = 0
        _hasCoordSelection.value = false
        _error.value = null
    }

    fun saveEntry(onSaved: () -> Unit = {}) {
        if (!_hasCoordSelection.value) {
            _error.value = "请先在网格中点选坐标"
            return
        }
        if (_saving.value) return

        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            _saveSuccess.value = false
            try {
                val occurredAt = System.currentTimeMillis()
                val hrAtEntry = resolveHrSnapshot(occurredAt)
                moodRepository.insert(
                    fact = _diaryText.value.trim(),
                    coordX = _coordX.value,
                    coordY = _coordY.value,
                    occurredAt = occurredAt,
                    hrAtEntry = hrAtEntry
                )
                moodPreferences.setLastReminderAt(System.currentTimeMillis())
                _saveSuccess.value = true
                resetFormAfterSave()
                onSaved()
            } catch (_: Exception) {
                _error.value = "保存失败，请重试"
            } finally {
                _saving.value = false
            }
        }
    }

    fun updateEntry(entry: MoodEntryEntity, onUpdated: (MoodEntryEntity) -> Unit) {
        if (!_hasCoordSelection.value) {
            _error.value = "请先在网格中点选坐标"
            return
        }
        if (_saving.value) return

        viewModelScope.launch {
            _saving.value = true
            _error.value = null
            try {
                val updated = moodRepository.update(
                    id = entry.id,
                    fact = _diaryText.value.trim(),
                    coordX = _coordX.value,
                    coordY = _coordY.value,
                    occurredAt = entry.occurredAt,
                    hrAtEntry = entry.hrAtEntry
                )
                if (updated != null) onUpdated(updated) else _error.value = "记录不存在"
            } catch (_: Exception) {
                _error.value = "保存失败，请重试"
            } finally {
                _saving.value = false
            }
        }
    }

    /** Esc / 稍后：写入逃避记录并重置 snooze 间隔 */
    fun recordSnooze(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                moodRepository.insert(
                    fact = MoodCheckInConstants.AVOIDANCE_FACT,
                    coordX = 0,
                    coordY = 0,
                    occurredAt = System.currentTimeMillis(),
                    hrAtEntry = null
                )
                moodPreferences.incrementSnoozeCount(moodPreferences.todayDateKey())
                moodPreferences.setLastReminderAt(System.currentTimeMillis())
            } catch (_: Exception) {
                // 逃避记录失败不阻断关闭
            }
            onDone()
        }
    }

    fun loadForEdit(entry: MoodEntryEntity) {
        _coordX.value = entry.coordX
        _coordY.value = entry.coordY
        _hasCoordSelection.value = true
        _diaryText.value = entry.fact
        _error.value = null
        _saveSuccess.value = false
    }

    fun setReminderInterval(minutes: Int) {
        viewModelScope.launch {
            moodPreferences.setReminderIntervalMinutes(minutes)
            MoodReminderScheduler.schedule(getApplication())
        }
    }

    fun setQuietHours(start: String, end: String) {
        viewModelScope.launch { moodPreferences.setQuietHours(start, end) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { moodPreferences.setNotificationsEnabled(enabled) }
    }

    fun setStrongPopup(enabled: Boolean) {
        viewModelScope.launch { moodPreferences.setStrongPopup(enabled) }
    }

    fun scheduleTestReminder(delaySeconds: Int) {
        MoodReminderScheduler.scheduleTestReminder(getApplication(), delaySeconds)
    }

    private suspend fun resolveHrSnapshot(occurredAt: Long): Int? {
        val fromLocal = hrRepository.getHrNearTimestamp(occurredAt)
        if (fromLocal != null) return fromLocal
        val mode = devicePreferences.connectionMode.first()
        if (mode != ConnectionMode.ON_DEMAND) return null
        val deviceId = devicePreferences.savedDeviceId.first() ?: return null
        return polarBleManager.connectForSnapshot(deviceId)
    }

    fun formatEntryTime(timestamp: Long): String {
        return timeFormatter.format(Instant.ofEpochMilli(timestamp).atZone(zoneId))
    }
}
