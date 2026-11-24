package com.example.core.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/** The latest analysed segment, shown on the voice screen. */
data class Heard(val result: CravingResult, val notificationSent: Boolean)

/**
 * Foreground service that keeps the microphone on in the background, cuts speech into
 * segments, sends each one to the voice server and notifies the user on a craving.
 */
class VoiceListeningService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Like the Python queue: at most 5 waiting segments, oldest dropped if the server is slow.
    private val segments = Channel<ByteArray>(capacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (_isRunning.value) return START_STICKY

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // Android 14+ refuses to start a microphone service from the background,
            // e.g. when the system restarts this service after killing it.
            Log.w(TAG, "Can't start listening from the background", e)
            showResumeNotification(this)
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationManagerCompat.from(this).cancel(RESUME_NOTIFICATION_ID)
        _isRunning.value = true
        scope.launch { keepListening() }
        scope.launch { analyzeSegments() }
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    /** Reopens the microphone after a failure; gives up and asks the user to resume if it keeps failing. */
    private suspend fun keepListening() {
        var failures = 0
        while (true) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                listen()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (SystemClock.elapsedRealtime() - startedAt > MIC_HEALTHY_MS) failures = 0
                if (++failures > MAX_MIC_RESTARTS) {
                    Log.e(TAG, "Microphone keeps failing, stopping", e)
                    break
                }
                Log.w(TAG, "Microphone failed, restarting ($failures/$MAX_MIC_RESTARTS)", e)
                delay(MIC_RESTART_DELAY_MS)
            }
        }
        showResumeNotification(this)
        stopSelf()
    }

    /** Runs until cancelled; throws if the microphone stops delivering audio. */
    private suspend fun listen() {
        val audioRecord = VoiceRecorder.openMicrophone()
        val frame = ByteArray(SpeechSegmenter.FRAME_BYTES)
        SpeechSegmenter().use { segmenter ->
            try {
                audioRecord.startRecording()
                check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
                while (currentCoroutineContext().isActive) {
                    val read = audioRecord.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) throw IOException("Microphone read failed ($read)")
                    if (read != frame.size) continue
                    segmenter.feed(frame.copyOf())?.let { segments.trySend(it) }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    private suspend fun analyzeSegments() {
        for (pcm in segments) {
            try {
                val results = analyzeWithRetries(pcm) ?: continue
                val craving = results.firstOrNull { it.isCraving }
                val shown = craving ?: results.maxByOrNull { it.score } ?: continue
                val sent = craving != null && CravingNotifier.notifyCraving(this)
                _lastHeard.value = Heard(shown, sent)
            } catch (e: Exception) {
                Log.w(TAG, "Segment analysis failed", e)
            }
        }
    }

    /** Returns null if the server stayed unreachable; the segment is then dropped. */
    private suspend fun analyzeWithRetries(pcm: ByteArray): List<CravingResult>? {
        repeat(UPLOAD_ATTEMPTS) { attempt ->
            try {
                return VoiceApi.analyze(pcm).also { _lastError.value = null }
            } catch (e: IOException) {
                Log.w(TAG, "Upload attempt ${attempt + 1}/$UPLOAD_ATTEMPTS failed", e)
                _lastError.value = "Can't reach the voice server (${e.message})"
                if (attempt < UPLOAD_ATTEMPTS - 1) delay(UPLOAD_RETRY_DELAY_MS)
            }
        }
        return null
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(CHANNEL_ID, "Background listening", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, VoiceListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_craving_notification)
            .setContentTitle("Listening for cravings")
            .setContentText("Audio is analysed for craving phrases and not stored.")
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "VoiceListeningService"
        private const val CHANNEL_ID = "voice_listening"
        private const val RESUME_CHANNEL_ID = "voice_listening_paused"
        private const val NOTIFICATION_ID = 1002
        private const val RESUME_NOTIFICATION_ID = 1003
        private const val ACTION_STOP = "com.example.core.voice.STOP"
        private const val PREFS = "voice_listening"
        private const val KEY_ENABLED = "enabled"

        private const val MAX_MIC_RESTARTS = 3
        private const val MIC_RESTART_DELAY_MS = 2_000L
        private const val MIC_HEALTHY_MS = 60_000L // a failure after this long starts a fresh count
        private const val UPLOAD_ATTEMPTS = 3
        private const val UPLOAD_RETRY_DELAY_MS = 3_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _lastHeard = MutableStateFlow<Heard?>(null)
        val lastHeard: StateFlow<Heard?> = _lastHeard

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError

        /** Must be called while the app is in the foreground (Android 14+ rule for microphone services). */
        fun start(context: Context) {
            setEnabled(context, true)
            ContextCompat.startForegroundService(context, Intent(context, VoiceListeningService::class.java))
        }

        fun stop(context: Context) {
            setEnabled(context, false)
            context.stopService(Intent(context, VoiceListeningService::class.java))
        }

        /** Call when the app comes to the foreground: restarts listening if the user left it on. */
        fun resumeIfEnabled(context: Context) {
            if (!isEnabled(context) || _isRunning.value) return
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                setEnabled(context, false)
                return
            }
            start(context)
        }

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        private fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
        }

        /** Asks the user to reopen the app, which restarts listening from the foreground. */
        fun showResumeNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val channel = NotificationChannel(
                RESUME_CHANNEL_ID,
                "Listening paused",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, RESUME_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_craving_notification)
                .setContentTitle("Craving listening paused")
                .setContentText("Tap to resume listening")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(RESUME_NOTIFICATION_ID, notification)
        }
    }
}
