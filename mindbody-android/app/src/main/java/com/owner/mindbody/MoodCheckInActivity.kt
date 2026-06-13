package com.owner.mindbody

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.mood.MoodRecordViewModel
import com.owner.mindbody.ui.mood.MoodRecordViewport
import com.owner.mindbody.ui.mood.RecordViewportVariant
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyTheme

/** 强弹窗 check-in，对齐 emotion CheckInPanel / openCheckInWindow。 */
class MoodCheckInActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindBodyTheme {
                CheckInContent(onFinish = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, MoodCheckInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun CheckInContent(onFinish: () -> Unit) {
    val viewModel: MoodRecordViewModel = viewModel()
    val coordX by viewModel.coordX.collectAsState()
    val coordY by viewModel.coordY.collectAsState()
    val hasSelection by viewModel.hasCoordSelection.collectAsState()
    val diaryText by viewModel.diaryText.collectAsState()
    val error by viewModel.error.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    val dailyIndexLabel by viewModel.dailyIndexLabel.collectAsState()

    val onSnooze = { viewModel.recordSnooze(onFinish) }
    BackHandler { onSnooze() }

    MoodRecordViewport(
        variant = RecordViewportVariant.POPUP,
        dateLabel = viewModel.dateLabel,
        dailyIndexLabel = dailyIndexLabel,
        lastRecordLabel = "到时间了，留意一下此刻",
        coordX = coordX,
        coordY = coordY,
        hasCoordSelection = hasSelection,
        onPickCoord = viewModel::pickCoord,
        diaryText = diaryText,
        onDiaryChange = viewModel::setDiaryText,
        error = error,
        saving = saving,
        saveSuccess = saveSuccess,
        onSave = { viewModel.saveEntry(onFinish) },
        onCancel = onSnooze,
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
            .padding(20.dp)
    )
}
