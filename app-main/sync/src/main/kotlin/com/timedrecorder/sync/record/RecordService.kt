package com.timedrecorder.sync.record



import android.app.Notification

import android.app.NotificationChannel

import android.app.NotificationManager

import android.app.Service

import android.content.Intent

import android.media.MediaRecorder

import android.os.Build

import android.os.IBinder

import androidx.core.app.NotificationCompat

import com.timedrecorder.core.data.status.RecordingStateHolder

import com.timedrecorder.core.data.repository.AudioFileRepository

import com.timedrecorder.core.data.repository.LogRepository

import com.timedrecorder.core.data.repository.ScheduleRepository

import com.timedrecorder.core.datastore.PreferencesDataSource

import com.timedrecorder.core.model.AudioFormat

import com.timedrecorder.core.model.LogLevel

import com.timedrecorder.core.model.LogType

import com.timedrecorder.core.model.RecordingScenario
import com.timedrecorder.core.model.RecordingState

import com.timedrecorder.sync.R

import com.timedrecorder.sync.RecordServiceController

import com.timedrecorder.sync.schedule.ScheduleAlarmManager

import com.timedrecorder.sync.worker.WorkScheduler

import dagger.hilt.android.AndroidEntryPoint

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.Job

import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.cancel

import kotlinx.coroutines.delay

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import java.io.File

import java.util.concurrent.atomic.AtomicBoolean

import javax.inject.Inject

import kotlin.math.min



/**

 * 前台录音服务：承载 MediaRecorder 与切片逻辑。

 * 支持手动/计划录音、暂停/继续、有效时长累计。

 */

@AndroidEntryPoint

class RecordService : Service() {



    @Inject lateinit var scheduleRepository: ScheduleRepository

    @Inject lateinit var audioFileRepository: AudioFileRepository

    @Inject lateinit var logRepository: LogRepository

    @Inject lateinit var preferencesDataSource: PreferencesDataSource

    @Inject lateinit var audioSliceWriter: AudioSliceWriter

    @Inject lateinit var recordingStateHolder: RecordingStateHolder

    @Inject lateinit var workScheduler: WorkScheduler

    @Inject lateinit var scheduleAlarmManager: ScheduleAlarmManager



    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var recordingJob: Job? = null

    private var mediaRecorder: MediaRecorder? = null

    private var currentTaskId: Long = -1L

    private var sliceSeq: Int = 0

    private var currentSliceFile: File? = null

    private var currentSliceStartAt: Long = 0L

    private var currentTaskName: String = "录音任务"

    private var sliceDurationMs: Long = 5 * 60_000L

    /** 当前手动录音场景，影响标题、切片间隔与文件名前缀 */
    private var currentScenario: RecordingScenario? = null

    /** 用户主动暂停标志 */

    @Volatile

    private var isPausedByUser: Boolean = false



    /** 暂停时保存的当前切片剩余倒计时（毫秒） */

    private var sliceDelayRemainingMs: Long = 0L



    private val isRecordingActive = AtomicBoolean(false)



    override fun onBind(intent: Intent?): IBinder? = null



    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            RecordServiceController.ACTION_START -> {

                val taskId = intent.getLongExtra(RecordServiceController.EXTRA_TASK_ID, -1L)

                val scenarioId = intent.getStringExtra(RecordServiceController.EXTRA_SCENARIO_ID)

                val scenario = RecordingScenario.fromId(scenarioId)

                if (taskId >= 0) startRecordingTask(taskId, scenario)

            }

            RecordServiceController.ACTION_STOP -> {

                val taskId = intent.getLongExtra(

                    RecordServiceController.EXTRA_TASK_ID,

                    RecordServiceController.ANY_ACTIVE_TASK_ID,

                )

                stopRecordingTask(taskId)

            }

            RecordServiceController.ACTION_PAUSE -> pauseRecording()

            RecordServiceController.ACTION_RESUME -> resumeRecording()

