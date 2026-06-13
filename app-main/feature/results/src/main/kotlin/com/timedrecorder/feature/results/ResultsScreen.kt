package com.timedrecorder.feature.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.EmptyState
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.designsystem.component.StatusPill
import com.timedrecorder.core.designsystem.theme.LocalStatusColors
import com.timedrecorder.core.model.ProcessResult
import com.timedrecorder.core.model.RiskLevel

/** 云端处理结果列表 — PRD §9.5，T2/T5 */
@Composable
fun ResultsRoute(
    onNavigateBack: () -> Unit = {},
    onNavigateToNoteDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ResultsScreen(
        uiState = uiState,
        onQueryChange = viewModel::setQuery,
        onNavigateBack = onNavigateBack,
        onNavigateToNoteDetail = onNavigateToNoteDetail,
        modifier = modifier,
    )
}

@Composable
fun ResultsScreen(
    uiState: ResultsUiState,
    onQueryChange: (String) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToNoteDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            RecorderTopAppBar(
                title = "处理结果",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        when (uiState) {
            ResultsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ResultsUiState.Error -> Text(
                uiState.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            is ResultsUiState.Success -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // T5：搜索框
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text("搜索关键词或摘要…") },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清空搜索")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    // 结果列表
                    if (uiState.filteredResults.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.Assessment,
                            title = if (uiState.query.isNotEmpty()) "无匹配结果" else "暂无处理结果",
                            description = if (uiState.query.isNotEmpty())
                                "换个关键词试试" else "云端处理完成后，摘要与识别结果会显示在这里",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.filteredResults, key = { it.id }) { result ->
                                ResultItem(
                                    result = result,
                                    onClick = { onNavigateToNoteDetail(result.fileId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultItem(
    result: ProcessResult,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "处理结果",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                result.riskLevel?.let { RiskPill(it) }
            }
            Text(
                text = result.summary ?: "无摘要",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (result.keywords.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.keywords.forEach { keyword ->
                        AssistChip(onClick = {}, label = { Text(keyword) })
                    }
                }
            }
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
