package com.example.core.voice

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * Shows a supportive notification when a craving is detected,
 * at most once per [COOLDOWN_MS], with a randomly picked message.
 */
object CravingNotifier {

    private const val CHANNEL_ID = "craving_alerts"
    private const val NOTIFICATION_ID = 1001
    private const val COOLDOWN_MS = 60 * 1000L // 1 min while testing; use 20 min for release
    private const val PREFS = "craving_notifier"
    private const val KEY_LAST_SENT = "last_sent"

    private val messages = listOf(
        "Feeling a craving? Take 2 minutes to breathe slowly.",
        "This urge will pass in a few minutes. You've got this.",
        "Try a glass of water and a short walk before deciding.",
        "Remember why you quit. Open your reasons in the app.",
        "Cravings peak and fade. Ride this one out with a breathing exercise.",
    )

    /** Returns true if a notification was shown, false if blocked by cooldown or permission. */
    fun notifyCraving(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_SENT, 0L) < COOLDOWN_MS) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        createChannel(context)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_craving_notification)
            .setContentTitle("Stay strong")
            .setContentText(messages.random())
            .setStyle(NotificationCompat.BigTextStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        prefs.edit { putLong(KEY_LAST_SENT, now) }
        return true
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Craving alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Support when a craving is detected" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
