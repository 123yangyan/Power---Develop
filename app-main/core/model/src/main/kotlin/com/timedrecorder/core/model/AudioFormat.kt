package com.timedrecorder.core.model

/**
 * 音频格式。V1.0 默认 m4a/aac。
 */
enum class AudioFormat(val extension: String, val mimeType: String) {
    M4A("m4a", "audio/mp4"),
    MP3("mp3", "audio/mpeg"),
    WAV("wav", "audio/wav"),
}
