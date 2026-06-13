package com.timedrecorder.feature.recording

import androidx.lifecycle.ViewModel
import com.timedrecorder.core.data.record.RecordingController
import com.timedrecorder.core.model.RecordingScenario
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RecordingScenarioViewModel @Inject constructor(
    private val recordingController: RecordingController,
) : ViewModel() {
    fun startRecording(scenario: RecordingScenario) {
        recordingController.startManualRecording(scenario)
    }
}
