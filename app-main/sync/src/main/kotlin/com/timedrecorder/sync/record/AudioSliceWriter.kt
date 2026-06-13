package com.timedrecorder.sync.record

import com.timedrecorder.core.common.DeviceIdProvider
import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频切片写入器：负责文件命名与数据库记录创建。
 * 命名规范：{deviceIdShort}_local_{yyyyMMdd}_{HHmmss}_{seq}.m4a
 */
@Singleton
class AudioSliceWriter @Inject constructor(
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFormat = SimpleDateFormat("HHmmss", Locale.US)

    /**
     * 生成切片文件名。
     *
     * @param seq    三位序号，从 001 起
     * @param prefix 文件前缀，计划任务使用 "local"，手动录音使用 "manual"（T4）
     */
    suspend fun generateFileName(
        format: AudioFormat,
        seq: Int,
        startTimeMillis: Long,
        prefix: String = "local",
    ): String {
        val deviceShort = deviceIdProvider.getDeviceIdShort()
        val date = dateFormat.format(Date(startTimeMillis))
        val time = timeFormat.format(Date(startTimeMillis))
        return "${deviceShort}_${prefix}_${date}_${time}_${seq.toString().padStart(3, '0')}.${format.extension}"
    }

    /** 获取录音文件存储目录 */
    fun getRecordingDirectory(baseDir: File): File {
        val dir = File(baseDir, "recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** 构建 AudioFile 数据库记录（切片完成后调用） */
    fun buildAudioFileRecord(
        taskId: Long?,
        fileName: String,
        filePath: String,
        format: AudioFormat,
        startAt: Long,
        endAt: Long,
        duration: Long,
        fileSize: Long,
        /** T4：是否为手动录音 */
        isManualRecording: Boolean = false,
    ): AudioFile = AudioFile(
        taskId = taskId,
        fileName = fileName,
        filePath = filePath,
        format = format,
        startAt = startAt,
        endAt = endAt,
        duration = duration,
        fileSize = fileSize,
        uploadStatus = UploadStatus.PENDING,
        processStatus = ProcessStatus.PENDING,
        isManualRecording = isManualRecording,
    )
}
