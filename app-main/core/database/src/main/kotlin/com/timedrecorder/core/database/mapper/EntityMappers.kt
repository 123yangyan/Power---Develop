package com.timedrecorder.core.database.mapper

import com.timedrecorder.core.database.entity.AppLogEntity
import com.timedrecorder.core.database.entity.AudioFileEntity
import com.timedrecorder.core.database.entity.MessageEntity
import com.timedrecorder.core.database.entity.ProcessResultEntity
import com.timedrecorder.core.database.entity.ScheduleTaskEntity
import com.timedrecorder.core.model.AppLogEntry
import com.timedrecorder.core.model.AudioFile
import com.timedrecorder.core.model.MessageItem
import com.timedrecorder.core.model.ProcessResult
import com.timedrecorder.core.model.ScheduleTask
import org.json.JSONArray

/** Entity → 对外 Model */
fun ScheduleTaskEntity.asExternalModel(): ScheduleTask = ScheduleTask(
    id = id,
    taskName = taskName,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    enabled = enabled,
    repeatType = repeatType,
    sliceDurationMinutes = sliceDurationMinutes,
    audioFormat = audioFormat,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** Model → Entity */
fun ScheduleTask.asEntity(): ScheduleTaskEntity = ScheduleTaskEntity(
    id = id,
    taskName = taskName,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    enabled = enabled,
    repeatType = repeatType,
    sliceDurationMinutes = sliceDurationMinutes,
    audioFormat = audioFormat,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AudioFileEntity.asExternalModel(): AudioFile = AudioFile(
    id = id,
    taskId = taskId,
    fileName = fileName,
    filePath = filePath,
    format = format,
    startAt = startAt,
    endAt = endAt,
    duration = duration,
    fileSize = fileSize,
    uploadStatus = uploadStatus,
    uploadRetryCount = uploadRetryCount,
    serverFileId = serverFileId,
    processStatus = processStatus,
    createdAt = createdAt,
    isManualRecording = isManualRecording,
)

fun AudioFile.asEntity(): AudioFileEntity = AudioFileEntity(
    id = id,
    taskId = taskId,
    fileName = fileName,
    filePath = filePath,
    format = format,
    startAt = startAt,
    endAt = endAt,
    duration = duration,
    fileSize = fileSize,
    uploadStatus = uploadStatus,
    uploadRetryCount = uploadRetryCount,
    serverFileId = serverFileId,
    processStatus = processStatus,
    createdAt = createdAt,
    isManualRecording = isManualRecording,
)

fun ProcessResultEntity.asExternalModel(): ProcessResult = ProcessResult(
    id = id,
    fileId = fileId,
    transcript = transcript,
    title = title,
    summary = summary,
    keywords = parseKeywords(keywordsJson),
    alertFlag = alertFlag,
    riskLevel = riskLevel,
    resultJson = resultJson,
    processedAt = processedAt,
    createdAt = createdAt,
)

fun ProcessResult.asEntity(): ProcessResultEntity = ProcessResultEntity(
    id = id,
    fileId = fileId,
    transcript = transcript,
    title = title,
    summary = summary,
    keywordsJson = encodeKeywords(keywords),
    alertFlag = alertFlag,
    riskLevel = riskLevel,
    resultJson = resultJson,
    processedAt = processedAt,
    createdAt = createdAt,
)

fun MessageEntity.asExternalModel(): MessageItem = MessageItem(
    id = id,
    title = title,
    content = content,
    type = type,
    fileId = fileId,
    isRead = isRead,
    createdAt = createdAt,
)

fun MessageItem.asEntity(): MessageEntity = MessageEntity(
    id = id,
    title = title,
    content = content,
    type = type,
    fileId = fileId,
    isRead = isRead,
    createdAt = createdAt,
)

fun AppLogEntity.asExternalModel(): AppLogEntry = AppLogEntry(
    id = id,
    logType = logType,
    logLevel = logLevel,
    content = content,
    createdAt = createdAt,
)

fun AppLogEntry.asEntity(): AppLogEntity = AppLogEntity(
    id = id,
    logType = logType,
    logLevel = logLevel,
    content = content,
    createdAt = createdAt,
)

/** 将关键词列表序列化为 JSON 数组字符串 */
private fun encodeKeywords(keywords: List<String>): String {
    val array = JSONArray()
    keywords.forEach { array.put(it) }
    return array.toString()
}

/** 从 JSON 数组字符串解析关键词列表 */
private fun parseKeywords(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                add(array.getString(i))
            }
        }
    }.getOrDefault(emptyList())
}
