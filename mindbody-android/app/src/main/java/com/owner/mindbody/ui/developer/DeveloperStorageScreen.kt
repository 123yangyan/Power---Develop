package com.owner.mindbody.ui.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.data.StorageTableCategory
import com.owner.mindbody.data.TableStorageStat
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.ui.theme.StatValue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DeveloperStorageScreen(
    onBack: () -> Unit,
    viewModel: DeveloperStorageViewModel = viewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val lastRefreshedAtMs by viewModel.lastRefreshedAtMs.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MindBodyColors.Background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MindBodyColors.OnBackground
                    )
                }
                SectionHeader(
                    eyebrow = "DEVELOPER",
                    title = "底层 storage 看板",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "统计前先 flush 缓冲，数字反映已落盘到 Room 的数据。连接手环后点刷新即可观察增长。",
                style = StatLabel
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.refresh() },
                    enabled = !isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                    shape = MindBodyShapes.RadioOption
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    Text(if (isLoading) "刷新中…" else "刷新统计")
                }
            }

            SummaryCard(
                totalRows = stats.sumOf { it.rowCount },
                tableCount = stats.size,
                lastRefreshedAtMs = lastRefreshedAtMs
            )

            StorageCategorySection(
                title = StorageTableCategory.LIVE_STREAM.label,
                stats = stats.filter { it.category == StorageTableCategory.LIVE_STREAM }
            )
            StorageCategorySection(
                title = StorageTableCategory.OFFLINE_SYNC.label,
                stats = stats.filter { it.category == StorageTableCategory.OFFLINE_SYNC }
            )
            StorageCategorySection(
                title = StorageTableCategory.BUSINESS.label,
                stats = stats.filter { it.category == StorageTableCategory.BUSINESS }
            )
        }
    }
}

@Composable
private fun SummaryCard(
    totalRows: Long,
    tableCount: Int,
    lastRefreshedAtMs: Long?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.StatCell)
            .background(MindBodyColors.StatCellBg)
            .border(1.dp, MindBodyColors.PrimaryIndigo.copy(alpha = 0.12f), MindBodyShapes.StatCell)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = "汇总", style = CardTitle)
        Text(text = "总记录数：$totalRows", style = StatValue)
        Text(text = "已统计表：$tableCount 张", style = StatLabel)
        Text(
            text = "上次刷新：${formatTimestamp(lastRefreshedAtMs)}",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary)
        )
    }
}

@Composable
private fun StorageCategorySection(
    title: String,
    stats: List<TableStorageStat>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = CardTitle)
        stats.forEach { stat ->
            StorageTableRow(stat = stat)
        }
    }
}

@Composable
private fun StorageTableRow(stat: TableStorageStat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MindBodyShapes.StatCell)
            .background(MindBodyColors.StatCellBg)
            .border(1.dp, MindBodyColors.StatCellBorder, MindBodyShapes.StatCell)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stat.displayName, style = StatValue.copy(fontSize = CardTitle.fontSize))
            Text(
                text = stat.rowCount.toString(),
                style = StatValue.copy(
                    color = if (stat.rowCount > 0) MindBodyColors.Emerald else MindBodyColors.OnBackgroundSecondary
                )
            )
        }
        Text(
            text = stat.tableName,
            style = StatLabel.copy(fontFamily = FontFamily.Monospace)
        )
        Text(
            text = "最近更新：${formatTimestamp(stat.lastUpdatedMs)}",
            style = StatLabel.copy(color = MindBodyColors.OnBackgroundSecondary)
        )
    }
}

private val refreshTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatTimestamp(timestampMs: Long?): String {
    if (timestampMs == null) return "--"
    val time = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return time.format(refreshTimeFormatter)
}
