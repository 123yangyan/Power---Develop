package com.owner.mindbody.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owner.mindbody.ui.components.NarrativeBody
import com.owner.mindbody.ui.components.NarrativeCaption
import com.owner.mindbody.ui.components.NarrativeCard
import com.owner.mindbody.ui.components.PremiumCard
import com.owner.mindbody.ui.components.SectionHeader
import com.owner.mindbody.ui.physio.StateColors
import com.owner.mindbody.ui.theme.CardTitle
import com.owner.mindbody.ui.theme.MindBodyColors
import com.owner.mindbody.ui.theme.MindBodyShapes
import com.owner.mindbody.ui.theme.StatLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    onBack: (() -> Unit)? = null,
    viewModel: TimelineViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MindBodyColors.Background)
    ) {
        if (onBack != null) {
            TopAppBar(
                title = {
                    Text(text = "时间", style = CardTitle)
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
                actions = {
                    IconButton(onClick = { /* 预留：完整日历选择 */ }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "选择日期",
                            tint = MindBodyColors.OnBackgroundSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MindBodyColors.Background,
                    titleContentColor = MindBodyColors.OnBackground
                )
            )
        } else {
            SectionHeader(
                eyebrow = "一天",
                title = "时间",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                trailing = {
                    IconButton(onClick = { /* 预留：完整日历选择 */ }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "选择日期",
                            tint = MindBodyColors.OnBackgroundSecondary
                        )
                    }
                }
            )
        }

        WeekDayStrip(
            weekDates = uiState.weekDates,
            selectedDate = uiState.selectedDate,
            onSelectDate = viewModel::selectDate,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                DateSectionHeader(date = uiState.selectedDate)
                Spacer(modifier = Modifier.height(8.dp))
                DaySummaryRow(summary = uiState.daySummary)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.events.isEmpty()) {
                item {
                    NarrativeCard(
                        accentColor = MindBodyColors.OceanBlue,
                        badgeLabel = "暂无记录"
                    ) {
                        NarrativeBody(
                            text = "这一天还没有训练、心情或身心反馈记录。佩戴 Polar Loop 并持续记录，时间轴会自动填充。"
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            } else {
                items(
                    items = uiState.events,
                    key = { eventKey(it) }
                ) { event ->
                    TimelineEventRow(event = event)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun WeekDayStrip(
    weekDates: List<LocalDate>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekDates.forEach { date ->
            val selected = date == selectedDate
            Column(
                modifier = Modifier
                    .clip(MindBodyShapes.Badge)
                    .clickable { onSelectDate(date) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = weekdayLabel(date),
                    style = StatLabel.copy(
                        fontSize = 11.sp,
                        color = if (selected) MindBodyColors.CalmTeal else MindBodyColors.OnBackgroundSecondary
                    )
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MindBodyColors.CalmTeal else MindBodyColors.StatCellBg
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = StatLabel.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MindBodyColors.CardWhite else MindBodyColors.OnBackground
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSectionHeader(date: LocalDate) {
    val formatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE)
    Text(
        text = date.format(formatter),
        style = CardTitle.copy(fontSize = 18.sp)
    )
}

@Composable
private fun DaySummaryRow(summary: DaySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SummaryChip(
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            value = summary.steps?.toString() ?: "—",
            tint = MindBodyColors.CalmTeal
        )
        SummaryChip(
            icon = Icons.Default.Favorite,
            value = summary.avgHrBpm?.let { "$it" } ?: "—",
            tint = MindBodyColors.HeartRed
        )
        SummaryChip(
            icon = Icons.Default.MonitorHeart,
            value = if (summary.anxietyEventCount > 0) summary.anxietyEventCount.toString() else "—",
            tint = MindBodyColors.AnxietyRose
        )
        SummaryChip(
            icon = Icons.Default.Nightlight,
            value = summary.sleepLabel,
            tint = MindBodyColors.PrimaryIndigo
        )
    }
}

@Composable
private fun SummaryChip(
    icon: ImageVector,
    value: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = StatLabel.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MindBodyColors.OnBackground
            )
        )
    }
}

@Composable
private fun TimelineEventRow(event: TimelineEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TimelineRail(event = event)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTimeRange(event.startMs, event.endMs),
                style = StatLabel.copy(
                    fontSize = 12.sp,
                    color = MindBodyColors.OnBackgroundSecondary
                ),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            when (event) {
                is TimelineEvent.Training -> TrainingEventCard(event)
                is TimelineEvent.Mood -> MoodEventCard(event)
                is TimelineEvent.PhysioFeedback -> PhysioEventCard(event)
            }
        }
    }
}

@Composable
private fun TimelineRail(event: TimelineEvent) {
    val accent = eventAccentColor(event)
    val icon = eventIcon(event)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(28.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(40.dp)
                .background(accent.copy(alpha = 0.25f))
        )
    }
}

@Composable
private fun TrainingEventCard(event: TimelineEvent.Training) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = event.title,
                style = CardTitle.copy(fontSize = 16.sp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricChip(label = event.durationLabel)
                event.avgBpm?.let { MetricChip(label = "$it bpm", tint = MindBodyColors.HeartRed) }
            }
            NarrativeCaption(text = "通过 Polar Loop 记录")
        }
    }
}

