package com.timedrecorder.core.database.converter

import androidx.room.TypeConverter
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.LogLevel
import com.timedrecorder.core.model.LogType
import com.timedrecorder.core.model.MessageType
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.RepeatType
import com.timedrecorder.core.model.RiskLevel
import com.timedrecorder.core.model.UploadStatus

/**
 * Room 枚举类型转换器，将枚举持久化为字符串。
 */
class EnumConverters {
    @TypeConverter
    fun fromUploadStatus(value: UploadStatus): String = value.name

    @TypeConverter
    fun toUploadStatus(value: String): UploadStatus = UploadStatus.valueOf(value)

    @TypeConverter
    fun fromProcessStatus(value: ProcessStatus): String = value.name

    @TypeConverter
    fun toProcessStatus(value: String): ProcessStatus = ProcessStatus.valueOf(value)

    @TypeConverter
    fun fromRepeatType(value: RepeatType): String = value.name

    @TypeConverter
    fun toRepeatType(value: String): RepeatType = RepeatType.valueOf(value)

    @TypeConverter
    fun fromAudioFormat(value: AudioFormat): String = value.name

    @TypeConverter
    fun toAudioFormat(value: String): AudioFormat = AudioFormat.valueOf(value)

    @TypeConverter
    fun fromRiskLevel(value: RiskLevel?): String? = value?.name

    @TypeConverter
    fun toRiskLevel(value: String?): RiskLevel? =
        value?.let { RiskLevel.valueOf(it) }

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun fromLogType(value: LogType): String = value.name

    @TypeConverter
    fun toLogType(value: String): LogType = LogType.valueOf(value)

    @TypeConverter
    fun fromLogLevel(value: LogLevel): String = value.name

    @TypeConverter
    fun toLogLevel(value: String): LogLevel = LogLevel.valueOf(value)
}
