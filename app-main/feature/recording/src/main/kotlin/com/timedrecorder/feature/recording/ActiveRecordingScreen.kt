package com.timedrecorder.feature.recording

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.AudioWaveformBar
import com.timedrecorder.core.model.RecordingState

/**
 * 全屏录音进行页：场景气泡 + 声纹 + 仪表盘 + 底部圆形控制键。
 *
 * 导航设计：
 * - 所有退出路径（收起/保存/放弃/外部结束）统一经过 closeOnce，防止重复 pop。
 * - 收起（最小化）直接 pop 回首页，录音服务在后台继续运行；首页会显示横幅。
 * - 保存/放弃：先触发 Service 操作，然后立即 pop，不等待状态变化。
 * - 外部结束（如任务到期）：LaunchedEffect 检测到会话结束后调用 closeOnce 自动退出。
 */
@Composable
fun ActiveRecordingRoute(
    onNavigateBack: () -> Unit,
    viewModel: ActiveRecordingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var wasActive by remember { mutableStateOf(false) }
    // 用 MutableState 存储 closed 标志，确保 lambda 每次读取都能拿到最新值
    val closedState = remember { mutableStateOf(false) }

    // 单次守卫：所有退出路径都经过这里，避免重复 popBackStack
    val closeOnce: () -> Unit = {
        if (!closedState.value) {
            closedState.value = true
            onNavigateBack()
        }
    }

    // 仅处理"外部结束"场景（如任务到期）：会话从有到无时自动退出
    LaunchedEffect(uiState.isActiveSession) {
        if (uiState.isActiveSession) {
            wasActive = true
        } else if (wasActive) {
            closeOnce()
        }
    }

    ActiveRecordingScreen(
        uiState = uiState,
        onNavigateBack = closeOnce,                                  // 收起：pop 回首页，录音后台继续
        onPause = viewModel::pauseRecording,
        onResume = viewModel::resumeRecording,
        onFinish = { viewModel.finishRecording(); closeOnce() },     // 保存：立即 pop，不等状态
        onRequestCancel = viewModel::requestCancel,
        onDismissCancelDialog = viewModel::dismissCancelDialog,
        onConfirmCancel = { viewModel.confirmCancel(); closeOnce() }, // 放弃：立即 pop，不等状态
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRecordingScreen(
    uiState: ActiveRecordingUiState,
    onNavigateBack: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onRequestCancel: () -> Unit,
    onDismissCancelDialog: () -> Unit,
    onConfirmCancel: () -> Unit,
) {
    if (uiState.showCancelDialog) {
        AlertDialog(
            onDismissRequest = onDismissCancelDialog,
            title = { Text("放弃录音？") },
            text = { Text("确定要放弃当前录音吗？已录制内容将不会保存。") },
            confirmButton = {
                TextButton(onClick = onConfirmCancel) {
                    Text("确定放弃", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCancelDialog) {
                    Text("继续录音")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 顶栏：最小化 + 正在录音指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "最小化")
                }
                RecordingIndicator(
                    isRecording = uiState.recordingState == RecordingState.RECORDING,
                )
                // 占位保持居中
                Box(modifier = Modifier.size(36.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // 场景标签气泡
                uiState.scenario?.let { scenario ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = scenario.displayName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }

                // 降噪状态横幅（固定高度，避免文案切换时挤压下方内容）
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 32.dp)
                        .height(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 大号计时器（固定最小宽度，避免数字变化时布局抖动）
                Text(
                    text = formatElapsedDuration(uiState.elapsedMs),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 200.dp),
                )

                // 声纹动画：固定高度容器，避免条形高度变化引起外层 Center 布局抖动
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AudioWaveformBar(
                        isActive = uiState.recordingState == RecordingState.RECORDING,
                    )
                }

                // 录制参数仪表盘
                RecordingDashboardPanel(
                    elapsedMs = uiState.elapsedMs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 底部控制键：丢弃 | 暂停/继续 | 完成
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                RecordingActionButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "放弃录音",
                    onClick = onRequestCancel,
                )
                RecordingActionButton(
                    icon = if (uiState.recordingState == RecordingState.PAUSED) {
                        Icons.Filled.PlayArrow
                    } else {
                        Icons.Filled.Pause
                    },
                    contentDescription = "暂停/继续",
                    onClick = {
                        when (uiState.recordingState) {
                            RecordingState.RECORDING -> onPause()
                            RecordingState.PAUSED -> onResume()
                            else -> Unit
                        }
                    },
                )
                RecordingActionButton(
                    icon = Icons.Filled.Check,
                    contentDescription = "保存录音",
                    onClick = onFinish,
                    isPrimary = true,
                    iconSize = 28.dp,
                )
            }
        }
    }
}

/** 正在录音红色闪烁指示器 */
@Composable
private fun RecordingIndicator(isRecording: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_dot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot_alpha",
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (isRecording) alpha else 0.5f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
            Text(
                text = if (isRecording) "正在录音" else "录音暂停",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 录制参数仪表盘面板 */
@Composable
private fun RecordingDashboardPanel(
    elapsedMs: Long,
    modifier: Modifier = Modifier,
) {
    val fileSizeLabel = formatRecordingFileSize(elapsedMs)

    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.SettingsVoice,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "系统录制参数状态",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardItem(
                    label = "编码格式",
                    value = "AAC-LC (M4A)",
                    modifier = Modifier.weight(1f),
                )
                DashboardItem(
                    label = "采样率 / 音质",
                    value = "44.1 kHz / 64 kbps",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardItem(
                    label = "当前文件占用",
                    value = fileSizeLabel,
                    modifier = Modifier.weight(1f),
                )
                DashboardItem(
                    label = "本机剩余空间",
                    value = "充足",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DashboardItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 底部圆形操作按钮 */
@Composable
private fun RecordingActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (isPrimary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (isPrimary) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** 根据录音时长估算当前文件大小（默认 64 kbps） */
private fun formatRecordingFileSize(elapsedMs: Long): String {
    val kbPerSec = 64 / 8.0
    val totalKb = (elapsedMs / 1000.0) * kbPerSec
    return if (totalKb < 1024) {
        "%.1f KB".format(totalKb)
    } else {
        "%.2f MB".format(totalKb / 1024)
    }
}

private fun formatElapsedDuration(ms: Long): String {
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
