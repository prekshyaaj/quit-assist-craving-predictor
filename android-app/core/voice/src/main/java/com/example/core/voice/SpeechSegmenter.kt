package com.example.core.voice

import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.ByteArrayOutputStream
import java.io.Closeable

/**
 * Cuts a continuous 16 kHz mono 16-bit PCM stream into speech segments using WebRTC VAD,
 * the same detector and timing rules as the server's vad.py, so only speech leaves the phone.
 */
class SpeechSegmenter : Closeable {

    private val vad = VadWebRTC(
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_480, // 30 ms
        mode = Mode.VERY_AGGRESSIVE,           // same as webrtcvad.Vad(3) on the server
    )
    private val preBuffer = ArrayDeque<ByteArray>()
    private val segment = ByteArrayOutputStream()
    private var recording = false
    private var speechRun = 0
    private var silenceRun = 0

    /** Feed one [FRAME_BYTES] frame; returns a finished segment, or null. */
    fun feed(frame: ByteArray): ByteArray? {
        val speech = vad.isSpeech(frame)

        if (!recording) {
            preBuffer.addLast(frame)
            if (preBuffer.size > PRE_BUFFER_FRAMES) preBuffer.removeFirst()
            speechRun = if (speech) speechRun + 1 else 0
            if (speechRun >= TRIGGER_FRAMES) {
                recording = true
                silenceRun = 0
                preBuffer.forEach { segment.write(it) }
                preBuffer.clear()
            }
            return null
        }

        segment.write(frame)
        silenceRun = if (speech) 0 else silenceRun + 1
        if (silenceRun < HANGOVER_FRAMES && segment.size() < MAX_SEGMENT_BYTES) return null

        val finished = segment.toByteArray()
        segment.reset()
        recording = false
        speechRun = 0
        return finished
    }

    override fun close() = vad.close()

    companion object {
        const val FRAME_BYTES = VoiceRecorder.SAMPLE_RATE * 30 / 1000 * 2 // 30 ms
        private const val PRE_BUFFER_FRAMES = 20                           // 600 ms
        private const val TRIGGER_FRAMES = 8                               // 240 ms
        private const val HANGOVER_FRAMES = 83                             // 2.5 s
        private const val MAX_SEGMENT_BYTES = VoiceRecorder.SAMPLE_RATE * 2 * 30 // 30 s
    }
}
