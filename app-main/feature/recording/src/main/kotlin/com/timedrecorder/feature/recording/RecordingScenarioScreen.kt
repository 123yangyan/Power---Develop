package com.timedrecorder.feature.recording

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.ScenarioGridCard
import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.ScenarioAction

/**
 * 录音准备页 BottomSheet：2×2 场景宫格 + 底部开录按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScenarioBottomSheet(
    onDismiss: () -> Unit,
    onStartRecording: (RecordingScenario) -> Unit,
    onNavigateToSchedule: () -> Unit,
    initialScenario: RecordingScenario? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by rememberSaveable {
        mutableStateOf(initialScenario ?: RecordingScenario.QUICK_NOTE)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp),
        ) {
            // ── 2×2 场景宫格 ──────────────────────────────────────────
            val scenarios = RecordingScenario.entries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScenarioGridCard(
                    title = scenarios[0].displayName,
                    durationHint = scenarios[0].durationHint,
                    sliceHint = scenarios[0].sliceHint,
                    icon = scenarios[0].icon(),
                    selected = selected == scenarios[0],
                    onClick = { selected = scenarios[0] },
                    modifier = Modifier.weight(1f),
                )
                ScenarioGridCard(
                    title = scenarios[1].displayName,
                    durationHint = scenarios[1].durationHint,
                    sliceHint = scenarios[1].sliceHint,
                    icon = scenarios[1].icon(),
                    selected = selected == scenarios[1],
                    onClick = { selected = scenarios[1] },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScenarioGridCard(
                    title = scenarios[2].displayName,
                    durationHint = scenarios[2].durationHint,
                    sliceHint = scenarios[2].sliceHint,
                    icon = scenarios[2].icon(),
                    selected = selected == scenarios[2],
                    onClick = { selected = scenarios[2] },
                    modifier = Modifier.weight(1f),
                )
                ScenarioGridCard(
                    title = scenarios[3].displayName,
                    durationHint = scenarios[3].durationHint,
                    sliceHint = scenarios[3].sliceHint,
                    icon = scenarios[3].icon(),
                    selected = selected == scenarios[3],
                    onClick = { selected = scenarios[3] },
                    modifier = Modifier.weight(1f),
                )
            }

            // ── 底部：大红圆形开录按钮（原型 record-trigger-btn 样式）──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 36.dp),
                contentAlignment = Alignment.Center,
            ) {
                RecordTriggerButton(
                    onClick = {
                        val scenario = selected ?: return@RecordTriggerButton
                        when (scenario.action) {
                            ScenarioAction.START_MANUAL_RECORDING -> onStartRecording(scenario)
                            ScenarioAction.NAVIGATE_TO_SCHEDULE -> {
                                onDismiss()
                                onNavigateToSchedule()
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 大红开录按钮（精确对齐原型 record-trigger-btn）：
 * - 外层白色 76dp 圆形，环形阴影
 * - 内层红色 58dp 实心圆
 */
@Composable
fun RecordTriggerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 外层：白色圆形 + 环形阴影（用两层 border + shadow 模拟）
    Box(
        modifier = modifier
            .size(76.dp)
            .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = 4.dp, color = MaterialTheme.colorScheme.background, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.error,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = "开始录音",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** 全屏路由版场景选择页 */
@Composable
fun RecordingScenarioRoute(
    onNavigateBack: () -> Unit,
    onStartRecording: (RecordingScenario) -> Unit,
    onNavigateToSchedule: () -> Unit,
    initialScenario: RecordingScenario? = null,
    viewModel: RecordingScenarioViewModel = hiltViewModel(),
) {
    var selected by rememberSaveable {
        mutableStateOf(initialScenario ?: RecordingScenario.QUICK_NOTE)
    }

    Scaffold(
        topBar = {
            RecorderTopAppBar(
                title = "选择录音场景",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val scenarios = RecordingScenario.entries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScenarioGridCard(
                    title = scenarios[0].displayName,
                    durationHint = scenarios[0].durationHint,
                    sliceHint = scenarios[0].sliceHint,
                    icon = scenarios[0].icon(),
                    selected = selected == scenarios[0],
                    onClick = { selected = scenarios[0] },
                    modifier = Modifier.weight(1f),
                )
                ScenarioGridCard(
                    title = scenarios[1].displayName,
                    durationHint = scenarios[1].durationHint,
                    sliceHint = scenarios[1].sliceHint,
                    icon = scenarios[1].icon(),
                    selected = selected == scenarios[1],
                    onClick = { selected = scenarios[1] },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ScenarioGridCard(
                    title = scenarios[2].displayName,
                    durationHint = scenarios[2].durationHint,
                    sliceHint = scenarios[2].sliceHint,
                    icon = scenarios[2].icon(),
                    selected = selected == scenarios[2],
                    onClick = { selected = scenarios[2] },
                    modifier = Modifier.weight(1f),
                )
                ScenarioGridCard(
                    title = scenarios[3].displayName,
                    durationHint = scenarios[3].durationHint,
                    sliceHint = scenarios[3].sliceHint,
                    icon = scenarios[3].icon(),
                    selected = selected == scenarios[3],
                    onClick = { selected = scenarios[3] },
                    modifier = Modifier.weight(1f),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                RecordTriggerButton(
                    onClick = {
                        val scenario = selected ?: return@RecordTriggerButton
                        when (scenario.action) {
                            ScenarioAction.START_MANUAL_RECORDING -> {
                                viewModel.startRecording(scenario)
                                onStartRecording(scenario)
                            }
                            ScenarioAction.NAVIGATE_TO_SCHEDULE -> onNavigateToSchedule()
                        }
                    },
                )
            }
        }
    }
}
