package com.example.core.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Opens the microphone as raw 16 kHz mono 16-bit PCM,
 * the format the voice server's /analyze endpoint expects.
 */
object VoiceRecorder {

    const val SAMPLE_RATE = 16000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    // Callers must hold RECORD_AUDIO before calling.
    @SuppressLint("MissingPermission")
    fun openMicrophone(): AudioRecord {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING).coerceAtLeast(4096)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
        return audioRecord
    }
}
