package com.timedrecorder.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 底部悬浮胶囊导航（M3 FAB 层级）：
 * 半透明白底 + 阴影，录音按钮 primary 填充突出主操作。
 */
@Composable
fun FloatingInputCapsule(
    onScheduleClick: () -> Unit,
    onRecordClick: () -> Unit,
    onFolderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(210.dp)
                .height(60.dp)
                .shadow(elevation = 10.dp, shape = MaterialTheme.shapes.extraLarge, clip = false),
            shape = MaterialTheme.shapes.extraLarge,
            color = Color(0xF2FFFFFF),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onScheduleClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "定时值守",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Surface(
                    onClick = onRecordClick,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 6.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "开始录音",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onFolderClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "本地文件",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
