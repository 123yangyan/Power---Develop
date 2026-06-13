package com.timedrecorder.feature.messages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timedrecorder.core.designsystem.component.EmptyState
import com.timedrecorder.core.designsystem.component.RecorderTopAppBar
import com.timedrecorder.core.model.MessageItem
import com.timedrecorder.core.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 消息中心 — PRD §9.6 */
@Composable
fun MessagesRoute(
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MessagesScreen(
        uiState = uiState,
        onToggleFilter = viewModel::toggleAlertsOnly,
        onMarkRead = viewModel::markAsRead,
        onMarkAllRead = viewModel::markAllAsRead,
        modifier = modifier,
    )
}

@Composable
fun MessagesScreen(
    uiState: MessagesUiState,
    onToggleFilter: () -> Unit,
    onMarkRead: (Long) -> Unit,
    onMarkAllRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = { RecorderTopAppBar(title = "消息中心") },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { padding ->
        when (uiState) {
            MessagesUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MessagesUiState.Error -> Text(uiState.message, modifier = Modifier.padding(padding).padding(16.dp), color = MaterialTheme.colorScheme.error)
            is MessagesUiState.Success -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = uiState.showAlertsOnly,
                            onClick = onToggleFilter,
                            label = { Text("仅看异常") },
                        )
                        TextButton(onClick = onMarkAllRead) {
                            Icon(Icons.Filled.MarkEmailRead, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("全部已读 (${uiState.unreadCount})")
                        }
                    }
                    if (uiState.messages.isEmpty()) {
                        EmptyState(
                            icon = Icons.Filled.NotificationsNone,
                            title = "暂无消息",
                            description = "识别到关键词或异常时，提醒会出现在这里",
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.messages, key = { it.id }) { msg ->
                                MessageItemCard(msg = msg, onClick = { onMarkRead(msg.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItemCard(msg: MessageItem, onClick: () -> Unit) {
    val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val isAlert = msg.type == MessageType.ALERT
    // 异常消息用红色图标，普通消息用主题色
    val accent = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = accent,
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = msg.title,
                    style = MaterialTheme.typography.titleMedium,
                    // 未读加粗，让用户一眼分辨
                    fontWeight = if (!msg.isRead) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = dateFmt.format(Date(msg.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // 未读小圆点
            if (!msg.isRead) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}
