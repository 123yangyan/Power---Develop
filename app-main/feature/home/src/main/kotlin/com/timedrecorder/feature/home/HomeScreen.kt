package com.timedrecorder.feature.home

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.EmptyState
import com.timedrecorder.core.designsystem.component.FloatingInputCapsule
import com.timedrecorder.core.designsystem.component.GlobalSearchBar
import com.timedrecorder.core.designsystem.component.StatusPill
import com.timedrecorder.core.designsystem.theme.LocalStatusColors
import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.TimelineItem
import com.timedrecorder.core.model.UploadStatus
import com.timedrecorder.feature.recording.RecordingScenarioBottomSheet
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 / 效率大盘 — 搜索 + 笔记列表 + 悬浮胶囊导航
 */
@Composable
fun HomeRoute(
    onNavigateToNoteDetail: (Long) -> Unit = {},
    onNavigateToResults: () -> Unit = {},
    onNavigateToActiveRecording: () -> Unit = {},
    onNavigateToSchedule: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDiagnostic: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showScenarioSheet by remember { mutableStateOf(false) }
    var preselectedScenario by remember { mutableStateOf<RecordingScenario?>(null) }

    // 导航仅由用户显式操作驱动，不再通过状态反推跳转

    HomeScreen(
        uiState = uiState,
        showScenarioSheet = showScenarioSheet,
        preselectedScenario = preselectedScenario,
        onSearchQueryChange = viewModel::setSearchQuery,
        onOpenScenarioSheet = { scenario ->
            preselectedScenario = scenario
            showScenarioSheet = true
        },
        onDismissScenarioSheet = {
            showScenarioSheet = false
            preselectedScenario = null
        },
        onStartRecording = { scenario ->
            showScenarioSheet = false
            preselectedScenario = null
            viewModel.startManualRecording(scenario)
            onNavigateToActiveRecording()
        },
        onNavigateToSchedule = {
            showScenarioSheet = false
            onNavigateToSchedule()
        },
        onNavigateToNoteDetail = onNavigateToNoteDetail,
        onNavigateToResults = onNavigateToResults,
        onNavigateToMessages = onNavigateToMessages,
        onNavigateToFiles = onNavigateToFiles,
        onNavigateToSettings = onNavigateToSettings,
        onRetryUpload = viewModel::retryUpload,
        onDeleteNote = viewModel::deleteNote,
        onResumeActiveRecording = onNavigateToActiveRecording,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    showScenarioSheet: Boolean,
    preselectedScenario: RecordingScenario?,
    onSearchQueryChange: (String) -> Unit,
    onOpenScenarioSheet: (RecordingScenario?) -> Unit,
    onDismissScenarioSheet: () -> Unit,
    onStartRecording: (RecordingScenario) -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit,
    onNavigateToResults: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRetryUpload: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onResumeActiveRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (showScenarioSheet) {
        RecordingScenarioBottomSheet(
            onDismiss = onDismissScenarioSheet,
            onStartRecording = onStartRecording,
            onNavigateToSchedule = onNavigateToSchedule,
            initialScenario = preselectedScenario,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
        bottomBar = {
            FloatingInputCapsule(
                onScheduleClick = onNavigateToSchedule,
                onRecordClick = { onOpenScenarioSheet(null) },
                onFolderClick = onNavigateToFiles,
            )
        },
    ) { padding ->
        when (uiState) {
            HomeUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is HomeUiState.Error -> {
                Text(
                    text = uiState.message,
                    modifier = Modifier.padding(padding).padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is HomeUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    item(key = "search") {
                        GlobalSearchBar(
                            query = uiState.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onMenuClick = onNavigateToSettings,
                        )
                    }

                    // 录音进行中横幅：收起录音页后可点击重新展开
                    if (uiState.isActiveSession) {
                        item(key = "active_banner") {
                            Surface(
                                onClick = onResumeActiveRecording,
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                    Text(
                                        text = "正在录音：${uiState.currentTaskName ?: ""}  点击返回",
                                        modifier = Modifier.padding(start = 8.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "list_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "笔记列表",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "筛选",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (uiState.todaySchedules.isNotEmpty()) {
                        item(key = "schedule_hint") {
                            TodayScheduleHint(
                                schedules = uiState.todaySchedules,
                                onViewAll = onNavigateToSchedule,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (uiState.timelineItems.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Filled.Notifications,
                                title = "还没有记录",
                                description = "点击下方麦克风，选一个场景开始录音吧",
                                modifier = Modifier.padding(top = 48.dp),
                            )
                        }
                    } else {
                        items(uiState.timelineItems, key = { it.id }) { item ->
                            when (item) {
                                is TimelineItem.ResultEntry -> NoteCard(
                                    item = item,
                                    onClick = { onNavigateToNoteDetail(item.result.fileId) },
                                    onDelete = { onDeleteNote(item.result.fileId) },
                                    onShare = {
                                        val shareText = buildString {
                                            item.result.title?.let { appendLine(it) }
                                            item.result.summary?.let { append(it) }
                                        }.trim()
                                        if (shareText.isNotEmpty()) {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(intent, "分享笔记"),
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                is TimelineItem.UploadEntry -> TimelineRowCard(
                                    item = item,
                                    onRetryUpload = { onRetryUpload(item.file.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                is TimelineItem.MessageEntry -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 今日计划提示条 */
@Composable
private fun TodayScheduleHint(
    schedules: List<com.timedrecorder.core.model.ScheduleTask>,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Schedule, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text("今日 ${schedules.size} 个值守任务", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = schedules.take(2).joinToString(" · ") {
                        "${it.formatStartTime()}-${it.formatEndTime()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onViewAll) { Text("查看") }
        }
    }
}

/**
 * 富媒体笔记卡片：标题 + 摘要预览 + 音频时长胶囊 + 页脚菜单。
 */
@Composable
private fun NoteCard(
    item: TimelineItem.ResultEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 优先使用云端返回的 title 字段
    val title = item.result.title
        ?: item.result.summary?.lines()?.firstOrNull()
        ?: "处理中…"
    val preview = item.result.summary ?: "正在生成智能摘要，请稍候…"
    val updateLabel = formatUpdateDate(item.timestamp)
    val durationLabel = formatAudioDuration(item.audioDuration)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )

            AudioPill(
                durationLabel = durationLabel,
                audioFilePath = item.audioFilePath,
                modifier = Modifier.padding(top = 16.dp),
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = updateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NoteCardMenu(
                    onDelete = onDelete,
                    onShare = onShare,
                )
            }
        }
    }
}

/** 笔记卡片右下角三点菜单 */
@Composable
private fun NoteCardMenu(
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
            )
            DropdownMenuItem(
                text = { Text("分享") },
                onClick = {
                    showMenu = false
                    onShare()
                },
            )
        }
    }
}

/** 音频播放胶囊：点击播放图标直接播放，不跳转详情页 */
@Composable
private fun AudioPill(
    durationLabel: String,
    audioFilePath: String?,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                mediaPlayer.release()
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(
                onClick = {
                    val path = audioFilePath
                    if (path.isNullOrBlank() || !File(path).exists()) return@IconButton
                    if (isPlaying) {
                        runCatching {
                            mediaPlayer.pause()
                            isPlaying = false
                        }
                    } else {
                        runCatching {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(path)
                            mediaPlayer.prepare()
                            mediaPlayer.setOnCompletionListener { isPlaying = false }
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    }
                },
                enabled = !audioFilePath.isNullOrBlank(),
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = durationLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** 上传失败条目：紧凑行卡片 + 重新上传按钮 */
@Composable
private fun TimelineRowCard(
    item: TimelineItem.UploadEntry,
    onRetryUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeLabel = formatUpdateDate(item.timestamp)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null)
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = item.file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall)
                    if (item.file.uploadStatus == UploadStatus.FAILED) {
                        TextButton(onClick = onRetryUpload) {
                            Text("重新上传")
                        }
                    }
                }
            }
            UploadStatusPill(item.file.uploadStatus)
        }
    }
}

@Composable
private fun UploadStatusPill(status: UploadStatus) {
    val statusColors = LocalStatusColors.current
    val (container, content, label) = when (status) {
        UploadStatus.SUCCESS -> Triple(statusColors.successContainer, statusColors.success, "已上传")
        UploadStatus.FAILED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "失败")
        UploadStatus.UPLOADING -> Triple(statusColors.infoContainer, statusColors.info, "上传中")
        UploadStatus.RETRYING -> Triple(statusColors.warningContainer, statusColors.warning, "重试中")
        UploadStatus.PENDING -> Triple(statusColors.warningContainer, statusColors.warning, "待上传")
    }
    StatusPill(text = label, containerColor = container, contentColor = content)
}

private fun formatUpdateDate(timestamp: Long): String {
    val fmt = SimpleDateFormat("M月d日 H:mm", Locale.CHINESE)
    return "${fmt.format(Date(timestamp))}更新"
}

private fun formatAudioDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0) return "—"
    val totalSec = durationMs / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
