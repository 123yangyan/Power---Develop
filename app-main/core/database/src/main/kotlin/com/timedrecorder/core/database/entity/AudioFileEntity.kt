package com.timedrecorder.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.timedrecorder.core.model.AudioFormat
import com.timedrecorder.core.model.ProcessStatus
import com.timedrecorder.core.model.UploadStatus

/** Room 实体：audio_file 表 */
@Entity(tableName = "audio_file")
data class AudioFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "task_id")
    val taskId: Long?,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    val format: AudioFormat = AudioFormat.M4A,
    @ColumnInfo(name = "start_at")
    val startAt: Long,
    @ColumnInfo(name = "end_at")
    val endAt: Long?,
    val duration: Long,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "upload_status")
    val uploadStatus: UploadStatus = UploadStatus.PENDING,
    @ColumnInfo(name = "upload_retry_count")
    val uploadRetryCount: Int = 0,
    @ColumnInfo(name = "server_file_id")
    val serverFileId: String? = null,
    @ColumnInfo(name = "process_status")
    val processStatus: ProcessStatus = ProcessStatus.PENDING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** T4：是否为手动录音，Room 以 INTEGER 存储，默认 0（false） */
    @ColumnInfo(name = "is_manual_recording", defaultValue = "0")
    val isManualRecording: Boolean = false,
)
