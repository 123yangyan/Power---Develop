package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.MoodPreferences
import com.owner.mindbody.ui.components.SectionHeader

@Composable
fun MoodRecordScreen(
    viewModel: MoodRecordViewModel = viewModel()
) {
    val coordX by viewModel.coordX.collectAsState()
    val coordY by viewModel.coordY.collectAsState()
    val hasSelection by viewModel.hasCoordSelection.collectAsState()
    val diaryText by viewModel.diaryText.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val dailyIndexLabel by viewModel.dailyIndexLabel.collectAsState()
    val lastRecordLabel by viewModel.lastRecordTimeLabel.collectAsState()
    val reminderInterval by viewModel.reminderIntervalMinutes.collectAsState()
    val quietStart by viewModel.quietStart.collectAsState()
    val quietEnd by viewModel.quietEnd.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val strongPopup by viewModel.strongPopup.collectAsState()
    val todayCount by viewModel.todayEntryCount.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var intervalInput by remember(reminderInterval) { mutableStateOf(reminderInterval.toString()) }
    var quietStartInput by remember(quietStart) { mutableStateOf(quietStart) }
    var quietEndInput by remember(quietEnd) { mutableStateOf(quietEnd) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionHeader(eyebrow = "心情", title = "记录此刻")

        MoodRecordViewport(
            variant = RecordViewportVariant.PAGE,
            dateLabel = viewModel.dateLabel,
            dailyIndexLabel = dailyIndexLabel,
            lastRecordLabel = lastRecordLabel,
            coordX = coordX,
            coordY = coordY,
            hasCoordSelection = hasSelection,
            onPickCoord = viewModel::pickCoord,
            diaryText = diaryText,
            onDiaryChange = viewModel::setDiaryText,
            error = error,
            saving = saving,
            saveSuccess = saveSuccess,
            onSave = { viewModel.saveEntry() },
            modifier = Modifier.padding(top = 8.dp)
        )

        MoodSettingsSection(
            showSettings = showSettings,
            onShowSettingsChange = { showSettings = it },
            notificationsEnabled = notificationsEnabled,
            onNotificationsChange = viewModel::setNotificationsEnabled,
            strongPopup = strongPopup,
            onStrongPopupChange = viewModel::setStrongPopup,
            intervalInput = intervalInput,
            onIntervalInputChange = { intervalInput = it },
            onApplyInterval = {
                viewModel.setReminderInterval(
                    intervalInput.toIntOrNull() ?: MoodPreferences.DEFAULT_REMINDER_INTERVAL_MINUTES
                )
            },
            quietStartInput = quietStartInput,
            onQuietStartChange = { quietStartInput = it },
            quietEndInput = quietEndInput,
            onQuietEndChange = { quietEndInput = it },
            onApplyQuietHours = { viewModel.setQuietHours(quietStartInput, quietEndInput) },
            todayEntryCount = todayCount,
            onTestReminder30s = { viewModel.scheduleTestReminder(30) },
            onTestReminder60s = { viewModel.scheduleTestReminder(60) },
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/** 编辑弹窗内复用的表单 */
@Composable
fun MoodEditForm(
    viewModel: MoodRecordViewModel,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val coordX by viewModel.coordX.collectAsState()
    val coordY by viewModel.coordY.collectAsState()
    val hasSelection by viewModel.hasCoordSelection.collectAsState()
    val diaryText by viewModel.diaryText.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()

    MoodRecordViewport(
        variant = RecordViewportVariant.MODAL,
        dateLabel = "",
        dailyIndexLabel = null,
        lastRecordLabel = "",
        coordX = coordX,
        coordY = coordY,
        hasCoordSelection = hasSelection,
        onPickCoord = viewModel::pickCoord,
        diaryText = diaryText,
        onDiaryChange = viewModel::setDiaryText,
        error = error,
        saving = saving,
        saveSuccess = false,
        isEdit = true,
        onSave = onSave,
        onCancel = onCancel
    )
}
