package com.timedrecorder.feature.files

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.EmptyState
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.StatusPill
import com.timedrecorder.core.designsystem.theme.LocalStatusColors
import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 本地文件 + 上传状态 — PRD §9.7、§9.8 */
@Composable
fun FilesRoute(
    onNavigateToNoteDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressMs by viewModel.progressMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var fileToDelete by remember { mutableStateOf<AudioFile?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.retryFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteFeedback.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("删除录音") },
            text = {
                Text("确定删除「${file.fileName}」？\n本地文件和摘要将一并清除，且无法恢复。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFile(file.id)
                        fileToDelete = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    FilesScreen(
        uiState = uiState,
        progressMs = progressMs,
        durationMs = durationMs,
        onFilterChange = viewModel::setFilter,
        onRetryUpload = viewModel::retryUpload,
        onRefreshResult = viewModel::refreshResult,
        onRetryAll = viewModel::retryAllFailed,
        onToggleExpanded = viewModel::toggleExpanded,
        onTogglePlayback = { file -> viewModel.togglePlayback(file.id, file.filePath) },
        onSeekTo = viewModel::seekTo,
        onNavigateToNoteDetail = onNavigateToNoteDetail,
        onShare = { filePath ->
            viewModel.buildShareIntent(filePath)?.let { intent ->
                context.startActivity(Intent.createChooser(intent, "分享音频文件"))
            }
        },
        onRequestDelete = { file -> fileToDelete = file },
        modifier = modifier,
    )
}

@Composable
fun FilesScreen(
    uiState: FilesUiState,
    progressMs: Long,
    durationMs: Long,
    onFilterChange: (UploadStatus?) -> Unit,
    onRetryUpload: (Long) -> Unit,
    onRefreshResult: (Long) -> Unit,
    onRetryAll: () -> Unit,
    onToggleExpanded: (Long) -> Unit,
    onTogglePlayback: (AudioFile) -> Unit,
    onSeekTo: (Long) -> Unit,
    onNavigateToNoteDetail: (Long) -> Unit,
    onShare: (String) -> Unit,
    onRequestDelete: (AudioFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { RecorderTopAppBar(title = "本地文件") },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        when (uiState) {
            FilesUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is FilesUiState.Error -> {
                Text(
                    uiState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(padding).padding(16.dp),
                )
            }
            is FilesUiState.Success -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // T1：失败文件数 > 1 时，顶部显示批量重试 Banner
                    AnimatedVisibility(
                        visible = uiState.failedCount > 1,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        FailedBanner(
                            failedCount = uiState.failedCount,
                            onRetryAll = onRetryAll,
                        )
                    }

                    // 上传状态筛选条
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = uiState.filter == null, onClick = { onFilterChange(null) }, label = { Text("全部") })
                        FilterChip(selected = uiState.filter == UploadStatus.SUCCESS, onClick = { onFilterChange(UploadStatus.SUCCESS) }, label = { Text("成功") })
                        FilterChip(selected = uiState.filter == UploadStatus.PENDING, onClick = { onFilterChange(UploadStatus.PENDING) }, label = { Text("待上传") })
                        FilterChip(selected = uiState.filter == UploadStatus.FAILED, onClick = { onFilterChange(UploadStatus.FAILED) }, label = { Text("失败") })
                    }

                    if (uiState.files.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Inbox,
                            title = "暂无录音文件",
                            description = "录音切片完成后会出现在这里",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.files, key = { it.id }) { file ->
                                FileItem(
                                    file = file,
                                    isRefreshing = file.id in uiState.refreshingFileIds,
                                    isPlaying = uiState.playerState is PlayerState.Playing && uiState.playingFileId == file.id,
                                    isPreparing = uiState.playerState is PlayerState.Preparing && uiState.playingFileId == file.id,
                                    isExpanded = uiState.expandedFileId == file.id,
                                    progressMs = if (uiState.playingFileId == file.id) progressMs else 0L,
                                    durationMs = if (uiState.playingFileId == file.id) durationMs else 0L,
                                    onRetry = { onRetryUpload(file.id) },
                                    onRefreshResult = { onRefreshResult(file.id) },
                                    onToggleExpanded = { onToggleExpanded(file.id) },
                                    onTogglePlayback = { onTogglePlayback(file) },
                                    onSeekTo = onSeekTo,
                                    onNavigateToNoteDetail = if (file.processStatus == ProcessStatus.COMPLETED) {
                                        { onNavigateToNoteDetail(file.id) }
                                    } else {
                                        null
                                    },
                                    onShare = { onShare(file.filePath) },
                                    onRequestDelete = { onRequestDelete(file) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * T1：顶部批量重试 Banner，当失败/待上传文件数 > 1 时显示。
 */
@Composable
private fun FailedBanner(failedCount: Int, onRetryAll: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "有 $failedCount 个文件上传失败",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRetryAll) {
                Text("全部重试")
            }
        }
    }
}

@Composable
private fun FileItem(
    file: AudioFile,
    isRefreshing: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    isExpanded: Boolean,
    progressMs: Long,
    durationMs: Long,
    onRetry: () -> Unit,
    onRefreshResult: () -> Unit,
    onToggleExpanded: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNavigateToNoteDetail: (() -> Unit)?,
    onShare: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val fileExists = java.io.File(file.filePath).exists()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            // 标题行：音频图标 + 文件名 + 分享图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = buildString {
                        append(file.fileName)
                        if (file.isManualRecording) append("（手动）")
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                // 删除按钮
                IconButton(onClick = onRequestDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                // T9：分享按钮（文件存在时才可用）
                IconButton(
                    onClick = onShare,
                    enabled = fileExists,
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "分享",
                        tint = if (fileExists) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                }
            }

            Text(
                text = "${dateFmt.format(Date(file.startAt))} · ${file.fileSize / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            // 状态徽章行
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UploadStatusPill(file.uploadStatus)
                ProcessStatusPill(file.processStatus)
            }

            // 操作按钮行
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 待上传/失败/卡死上传中时显示手动重传
                if (file.uploadStatus.canManualRetry()) {
                    OutlinedButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("手动重传")
                    }
                }

                // 已上传但结果未到时，可手动拉取云端摘要
                if (
                    file.uploadStatus == UploadStatus.SUCCESS &&
                    file.processStatus != ProcessStatus.COMPLETED &&
                    !file.serverFileId.isNullOrBlank()
                ) {
                    OutlinedButton(
                        onClick = onRefreshResult,
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp).padding(end = 4.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        }
                        Text(if (isRefreshing) "拉取中…" else "拉取结果")
                    }
                }

                // T2：处理完成时显示查看结果按钮
                if (onNavigateToNoteDetail != null) {
                    OutlinedButton(onClick = onNavigateToNoteDetail) {
                        Text("查看结果")
                    }
                }
            }

            // T3：展开时显示播放条；折叠时显示「播放」按钮
            if (fileExists) {
                AnimatedVisibility(visible = isExpanded) {
                    PlaybackBar(
                        isPlaying = isPlaying,
                        isPreparing = isPreparing,
                        progressMs = progressMs,
                        durationMs = durationMs,
                        onTogglePlayback = onTogglePlayback,
                        onSeekTo = onSeekTo,
                    )
                }
                if (!isExpanded) {
                    OutlinedButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("播放")
                    }
                }
            } else {
                Text(
                    text = "文件已清理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * T3：播放进度条组件。
 * 包含：播放/暂停按钮、当前时间/总时长文字、可拖拽进度滑条。
 */
@Composable
private fun PlaybackBar(
    isPlaying: Boolean,
    isPreparing: Boolean,
    progressMs: Long,
    durationMs: Long,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 播放/暂停按钮
            if (isPreparing) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            } else {
                IconButton(onClick = onTogglePlayback) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // 当前时间
            Text(
                text = formatDuration(progressMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 进度滑条（可拖拽）
            Slider(
                value = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f,
                onValueChange = { ratio ->
                    onSeekTo((ratio * durationMs).toLong())
                },
                modifier = Modifier.weight(1f),
            )

            // 总时长
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 将毫秒格式化为 mm:ss 字符串 */
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun UploadStatusPill(status: UploadStatus) {
    val c = LocalStatusColors.current
    val (container, content, label) = when (status) {
        UploadStatus.SUCCESS -> Triple(c.successContainer, c.success, "上传成功")
        UploadStatus.FAILED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "上传失败")
        UploadStatus.UPLOADING -> Triple(c.infoContainer, c.info, "上传中")
        UploadStatus.RETRYING -> Triple(c.warningContainer, c.warning, "重试中")
        UploadStatus.PENDING -> Triple(c.warningContainer, c.warning, "待上传")
    }
    StatusPill(text = label, containerColor = container, contentColor = content)
}

@Composable
private fun ProcessStatusPill(status: ProcessStatus) {
    val c = LocalStatusColors.current
    val (container, content, label) = when (status) {
        ProcessStatus.COMPLETED -> Triple(c.successContainer, c.success, "已处理")
        ProcessStatus.FAILED -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "处理失败")
        ProcessStatus.PROCESSING -> Triple(c.infoContainer, c.info, "处理中")
        ProcessStatus.PENDING -> Triple(c.warningContainer, c.warning, "待处理")
    }
    StatusPill(text = label, containerColor = container, contentColor = content)
}

/** 是否可手动重传（含卡死的 UPLOADING 状态） */
private fun UploadStatus.canManualRetry(): Boolean = when (this) {
    UploadStatus.PENDING,
    UploadStatus.FAILED,
    UploadStatus.RETRYING,
    UploadStatus.UPLOADING,
    -> true
    else -> false
}