            RecordServiceController.ACTION_CANCEL -> cancelRecording()

        }

        return START_STICKY

    }



    override fun onDestroy() {

        recordingJob?.cancel()

        serviceScope.cancel()

        releaseRecorder()

        isRecordingActive.set(false)

        recordingStateHolder.reset()

        super.onDestroy()

    }



    private fun startRecordingTask(taskId: Long, scenario: RecordingScenario? = null) {

        serviceScope.launch {

            if (taskId != RecordServiceController.MANUAL_TASK_ID &&

                currentTaskId == RecordServiceController.MANUAL_TASK_ID &&

                recordingJob?.isActive == true

            ) {

                logRepository.log(LogType.RECORDING, LogLevel.INFO, "计划任务 $taskId 抢占手动录音，先终止手动录音")

                recordingStateHolder.endRecordingSegment()

                finalizeCurrentSlice(currentTaskId, sliceDurationMinutes = 0)

                recordingJob?.cancel()

                recordingJob = null

                isRecordingActive.set(false)

                isPausedByUser = false

                sliceDelayRemainingMs = 0L

            }



            if (recordingJob?.isActive == true && currentTaskId == taskId) return@launch



            if (!isRecordingActive.compareAndSet(false, true)) {

                logRepository.log(LogType.RECORDING, LogLevel.WARN, "已有任务 $currentTaskId 在录音，忽略 taskId=$taskId")

                return@launch

            }



            currentTaskId = taskId

            sliceSeq = 0

            isPausedByUser = false

            sliceDelayRemainingMs = 0L



            val prefs = preferencesDataSource.userPreferences.first()

            val isManual = taskId == RecordServiceController.MANUAL_TASK_ID

            currentScenario = if (isManual) scenario else null

            // 手动录音整段录制，结束后一次上传；定时任务仍按设置切片
            sliceDurationMs = if (isManual) {
                Long.MAX_VALUE / 2
            } else {
                val sliceMinutes = currentScenario?.sliceDurationMinutes ?: prefs.sliceDurationMinutes
                sliceMinutes * 60_000L
            }

            currentTaskName = if (isManual) {

                currentScenario?.defaultSessionTitle ?: "手动录音"

            } else {

                scheduleRepository.getTaskById(taskId)?.taskName ?: "录音任务"

            }

            val audioFormat = if (isManual) {

                AudioFormat.M4A

            } else {

                scheduleRepository.getTaskById(taskId)?.audioFormat ?: AudioFormat.M4A

            }



            recordingStateHolder.beginSession(taskId, currentTaskName, currentScenario)

            startForeground(NOTIFICATION_ID, buildNotification())



            logRepository.log(

                LogType.RECORDING, LogLevel.INFO,

                "录音服务已启动${if (isManual) "（手动）" else ""}，切片间隔 ${prefs.sliceDurationMinutes} 分钟",

            )



            recordingJob = launch {

                try {

                    while (isActive) {

                        while (isPausedByUser && isActive) {

                            delay(200)

                        }

                        if (!isActive) break



                        recordingStateHolder.endRecordingSegment()

                        recordingStateHolder.updateState(RecordingState.SLICING)

                        updateNotification()



                        sliceSeq++

                        currentSliceStartAt = System.currentTimeMillis()



                        val prefix = if (isManual) {

                            currentScenario?.filePrefix ?: "manual"

                        } else {

                            "local"

                        }

                        val fileName = audioSliceWriter.generateFileName(

                            format = audioFormat,

                            seq = sliceSeq,

                            startTimeMillis = currentSliceStartAt,

                            prefix = prefix,

                        )

                        val dir = audioSliceWriter.getRecordingDirectory(filesDir)

                        currentSliceFile = File(dir, fileName)



                        startMediaRecorder(currentSliceFile!!, audioFormat, prefs.audioBitrate.bps)

                        recordingStateHolder.beginRecordingSegment()

                        recordingStateHolder.updateState(RecordingState.RECORDING)

                        updateNotification()



                        var remaining = if (sliceDelayRemainingMs > 0) {

                            sliceDelayRemainingMs

                        } else {

                            sliceDurationMs

                        }

                        sliceDelayRemainingMs = 0L



                        while (remaining > 0 && isActive) {

                            if (isPausedByUser) {

                                sliceDelayRemainingMs = remaining

                                while (isPausedByUser && isActive) {

                                    delay(200)

                                }

                                if (!isActive) break

                                continue

                            }

                            val tick = min(remaining, 500L)

                            delay(tick)

                            if (!isPausedByUser) {

                                remaining -= tick

                            }

                        }



                        if (!isActive) break



                        recordingStateHolder.endRecordingSegment()

                        finalizeCurrentSlice(taskId, prefs.sliceDurationMinutes, isManual)

                        updateNotification()

                    }

                } catch (e: Exception) {

                    recordingStateHolder.endRecordingSegment()

                    recordingStateHolder.updateState(RecordingState.ERROR)

                    logRepository.log(LogType.RECORDING, LogLevel.ERROR, "录音异常: ${e.message}")

                } finally {

                    isRecordingActive.set(false)

                }

            }

        }

    }



    private fun pauseRecording() {

        serviceScope.launch {

            if (!isRecordingActive.get() || isPausedByUser) return@launch

            if (recordingStateHolder.state.value != RecordingState.RECORDING) return@launch



            isPausedByUser = true

            recordingStateHolder.endRecordingSegment()

            runCatching { mediaRecorder?.pause() }

            recordingStateHolder.updateState(RecordingState.PAUSED)

            logRepository.log(LogType.RECORDING, LogLevel.INFO, "录音已暂停")

            updateNotification()

        }

    }



    private fun resumeRecording() {

        serviceScope.launch {

            if (!isRecordingActive.get() || !isPausedByUser) return@launch



            isPausedByUser = false

            runCatching { mediaRecorder?.resume() }

            recordingStateHolder.beginRecordingSegment()

            recordingStateHolder.updateState(RecordingState.RECORDING)

            logRepository.log(LogType.RECORDING, LogLevel.INFO, "录音已继续")

            updateNotification()

        }

    }



    /** 放弃录音：删除当前切片文件，不写入数据库 */
    private fun cancelRecording() {

        serviceScope.launch {

            if (!isRecordingActive.get()) return@launch



            isPausedByUser = false

            sliceDelayRemainingMs = 0L

            recordingStateHolder.endRecordingSegment()

            releaseRecorder()

            currentSliceFile?.let { file ->

                if (file.exists()) {

                    file.delete()

                    logRepository.log(LogType.RECORDING, LogLevel.INFO, "已丢弃切片: ${file.name}")

                }

            }

            currentSliceFile = null

            recordingJob?.cancel()

            recordingJob = null

            isRecordingActive.set(false)

            currentScenario = null

            recordingStateHolder.reset()

            stopForeground(STOP_FOREGROUND_REMOVE)

            stopSelf()

        }

    }



    private fun stopRecordingTask(taskId: Long) {

        serviceScope.launch {

            val matchesTask = taskId == RecordServiceController.ANY_ACTIVE_TASK_ID ||

                currentTaskId == taskId ||

                taskId < 0

            if (!matchesTask || !isRecordingActive.get()) return@launch



            val stoppingTaskId = currentTaskId

            if (stoppingTaskId > RecordServiceController.MANUAL_TASK_ID) {

                scheduleRepository.getTaskById(stoppingTaskId)?.let { task ->

                    if (task.enabled) {

                        scheduleAlarmManager.scheduleTaskAlarms(task)

                    }

                }

            }



            isPausedByUser = false

            sliceDelayRemainingMs = 0L

            recordingStateHolder.endRecordingSegment()

            finalizeCurrentSlice(stoppingTaskId, sliceDurationMinutes = 0)

            recordingJob?.cancel()

            recordingJob = null

            isRecordingActive.set(false)

            currentScenario = null

            recordingStateHolder.reset()

            stopForeground(STOP_FOREGROUND_REMOVE)

            stopSelf()

        }

    }



    private suspend fun finalizeCurrentSlice(

        taskId: Long,

        sliceDurationMinutes: Int,

        isManual: Boolean = taskId == RecordServiceController.MANUAL_TASK_ID,

    ) {

        val sliceFile = currentSliceFile ?: return

        val startAt = currentSliceStartAt

        releaseRecorder()

        currentSliceFile = null



        if (!sliceFile.exists() || sliceFile.length() == 0L) return



        val endAt = System.currentTimeMillis()

        val task = if (isManual) null else scheduleRepository.getTaskById(taskId)

        val format = task?.audioFormat ?: AudioFormat.M4A



        val record = audioSliceWriter.buildAudioFileRecord(

            taskId = taskId,

            fileName = sliceFile.name,

            filePath = sliceFile.absolutePath,

            format = format,

            startAt = startAt,

            endAt = endAt,

            duration = endAt - startAt,

            fileSize = sliceFile.length(),

            isManualRecording = isManual,

        )



        val fileId = audioFileRepository.insertFile(record)

        logRepository.log(LogType.RECORDING, LogLevel.INFO, "切片已保存: ${sliceFile.name}")



        recordingStateHolder.updateState(RecordingState.UPLOADING)

        workScheduler.enqueueUpload(fileId)

    }



    private fun startMediaRecorder(outputFile: File, format: AudioFormat, bitRate: Int) {

        releaseRecorder()

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

            MediaRecorder(this)

        } else {

            @Suppress("DEPRECATION")

            MediaRecorder()

        }.apply {

            setAudioSource(MediaRecorder.AudioSource.MIC)

            setOutputFormat(

                when (format) {

                    AudioFormat.M4A -> MediaRecorder.OutputFormat.MPEG_4

                    AudioFormat.MP3 -> MediaRecorder.OutputFormat.MPEG_4

                    AudioFormat.WAV -> MediaRecorder.OutputFormat.THREE_GPP

                },

            )

            setAudioEncoder(

                when (format) {

                    AudioFormat.M4A -> MediaRecorder.AudioEncoder.AAC

                    AudioFormat.MP3 -> MediaRecorder.AudioEncoder.AAC

                    AudioFormat.WAV -> MediaRecorder.AudioEncoder.AMR_NB

                },

            )

            setAudioEncodingBitRate(bitRate)

            setOutputFile(outputFile.absolutePath)

            prepare()

            start()

        }

    }



    private fun releaseRecorder() {

        runCatching {

            mediaRecorder?.apply { stop(); release() }

        }

        mediaRecorder = null

    }



    private fun updateNotification() {

        val manager = getSystemService(NotificationManager::class.java)

        manager.notify(NOTIFICATION_ID, buildNotification())

    }



    private fun buildNotification(): Notification {

        val state = recordingStateHolder.state.value

        val elapsed = formatElapsed(recordingStateHolder.currentDisplayElapsedMs())

        val statusText = when (state) {

            RecordingState.RECORDING -> "录音中 · $elapsed"

            RecordingState.PAUSED -> "已暂停 · $elapsed"

            RecordingState.SLICING -> "切片中 · $elapsed"

            RecordingState.UPLOADING -> "上传中 · $elapsed"

            else -> "录音服务运行中 · $elapsed"

        }

        return NotificationCompat.Builder(this, CHANNEL_ID)

            .setContentTitle(currentTaskName)

            .setContentText(statusText)

            .setSmallIcon(R.drawable.ic_notification)

            .setOngoing(true)

            .build()

    }



    private fun formatElapsed(ms: Long): String {

        val totalSec = ms / 1000

        val hours = totalSec / 3600

        val minutes = (totalSec % 3600) / 60

        val seconds = totalSec % 60

        return if (hours > 0) {

            "%d:%02d:%02d".format(hours, minutes, seconds)

        } else {

            "%02d:%02d".format(minutes, seconds)

        }

    }



    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(

                CHANNEL_ID, "录音服务", NotificationManager.IMPORTANCE_LOW,

            ).apply { description = "定时录音前台服务" }

            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        }

    }



    companion object {

        private const val CHANNEL_ID = "recording_service"

        private const val NOTIFICATION_ID = 1001

    }

}


