package com.owner.mindbody.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.StatLabel

private enum class MoodRecordSegment {
    Record,
    History
}

@Composable
fun MoodRecordScreen(
    viewModel: MoodRecordViewModel = viewModel()
) {
    var segment by rememberSaveable { mutableStateOf(MoodRecordSegment.Record) }

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
        SectionHeader(
            eyebrow = "心情",
            title = when (segment) {
                MoodRecordSegment.Record -> "记录此刻"
                MoodRecordSegment.History -> "历史记录"
            }
        )

        RecordSegmentToggle(
            selected = segment,
            onSelect = { segment = it },
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        when (segment) {
            MoodRecordSegment.Record -> {
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
                        .padding(top = 4.dp)
                )
            }
            MoodRecordSegment.History -> {
                MoodHistoryContent(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RecordSegmentToggle(
    selected: MoodRecordSegment,
    onSelect: (MoodRecordSegment) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MindBodyColors.StatCellBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentChip(
            label = "记录此刻",
            selected = selected == MoodRecordSegment.Record,
            onClick = { onSelect(MoodRecordSegment.Record) },
            modifier = Modifier.weight(1f)
        )
        SegmentChip(
            label = "历史记录",
            selected = selected == MoodRecordSegment.History,
            onClick = { onSelect(MoodRecordSegment.History) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MindBodyColors.CardWhite else MindBodyColors.StatCellBg
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        style = StatLabel.copy(
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MindBodyColors.PrimaryIndigo else MindBodyColors.OnBackgroundSecondary
        ),
        textAlign = TextAlign.Center
    )
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
