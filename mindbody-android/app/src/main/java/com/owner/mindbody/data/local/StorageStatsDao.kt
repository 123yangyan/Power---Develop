package com.owner.mindbody.data.local

import androidx.room.Dao
import androidx.room.Query

/**
 * 开发者看板：各表行数与最近更新时间统计（只读）。
 */
@Dao
interface StorageStatsDao {

    @Query("SELECT COUNT(*) FROM hr_samples")
    suspend fun countHrSamples(): Long

    @Query("SELECT MAX(timestamp) FROM hr_samples")
    suspend fun lastHrSampleTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM skin_temp_samples")
    suspend fun countSkinTempSamples(): Long

    @Query("SELECT MAX(timestamp) FROM skin_temp_samples")
    suspend fun lastSkinTempTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM acc_minute_summary")
    suspend fun countAccMinuteSummary(): Long

    @Query("SELECT MAX(minuteTimestamp) FROM acc_minute_summary")
    suspend fun lastAccMinuteTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM ppi_samples")
    suspend fun countPpiSamples(): Long

    @Query("SELECT MAX(timestamp) FROM ppi_samples")
    suspend fun lastPpiTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM hr_247_samples")
    suspend fun countHr247Samples(): Long

    @Query("SELECT MAX(timestamp) FROM hr_247_samples")
    suspend fun lastHr247Timestamp(): Long?

    @Query("SELECT COUNT(*) FROM ppi_247_samples")
    suspend fun countPpi247Samples(): Long

    @Query("SELECT MAX(timestamp) FROM ppi_247_samples")
    suspend fun lastPpi247Timestamp(): Long?

    @Query("SELECT COUNT(*) FROM skin_temp_247_samples")
    suspend fun countSkinTemp247Samples(): Long

    @Query("SELECT MAX(timestamp) FROM skin_temp_247_samples")
    suspend fun lastSkinTemp247Timestamp(): Long?

    @Query("SELECT COUNT(*) FROM activity_day_summary")
    suspend fun countActivityDaySummary(): Long

    @Query("SELECT MAX(updatedAt) FROM activity_day_summary")
    suspend fun lastActivityDayUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM activity_minute_samples")
    suspend fun countActivityMinuteSamples(): Long

    @Query("SELECT MAX(timestamp) FROM activity_minute_samples")
    suspend fun lastActivityMinuteTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM nightly_recharge")
    suspend fun countNightlyRecharge(): Long

    @Query("SELECT MAX(updatedAt) FROM nightly_recharge")
    suspend fun lastNightlyRechargeUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM sleep_sessions")
    suspend fun countSleepSessions(): Long

    @Query("SELECT MAX(updatedAt) FROM sleep_sessions")
    suspend fun lastSleepUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM training_sessions")
    suspend fun countTrainingSessions(): Long

    @Query("SELECT MAX(updatedAt) FROM training_sessions")
    suspend fun lastTrainingUpdatedAt(): Long?

    @Query("SELECT COUNT(*) FROM mood_entries")
    suspend fun countMoodEntries(): Long

    @Query("SELECT MAX(occurredAt) FROM mood_entries")
    suspend fun lastMoodOccurredAt(): Long?
}
