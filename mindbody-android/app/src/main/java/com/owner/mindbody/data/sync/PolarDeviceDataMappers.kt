package com.owner.mindbody.data.sync

import com.owner.mindbody.data.local.ActivityDaySummaryEntity
import com.owner.mindbody.data.local.ActivityMinuteSampleEntity
import com.owner.mindbody.data.local.Hr247SampleEntity
import com.owner.mindbody.data.local.NightlyRechargeEntity
import com.owner.mindbody.data.local.Ppi247SampleEntity
import com.owner.mindbody.data.local.SleepSessionEntity
import com.owner.mindbody.data.local.SkinTemp247SampleEntity
import com.owner.mindbody.data.local.TrainingSessionEntity
import com.polar.sdk.api.model.activity.AutomaticSampleTriggerType
import com.polar.sdk.api.model.activity.Polar247HrSamples
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTime
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivitySamplesData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.PolarSkinTemperatureData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.api.model.sleep.PolarSleepData
import com.polar.sdk.api.model.sleep.SleepCycle
import com.polar.sdk.api.model.sleep.SleepWakePhase
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.Types.PbDuration
import fi.polar.remote.representation.protobuf.Types.PbLocalDateTime
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal object PolarDeviceDataMappers {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    fun mergeActivityDaySummary(
        date: String,
        steps: Int?,
        activeTimeMinutes: Int?,
        caloriesActivity: Int?,
        caloriesTraining: Int?,
        caloriesBmr: Int?
    ): ActivityDaySummaryEntity {
        val total = listOfNotNull(caloriesActivity, caloriesTraining, caloriesBmr).takeIf { it.isNotEmpty() }?.sum()
        return ActivityDaySummaryEntity(
            date = date,
            steps = steps,
            activeTimeMinutes = activeTimeMinutes,
            caloriesTotal = total,
            caloriesActivity = caloriesActivity,
            caloriesTraining = caloriesTraining,
            caloriesBmr = caloriesBmr
        )
    }

    fun activeTimeToMinutes(data: PolarActiveTimeData): Int {
        return listOf(
            data.timeLightActivity,
            data.timeContinuousModerateActivity,
            data.timeIntermittentModerateActivity,
            data.timeContinuousVigorousActivity,
            data.timeIntermittentVigorousActivity
        ).sumOf { it.toTotalMinutes() }
    }

    fun map247Hr(data: Polar247HrSamplesData): List<Hr247SampleEntity> {
        val day = data.date
        return data.samples.flatMap { session -> expand247Hr(day, session) }
    }

    fun map247Ppi(data: Polar247PPiSamplesData): List<Ppi247SampleEntity> {
        val day = data.date
        val sample = data.samples
        val baseMs = day.atTime(sample.startTime).atZone(zoneId).toInstant().toEpochMilli()
        return sample.ppiValueList.mapIndexed { index, ppi ->
            val status = sample.statusList.getOrNull(index)
            Ppi247SampleEntity(
                timestamp = baseMs + index,
                ppiMs = ppi,
                errorEstimateMs = sample.ppiErrorEstimateList.getOrNull(index),
                triggerType = sample.triggerType.name,
                skinContact = status?.skinContact?.name,
                movement = status?.movement?.name
            )
        }
    }

    fun mapSkinTemp247(data: PolarSkinTemperatureData): List<SkinTemp247SampleEntity> {
        val day = data.date ?: return emptyList()
        val dayStartMs = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val samples = data.result?.skinTemperatureList.orEmpty()
        return samples.map { sample ->
            SkinTemp247SampleEntity(
                timestamp = dayStartMs + sample.recordingTimeDeltaMs,
                temperatureC = sample.temperature
            )
        }
    }

    fun mapNightlyRecharge(data: PolarNightlyRechargeData): NightlyRechargeEntity? {
        val date = data.sleepResultDate?.toString() ?: return null
        val ansPercent = data.ansRate?.let { (it * 20).coerceIn(0, 100) }
        return NightlyRechargeEntity(
            date = date,
            ansChargePercent = ansPercent,
            recoveryIndicator = data.recoveryIndicator,
            ansRate = data.ansRate,
            hrMeanBpm = data.meanBaselineRRI?.let { 60_000 / it.coerceAtLeast(1) },
            rrMeanMs = data.meanNightlyRecoveryRRI,
            breathingRateHz = data.meanNightlyRecoveryRespirationInterval?.let { 1000f / it },
            sleepTip = data.sleepTip,
            vitalityTip = data.vitalityTip,
            exerciseTip = data.exerciseTip
        )
    }

    fun mapActivityMinute(dayData: PolarActivitySamplesDayData): List<ActivityMinuteSampleEntity> {
        val samples = dayData.polarActivitySamplesDataList.orEmpty()
        return samples.flatMap { block -> expandActivityMinuteBlock(block) }
    }

    fun mapSleep(data: PolarSleepData): SleepSessionEntity? {
        val date = data.date?.toString() ?: data.result?.sleepResultDate?.toString() ?: return null
        val result = data.result ?: return SleepSessionEntity(date = date)
        return SleepSessionEntity(
            date = date,
            sleepStartTimeMs = result.sleepStartTime?.toEpochMs(),
            sleepEndTimeMs = result.sleepEndTime?.toEpochMs(),
            sleepGoalMinutes = result.sleepGoalMinutes,
            userSleepRating = result.userSleepRating?.value,
            batteryRanOut = result.batteryRanOut ?: false,
            sleepSkinTempCelsius = result.sleepSkinTemperatureResult?.sleepSkinTemperatureCelsius,
            sleepSkinTempDeviation = result.sleepSkinTemperatureResult?.deviationFromBaseLine,
            sleepWakePhasesJson = phasesToJson(result.sleepWakePhases),
            sleepCyclesJson = cyclesToJson(result.sleepCycles)
        )
    }

    fun mapTrainingReference(reference: PolarTrainingSessionReference): TrainingSessionEntity {
        return TrainingSessionEntity(
            devicePath = reference.path,
            sessionDate = reference.date.toString(),
            fileSizeBytes = reference.fileSize,
            exerciseCount = reference.exercises.size
        )
    }

    fun enrichTrainingSession(
        reference: PolarTrainingSessionReference,
        session: PolarTrainingSession
    ): TrainingSessionEntity {
        val summary = session.sessionSummary
        val startMs = summary?.start?.let { pbLocalDateTimeToEpochMs(it) }
        val durationSec = summary?.duration?.toTotalSeconds()
        val endMs = if (startMs != null && durationSec != null) {
            startMs + durationSec * 1000L
        } else {
            summary?.end?.let { pbLocalDateTimeToEpochMs(it) }
        }
        return TrainingSessionEntity(
            devicePath = reference.path,
            sessionDate = reference.date.toString(),
            fileSizeBytes = reference.fileSize,
            exerciseCount = reference.exercises.size,
            startTimeMs = startMs,
            endTimeMs = endMs,
            durationSeconds = durationSec
        )
    }

    private fun PbDuration.toTotalSeconds(): Int = hours * 3600 + minutes * 60 + seconds

    /** 将 Polar protobuf 本地时间转为 epoch 毫秒（不依赖 SDK internal API）。 */
    private fun pbLocalDateTimeToEpochMs(pbDateTime: PbLocalDateTime): Long {
        val local = LocalDateTime.of(
            pbDateTime.date.year,
            pbDateTime.date.month,
            pbDateTime.date.day,
            pbDateTime.time.hour,
            pbDateTime.time.minute,
            pbDateTime.time.seconds,
            pbDateTime.time.millis * 1_000_000
        )
        return local.atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun expand247Hr(day: LocalDate, session: Polar247HrSamples): List<Hr247SampleEntity> {
        val baseMs = day.atTime(session.startTime).atZone(zoneId).toInstant().toEpochMilli()
        val intervalMs = when (session.triggerType) {
            AutomaticSampleTriggerType.TRIGGER_TYPE_HIGH_ACTIVITY -> 60_000L
            AutomaticSampleTriggerType.TRIGGER_TYPE_TIMED -> 5 * 60_000L
            else -> 5 * 60_000L
        }
        return session.hrSamples.mapIndexed { index, bpm ->
            Hr247SampleEntity(
                timestamp = baseMs + index * intervalMs,
                bpm = bpm,
                triggerType = session.triggerType.name
            )
        }
    }

    private fun expandActivityMinuteBlock(block: PolarActivitySamplesData): List<ActivityMinuteSampleEntity> {
        val start = block.startTime ?: return emptyList()
        val startMs = start.atZone(zoneId).toInstant().toEpochMilli()
        val stepIntervalSec = block.stepRecordingInterval ?: 60
        val metIntervalSec = block.metRecordingInterval ?: 30
        val stepEntities = block.stepSamples.mapIndexed { index, steps ->
            ActivityMinuteSampleEntity(
                timestamp = startMs + index * stepIntervalSec * 1000L,
                steps = steps
            )
        }
        val metEntities = block.metSamples.mapIndexed { index, met ->
            ActivityMinuteSampleEntity(
                timestamp = startMs + index * metIntervalSec * 1000L,
                metX100 = (met * 100).toInt()
            )
        }
        val activityEntities = block.activityInfoList.map { info ->
            ActivityMinuteSampleEntity(
                timestamp = info.timeStamp.atZone(zoneId).toInstant().toEpochMilli(),
                activityLevel = info.activityClass?.value
            )
        }
        return stepEntities + metEntities + activityEntities
    }

    private fun PolarActiveTime.toTotalMinutes(): Int {
        return hours * 60 + minutes + if (seconds > 0 || millis > 0) 1 else 0
    }

    private fun ZonedDateTime.toEpochMs(): Long = toInstant().toEpochMilli()

    private fun phasesToJson(phases: List<SleepWakePhase>?): String? {
        if (phases.isNullOrEmpty()) return null
        val array = JSONArray()
        phases.forEach { phase ->
            array.put(
                JSONObject()
                    .put("secondsFromSleepStart", phase.secondsFromSleepStart)
                    .put("state", phase.state.name)
            )
        }
        return array.toString()
    }

    private fun cyclesToJson(cycles: List<SleepCycle>?): String? {
        if (cycles.isNullOrEmpty()) return null
        val array = JSONArray()
        cycles.forEach { cycle ->
            array.put(
                JSONObject()
                    .put("secondsFromSleepStart", cycle.secondsFromSleepStart)
                    .put("sleepDepthStart", cycle.sleepDepthStart.toDouble())
            )
        }
        return array.toString()
    }
}