@Composable
private fun MoodEventCard(event: TimelineEvent.Mood) {
    PremiumCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = event.roleDisplayName ?: "心情记录",
                style = CardTitle.copy(fontSize = 16.sp)
            )
            event.diaryPreview?.let { diary ->
                Text(
                    text = diary,
                    style = StatLabel.copy(
                        fontSize = 13.sp,
                        color = MindBodyColors.OnBackgroundSecondary,
                        lineHeight = 20.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            event.hrBpm?.let { bpm ->
                MetricChip(label = "$bpm bpm", tint = MindBodyColors.HeartRed)
            }
        }
    }
}

@Composable
private fun PhysioEventCard(event: TimelineEvent.PhysioFeedback) {
    val token = StateColors.of(event.stateLabel)
    NarrativeCard(
        accentColor = token.accentColor,
        badgeLabel = token.zhLabel
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "身心反馈",
                style = CardTitle.copy(fontSize = 15.sp)
            )
            MetricChip(
                label = "焦虑 ${event.anxietyScore.toInt()}",
                tint = token.accentColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = event.message,
            style = StatLabel.copy(
                fontSize = 13.sp,
                color = MindBodyColors.OnBackgroundSecondary,
                lineHeight = 20.sp
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricChip(
    label: String,
    tint: androidx.compose.ui.graphics.Color = MindBodyColors.OnBackgroundSecondary
) {
    Box(
        modifier = Modifier
            .clip(MindBodyShapes.Badge)
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = StatLabel.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = tint
            )
        )
    }
}

private fun eventKey(event: TimelineEvent): String = when (event) {
    is TimelineEvent.Training -> "training-${event.id}"
    is TimelineEvent.Mood -> "mood-${event.id}"
    is TimelineEvent.PhysioFeedback -> "physio-${event.id}"
}

private fun eventAccentColor(event: TimelineEvent): androidx.compose.ui.graphics.Color = when (event) {
    is TimelineEvent.Training -> MindBodyColors.CalmTeal
    is TimelineEvent.Mood -> MindBodyColors.PrimaryIndigo
    is TimelineEvent.PhysioFeedback -> StateColors.of(event.stateLabel).accentColor
}

private fun eventIcon(event: TimelineEvent): ImageVector = when (event) {
    is TimelineEvent.Training -> Icons.AutoMirrored.Filled.DirectionsWalk
    is TimelineEvent.Mood -> Icons.Default.Edit
    is TimelineEvent.PhysioFeedback -> Icons.Default.MonitorHeart
}

private fun weekdayLabel(date: LocalDate): String {
    return when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> ""
    }
}

private fun formatTimeRange(startMs: Long, endMs: Long): String {
    val start = formatClock(startMs)
    val end = formatClock(endMs)
    return if (start == end) start else "$start–$end"
}

private fun formatClock(timestampMs: Long): String {
    val formatter = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(java.util.Date(timestampMs))
}
