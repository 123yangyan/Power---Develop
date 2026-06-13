package com.timedrecorder.feature.files

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * T3：MediaPlayer 封装控制器。
 *
 * 关键点：
 * - prepareAsync() 避免主线程阻塞（ANR 风险）
 * - 同一时刻只允许一个文件播放，切换时自动 release 前一个
 * - 进度以 500ms 为间隔轮询更新，避免过于频繁的 UI 重组
 *
 * 注意：该类无 @Singleton 注解，由 FilesViewModel 独占生命周期，
 * ViewModel.onCleared() 时须调用 release() 彻底销毁 MediaPlayer。
 */
class AudioPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // 播放器状态
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // 当前播放的文件 ID（null 表示没有文件在播放）
    private val _playingFileId = MutableStateFlow<Long?>(null)
    val playingFileId: StateFlow<Long?> = _playingFileId.asStateFlow()

    // 当前播放进度（毫秒）
    private val _progressMs = MutableStateFlow(0L)
    val progressMs: StateFlow<Long> = _progressMs.asStateFlow()

    // 当前文件总时长（毫秒）
    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    // 当前播放倍速
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    // 使用 Main 线程调度：MediaPlayer 回调默认在主线程
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * 播放指定文件。
     * - 若当前正在播放同一文件，则切换为暂停（点击同一文件 = 暂停/恢复）
     * - 若当前正在播放不同文件，先 release 再开始新文件
     */
    fun play(filePath: String, fileId: Long) {
        if (_playingFileId.value == fileId) {
            // 同一文件：在播放/暂停之间切换
            when (_state.value) {
                is PlayerState.Playing -> pause()
                is PlayerState.Paused -> resume()
                else -> startNew(filePath, fileId)
            }
            return
        }
        // 切换到新文件：先释放旧播放器
        release()
        startNew(filePath, fileId)
    }

    /** 暂停播放 */
    fun pause() {
        mediaPlayer?.pause()
        _state.value = PlayerState.Paused
        stopProgressTracking()
    }

    /** 恢复播放 */
    fun resume() {
        mediaPlayer?.start()
        _state.value = PlayerState.Playing
        startProgressTracking()
    }

    /** 跳转到指定进度（毫秒） */
    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
        mediaPlayer?.seekTo(clamped.toInt())
        _progressMs.value = clamped
    }

    /** 快进指定毫秒（默认 15 秒） */
    fun skipForward(ms: Long = 15_000) {
        seekTo(_progressMs.value + ms)
    }

    /** 快退指定毫秒（默认 15 秒） */
    fun skipBackward(ms: Long = 15_000) {
        seekTo(_progressMs.value - ms)
    }

    /** 切换播放倍速（1.0x / 1.5x 循环） */
    fun togglePlaybackSpeed() {
        val next = if (_playbackSpeed.value >= 1.5f) 1.0f else 1.5f
        setPlaybackSpeed(next)
    }

    /** 设置播放倍速 */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applyPlaybackSpeed()
    }

    /** 释放所有资源（ViewModel.onCleared() 时调用） */
    fun release() {
        stopProgressTracking()
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _state.value = PlayerState.Idle
        _playingFileId.value = null
        _progressMs.value = 0L
        _durationMs.value = 0L
        _playbackSpeed.value = 1.0f
    }

    private fun applyPlaybackSpeed() {
        val mp = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mp.playbackParams = PlaybackParams().setSpeed(_playbackSpeed.value)
            }
        }
    }

    /** 内部：新建 MediaPlayer 并开始准备 */
    private fun startNew(filePath: String, fileId: Long) {
        _playingFileId.value = fileId
        _state.value = PlayerState.Preparing

        scope.launch {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(filePath)

                // 准备完毕回调
                mp.setOnPreparedListener { player ->
                    _durationMs.value = player.duration.toLong()
                    applyPlaybackSpeed()
                    player.start()
                    _state.value = PlayerState.Playing
                    startProgressTracking()
                }

                // 播放结束回调
                mp.setOnCompletionListener {
                    _state.value = PlayerState.Completed
                    _progressMs.value = 0L
                    stopProgressTracking()
                }

                // 播放错误回调
                mp.setOnErrorListener { _, what, extra ->
                    _state.value = PlayerState.Error("播放出错 (what=$what, extra=$extra)")
                    stopProgressTracking()
                    true
                }

                // 异步准备，不阻塞主线程（避免 ANR）
                mp.prepareAsync()
                mediaPlayer = mp
            } catch (e: Exception) {
                _state.value = PlayerState.Error(e.message ?: "无法加载音频文件")
            }
        }
    }

    /** 每 500ms 更新一次进度 */
    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _progressMs.value = mediaPlayer?.currentPosition?.toLong() ?: 0L
                delay(500L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
