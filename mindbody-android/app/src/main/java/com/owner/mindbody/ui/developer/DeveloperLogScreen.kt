package com.owner.mindbody.ui.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import com.owner.mindbody.util.LogLevel

@Composable
fun DeveloperLogScreen(
    onBack: () -> Unit,
    viewModel: DeveloperLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    val autoScroll by viewModel.autoScrollToBottom.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    LaunchedEffect(entries.size, autoScroll) {
        if (autoScroll && entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
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
                    title = "运行日志",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.copyAll(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MindBodyColors.PrimaryIndigo),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("复制全部")
                }
                OutlinedButton(
                    onClick = { viewModel.clearLogs() },
                    modifier = Modifier.weight(1f),
                    shape = MindBodyShapes.RadioOption
                ) {
                    Text("清空")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "自动滚到底部", style = CardTitle)
                Switch(
                    checked = autoScroll,
                    onCheckedChange = viewModel::setAutoScrollToBottom
                )
            }

            Text(
                text = "共 ${entries.size} 条 · 长按可选中复制片段",
                style = StatLabel
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MindBodyColors.StatCellBg, MindBodyShapes.StatCell)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = "暂无日志。启动 App 或连接手环后此处会显示运行记录。",
                            style = StatLabel,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        SelectionContainer {
                            Text(
                                text = entry.formatLine(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = levelColor(entry.level),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: LogLevel): androidx.compose.ui.graphics.Color {
    return when (level) {
        LogLevel.DEBUG -> MindBodyColors.OnBackgroundSecondary
        LogLevel.INFO -> MindBodyColors.OnBackground
        LogLevel.WARN -> MindBodyColors.Amber
        LogLevel.ERROR -> MindBodyColors.HeartRed
    }
}
