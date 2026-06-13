package com.timedrecorder.feature.notedetail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.StatusPill
import com.timedrecorder.core.designsystem.theme.LocalStatusColors
import com.timedrecorder.core.model.ProcessResult
import com.timedrecorder.core.model.RiskLevel
import com.timedrecorder.feature.files.PlayerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 录音内容详情页 — 播放器 + 录音原文/录音总结 Tab */
@Composable
fun NoteDetailRoute(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    NoteDetailScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSelectTab = viewModel::selectTab,
        onTogglePlayback = viewModel::togglePlayback,
        onSeekTo = viewModel::seekTo,
        onSkipForward = viewModel::skipForward,
        onSkipBackward = viewModel::skipBackward,
        onToggleSpeed = viewModel::togglePlaybackSpeed,
        onShare = {
            viewModel.buildShareIntent()?.let { intent ->
                context.startActivity(android.content.Intent.createChooser(intent, "分享音频文件"))
            } ?: Toast.makeText(context, "文件不可用", Toast.LENGTH_SHORT).show()
        },
        modifier = modifier.fillMaxSize(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    uiState: NoteDetailUiState,
    onNavigateBack: () -> Unit,
    onSelectTab: (NoteDetailTab) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleSpeed: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                RecorderTopAppBar(
                    title = "录音详情",
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Filled.Share, contentDescription = "分享")
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        when (uiState) {
            NoteDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is NoteDetailUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "加载失败",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp).padding(top = 4.dp),
                        )
                    }
                }
            }
            is NoteDetailUiState.Success -> {
                // AI 短标题优先，旧数据回退到摘要首行或文件名
                val displayTitle = uiState.result?.title
                    ?: uiState.result?.summary?.lines()?.firstOrNull()
                    ?: uiState.audioFile.fileName
                val tabs = listOf(
                    NoteDetailTab.TRANSCRIPT to "录音原文",
                    NoteDetailTab.SUMMARY to "录音总结",
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    Text(
                        text = "创建时间 ${formatDateTime(uiState.audioFile.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    TagRow(
                        keywords = uiState.result?.keywords.orEmpty(),
                        riskLevel = uiState.result?.riskLevel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    if (uiState.fileExists) {
                        AudioPlayerCard(
                            isPlaying = uiState.playerState is PlayerState.Playing,
                            isPreparing = uiState.playerState is PlayerState.Preparing,
                            progressMs = uiState.progressMs,
                            durationMs = uiState.durationMs,
                            playbackSpeed = uiState.playbackSpeed,
                            onTogglePlayback = onTogglePlayback,
                            onSeekTo = onSeekTo,
                            onSkipForward = onSkipForward,
                            onSkipBackward = onSkipBackward,
                            onToggleSpeed = onToggleSpeed,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "本地音频文件不可用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    TabRow(
                        selectedTabIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }.coerceAtLeast(0),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        tabs.forEach { (tab, label) ->
                            Tab(
                                selected = uiState.selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                text = {
                                    Text(
                                        text = label,
                                        fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when (uiState.selectedTab) {
                            NoteDetailTab.TRANSCRIPT -> TranscriptContent(
                                transcript = uiState.result?.transcript,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                            NoteDetailTab.SUMMARY -> SummaryContent(
                                result = uiState.result,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(
    keywords: List<String>,
    riskLevel: RiskLevel?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = {},
            label = { Text("+") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        AssistChip(onClick = {}, label = { Text("录音笔记") })
        keywords.forEach { keyword ->
            AssistChip(onClick = {}, label = { Text(keyword) })
        }
        riskLevel?.let { RiskPill(it) }
    }
}

@Composable
private fun AudioPlayerCard(
    isPlaying: Boolean,
    isPreparing: Boolean,
    progressMs: Long,
    durationMs: Long,
    playbackSpeed: Float,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onToggleSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDurationHms(progressMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatDurationHms(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = if (durationMs > 0) progressMs.toFloat() / durationMs else 0f,
                onValueChange = { ratio -> onSeekTo((ratio * durationMs).toLong()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onSkipBackward) {
                    Icon(Icons.Filled.Replay, contentDescription = "后退15秒")
                }
                if (isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else {
                    IconButton(
                        onClick = onTogglePlayback,
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(50),
                            ),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                IconButton(onClick = onSkipForward) {
                    Icon(Icons.Filled.Forward30, contentDescription = "前进15秒")
                }
                TextButton(onClick = onToggleSpeed) {
                    Text("%.1fx".format(playbackSpeed))
                }
            }
        }
    }
}

@Composable
private fun TranscriptContent(
    transcript: String?,
    modifier: Modifier = Modifier,
) {
    if (transcript.isNullOrBlank()) {
        PlaceholderCard(
            title = "暂无转写内容",
            description = "新录音处理完成后将自动显示。\n旧录音需重新上传处理才会有转写。",
            modifier = modifier,
        )
    } else {
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )
    }
}

@Composable
private fun SummaryContent(
    result: ProcessResult?,
    modifier: Modifier = Modifier,
) {
    if (result == null) {
        PlaceholderCard(
            title = "总结生成中",
            description = "云端正在处理这条录音，完成后会自动显示 AI 总结。",
            modifier = modifier,
            showProgress = true,
        )
        return
    }
    Column(modifier = modifier) {
        Text(
            text = result.summary ?: "暂无总结",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (result.keywords.isNotEmpty()) {
            Text(
                text = "关键词：${result.keywords.joinToString("、")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        result.riskLevel?.let { level ->
            Row(modifier = Modifier.padding(top = 8.dp)) {
                RiskPill(level)
            }
        }
    }
}

/** 轻量占位卡片 */
@Composable
private fun PlaceholderCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = if (showProgress) 12.dp else 0.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RiskPill(level: RiskLevel) {
    val c = LocalStatusColors.current
    val (container, content, label) = when (level) {
        RiskLevel.HIGH -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, "高风险")
        RiskLevel.MEDIUM -> Triple(c.warningContainer, c.warning, "中风险")
        RiskLevel.LOW -> Triple(c.successContainer, c.success, "低风险")
    }
    StatusPill(text = label, containerColor = container, contentColor = content)
}

private fun formatDateTime(timestamp: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

private fun formatDurationHms(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
