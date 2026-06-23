package com.owner.mindbody.ui.physio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors

/**
 * LLM 反馈历史列表页（非 Tab 路由，从「状态」Tab 进入）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackHistoryListScreen(
    onBack: () -> Unit,
    onNavigateToMoodRecord: () -> Unit,
    viewModel: PhysioStateViewModel = viewModel()
) {
    val feedbackList by viewModel.feedbackHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "反馈历史",
                    style = CardTitle
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MindBodyColors.OnBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MindBodyColors.Background,
                titleContentColor = MindBodyColors.OnBackground
            )
        )

        if (feedbackList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                NarrativeCard(
                    accentColor = MindBodyColors.OceanBlue,
                    badgeLabel = "暂无记录"
                ) {
                    NarrativeBody(
                        text = "还没有 LLM 反馈历史。请保持 Polar Loop 佩戴，等待基线建立后系统会自动生成分析反馈。"
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(
                    items = feedbackList,
                    key = { it.id }
                ) { entry ->
                    FeedbackHistoryCard(
                        entry = entry,
                        onRecordNow = if (entry.userResponse == null) onNavigateToMoodRecord else null
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
