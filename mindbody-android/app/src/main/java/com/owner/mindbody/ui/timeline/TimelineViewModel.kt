package com.owner.mindbody.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owner.mindbody.MindBodyApplication
import com.owner.mindbody.data.FeedbackHistoryCodec
import com.owner.mindbody.data.LlmFeedbackEntry
import com.owner.mindbody.data.local.Hr247SampleEntity
import com.owner.mindbody.data.local.HrSampleEntity
import com.owner.mindbody.data.local.MoodEntryEntity
import com.owner.mindbody.data.local.TrainingSessionEntity
import com.owner.mindbody.ui.mood.EmotionRoles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DaySummary(
    val steps: Int?,
    val avgHrBpm: Int?,
    val sleepLabel: String = "—",
    val anxietyEventCount: Int = 0
)

sealed class TimelineEvent {
    abstract val sortKeyMs: Long
    abstract val startMs: Long
    abstract val endMs: Long

    data class Training(
        val id: String,
        override val startMs: Long,
        override val endMs: Long,
        val title: String,
        val durationLabel: String,
        val avgBpm: Int?
    ) : TimelineEvent() {
        override val sortKeyMs: Long = startMs
    }

    data class Mood(
        val id: Long,
        override val startMs: Long,
        val roleDisplayName: String?,
        val diaryPreview: String?,
        val hrBpm: Int?
    ) : TimelineEvent() {
        override val endMs: Long = startMs
        override val sortKeyMs: Long = startMs
    }

    data class PhysioFeedback(
        val id: Long,
        override val startMs: Long,
        val stateLabel: String,
        val anxietyScore: Float,
        val message: String
    ) : TimelineEvent() {
        override val endMs: Long = startMs
        override val sortKeyMs: Long = startMs
    }
}

