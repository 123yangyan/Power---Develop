package com.owner.mindbody.ui.mood

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.SectionHeader

@Composable
fun MoodRecordScreen(
    viewModel: MoodRecordViewModel = viewModel()
) {
    val selectedRoleId by viewModel.selectedRoleId.collectAsState()
    val hasSelection by viewModel.hasCoordSelection.collectAsState()
    val diaryText by viewModel.diaryText.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val dailyIndexLabel by viewModel.dailyIndexLabel.collectAsState()
    val lastRecordLabel by viewModel.lastRecordTimeLabel.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        SectionHeader(eyebrow = "心情", title = "记录此刻")

        MoodRecordViewport(
            variant = RecordViewportVariant.PAGE,
            dateLabel = viewModel.dateLabel,
            dailyIndexLabel = dailyIndexLabel,
            lastRecordLabel = lastRecordLabel,
            selectedRoleId = selectedRoleId,
            onSelectRole = viewModel::pickRole,
            hasCoordSelection = hasSelection,
            diaryText = diaryText,
            onDiaryChange = viewModel::setDiaryText,
            error = error,
            saving = saving,
            saveSuccess = saveSuccess,
            onSave = { viewModel.saveEntry() },
            onStageRoleTap = { role ->
                if (diaryText.isBlank()) {
                    viewModel.quickCaptureRole(role)
                } else {
                    viewModel.pickRole(role)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp)
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
    val selectedRoleId by viewModel.selectedRoleId.collectAsState()
    val hasSelection by viewModel.hasCoordSelection.collectAsState()
    val diaryText by viewModel.diaryText.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()

    MoodRecordViewport(
        variant = RecordViewportVariant.MODAL,
        dateLabel = "",
        dailyIndexLabel = null,
        lastRecordLabel = "",
        selectedRoleId = selectedRoleId,
        onSelectRole = viewModel::pickRole,
        hasCoordSelection = hasSelection,
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
