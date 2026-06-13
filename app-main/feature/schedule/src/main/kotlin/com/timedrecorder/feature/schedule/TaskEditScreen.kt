package com.timedrecorder.feature.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.SectionHeader

/** 新增/编辑任务页 — PRD §9.2 */
@Composable
fun TaskEditRoute(
    taskId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(taskId) {
        if (taskId != null && taskId > 0) viewModel.loadTask(taskId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        is TaskEditUiState.Editing -> TaskEditScreen(
            state = state,
            onNameChange = viewModel::updateTaskName,
            onStartHourChange = { h, m -> viewModel.updateStartTime(h, m) },
            onEndHourChange = { h, m -> viewModel.updateEndTime(h, m) },
            onSave = { viewModel.save(onNavigateBack) },
        )
    }
}

@Composable
fun TaskEditScreen(
    state: TaskEditUiState.Editing,
    onNameChange: (String) -> Unit,
    onStartHourChange: (Int, Int) -> Unit,
    onEndHourChange: (Int, Int) -> Unit,
    onSave: () -> Unit,
) {
    val task = state.task
    val startH = task.startTimeMinutes / 60
    val startM = task.startTimeMinutes % 60
    val endH = task.endTimeMinutes / 60
    val endM = task.endTimeMinutes % 60

    Scaffold(
        topBar = { RecorderTopAppBar(title = if (state.isNew) "新增任务" else "编辑任务") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 任务名称
            SectionHeader("任务信息")
            EditCard {
                OutlinedTextField(
                    value = task.taskName.orEmpty(),
                    onValueChange = onNameChange,
                    label = { Text("任务名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 时间段：开始 / 结束 各一行，时:分并排
            SectionHeader("录音时间段")
            EditCard {
                TimeRow(
                    label = "开始时间",
                    hour = startH,
                    minute = startM,
                    onHourChange = { onStartHourChange(it, startM) },
                    onMinuteChange = { onStartHourChange(startH, it) },
                )
                TimeRow(
                    label = "结束时间",
                    hour = endH,
                    minute = endM,
                    onHourChange = { onEndHourChange(it, endM) },
                    onMinuteChange = { onEndHourChange(endH, it) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            state.errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }

            // 保存（主操作）
            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("保存")
                }
            }
        }
    }
}

/** 编辑分组卡片 */
@Composable
private fun EditCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

/** 一行时间输入：标题 + 小时框 : 分钟框 */
@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OutlinedTextField(
                value = "$hour",
                onValueChange = { onHourChange(it.toIntOrNull()?.coerceIn(0, 23) ?: 0) },
                label = { Text("时") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
            Text(
                text = " : ",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedTextField(
                value = "$minute",
                onValueChange = { onMinuteChange(it.toIntOrNull()?.coerceIn(0, 59) ?: 0) },
                label = { Text("分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
        }
    }
}