data class TimelineUiState(
    val selectedDate: LocalDate,
    val weekDates: List<LocalDate>,
    val daySummary: DaySummary,
    val events: List<TimelineEvent>
) {
    companion object {
        fun initial(zoneId: ZoneId): TimelineUiState {
            val today = LocalDate.now(zoneId)
            return TimelineUiState(
                selectedDate = today,
                weekDates = TimelineViewModel.weekDatesFor(today),
                daySummary = DaySummary(steps = null, avgHrBpm = null),
                events = emptyList()
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = (application as MindBodyApplication).storage
    private val zoneId = ZoneId.systemDefault()
    private val httpClient = OkHttpClient()

    private val _selectedDate = MutableStateFlow(LocalDate.now(zoneId))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    init {
        viewModelScope.launch { refreshFeedbackFromServer() }
    }

    val uiState: StateFlow<TimelineUiState> = _selectedDate
        .flatMapLatest { date -> observeDayState(date) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimelineUiState.initial(zoneId)
        )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    private fun observeDayState(date: LocalDate): Flow<TimelineUiState> {
        val (startMs, endMs) = dateRangeMs(date)
        val sessionDate = date.toString()
        val dayDataFlow = combine(
            storage.training.observeSessionsForDay(startMs, endMs, sessionDate, startMs),
            storage.mood.observeAll(),
            storage.feedbackHistory,
            storage.activityMinute.observeBetween(startMs, endMs),
        ) { training, allMoods, allFeedback, activity ->
            DayDataSlice(training, allMoods, allFeedback, activity)
        }
        val hrFlow = combine(
            storage.hr247.observeBetween(startMs, endMs),
            storage.hr.observeBetween(startMs, endMs),
        ) { hr247, hrLive -> hr247 to hrLive }

        return combine(dayDataFlow, hrFlow) { dayData, hrPair ->
            val (hr247, hrLive) = hrPair
            val moods = dayData.moods.filter { isOnDate(it.occurredAt, date) }
            val feedback = dayData.feedback.filter { isOnDate(it.timestampMs, date) }
            val hrSamples = mergeHrSamples(hr247, hrLive)
            TimelineUiState(
                selectedDate = date,
                weekDates = weekDatesFor(date),
                daySummary = buildDaySummary(dayData.activity, hrSamples, feedback),
                events = buildEvents(dayData.training, moods, feedback, hrSamples)
            )
        }
    }

    private data class DayDataSlice(
        val training: List<TrainingSessionEntity>,
        val moods: List<MoodEntryEntity>,
        val feedback: List<LlmFeedbackEntry>,
        val activity: List<com.owner.mindbody.data.local.ActivityMinuteSampleEntity>
    )

    private fun buildDaySummary(
        activity: List<com.owner.mindbody.data.local.ActivityMinuteSampleEntity>,
        hrSamples: List<Pair<Long, Int>>,
        feedback: List<LlmFeedbackEntry>
    ): DaySummary {
        val steps = activity.mapNotNull { it.steps }.sum().takeIf { it > 0 }
        val avgHr = hrSamples.map { it.second }.average().takeIf { hrSamples.isNotEmpty() }?.toInt()
        val anxietyCount = feedback.count {
            it.stateLabel in ANXIETY_STATE_LABELS
        }
        return DaySummary(
            steps = steps,
            avgHrBpm = avgHr,
            anxietyEventCount = anxietyCount
        )
    }

    private fun buildEvents(
        training: List<TrainingSessionEntity>,
        moods: List<MoodEntryEntity>,
        feedback: List<LlmFeedbackEntry>,
        hrSamples: List<Pair<Long, Int>>
    ): List<TimelineEvent> {
        val events = mutableListOf<TimelineEvent>()

        training.forEach { session ->
            val start = session.startTimeMs ?: sessionDateStartMs(session.sessionDate)
            if (start == null) return@forEach
            val end = session.endTimeMs ?: (start + (session.durationSeconds ?: 0) * 1000L)
            val avgBpm = averageBpmBetween(hrSamples, start, end)
            events += TimelineEvent.Training(
                id = session.devicePath,
                startMs = start,
                endMs = end,
                title = trainingTitle(session),
                durationLabel = formatDurationSeconds(session.durationSeconds ?: durationSeconds(start, end)),
                avgBpm = avgBpm
            )
        }

        moods.forEach { entry ->
            val role = EmotionRoles.findById(entry.roleId)
            events += TimelineEvent.Mood(
                id = entry.id,
                startMs = entry.occurredAt,
                roleDisplayName = role?.displayName,
                diaryPreview = entry.fact.trim().takeIf { it.isNotEmpty() },
                hrBpm = entry.hrAtEntry
            )
        }

        feedback.filter { it.stateLabel != "baseline_building" }.forEach { entry ->
            events += TimelineEvent.PhysioFeedback(
                id = entry.id,
                startMs = entry.timestampMs,
                stateLabel = entry.stateLabel,
                anxietyScore = entry.anxietyScore,
                message = entry.message
            )
        }

        return events.sortedBy { it.sortKeyMs }
    }

    private fun mergeHrSamples(
        hr247: List<Hr247SampleEntity>,
        hrLive: List<HrSampleEntity>
    ): List<Pair<Long, Int>> {
        val merged = LinkedHashMap<Long, Int>()
        hr247.forEach { sample ->
            merged[sample.timestamp / MERGE_BUCKET_MS] = sample.bpm
        }
        hrLive.forEach { sample ->
            merged[sample.timestamp / MERGE_BUCKET_MS] = sample.bpm
        }
        return merged.entries.map { (bucket, bpm) -> bucket * MERGE_BUCKET_MS to bpm }
    }

    private fun averageBpmBetween(
        hrSamples: List<Pair<Long, Int>>,
        startMs: Long,
        endMs: Long
    ): Int? {
        val values = hrSamples.filter { (ts, _) -> ts in startMs..endMs }.map { it.second }
        return values.average().takeIf { values.isNotEmpty() }?.toInt()
    }

    private fun isOnDate(timestampMs: Long, date: LocalDate): Boolean {
        val eventDate = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDate()
        return eventDate == date
    }

    private fun dateRangeMs(date: LocalDate): Pair<Long, Long> {
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
        return start to end
    }

    private fun trainingTitle(session: TrainingSessionEntity): String {
        return when {
            session.exerciseCount > 1 -> "综合训练"
            session.exerciseCount == 1 -> "运动训练"
            else -> "训练会话"
        }
    }

    private suspend fun refreshFeedbackFromServer() {
        try {
            withContext(Dispatchers.IO) {
                val baseUrl = storage.syncPreferences.baseUrl.first()
                val apiKey = storage.syncPreferences.apiKey.first()
                val deviceId = storage.syncPreferences.deviceId.first()
                if (baseUrl.isBlank() || apiKey.isBlank() || deviceId.isBlank()) return@withContext

                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/api/vitals/stream/status?device_id=$deviceId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext
                    val body = response.body?.string() ?: return@withContext
                    val feedback = FeedbackHistoryCodec.fromApiResponse(body)
                    if (feedback.isNotEmpty()) {
                        storage.updateFeedbackHistory(feedback)
                    }
                }
            }
        } catch (_: Exception) {
            // 网络失败静默处理，保留 DataStore / 内存中的上次数据
        }
    }

    private fun sessionDateStartMs(sessionDate: String): Long? {
        return runCatching {
            LocalDate.parse(sessionDate).atStartOfDay(zoneId).toInstant().toEpochMilli()
        }.getOrNull()
    }

    companion object {
        private const val MERGE_BUCKET_MS = 10_000L
        private val ANXIETY_STATE_LABELS = setOf("elevated", "anxious", "high_anxiety")

        fun weekDatesFor(date: LocalDate): List<LocalDate> {
            val mondayOffset = date.dayOfWeek.value - 1
            val monday = date.minusDays(mondayOffset.toLong())
            return (0..6).map { monday.plusDays(it.toLong()) }
        }

        fun formatDurationSeconds(totalSeconds: Int): String {
            if (totalSeconds <= 0) return "0s"
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m"
                else -> "${seconds}s"
            }
        }

        private fun durationSeconds(startMs: Long, endMs: Long): Int {
            return ((endMs - startMs).coerceAtLeast(0) / 1000).toInt()
        }
    }
}
