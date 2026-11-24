package com.example.core.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After a reboot or app update the listening service is gone, and Android 14+ won't let it
 * restart from the background, so ask the user to tap and resume if they had it on.
 */
class ListeningBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (VoiceListeningService.isEnabled(context)) {
            VoiceListeningService.showResumeNotification(context)
        }
    }
}
