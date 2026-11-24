package com.example.core.voice

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.voiceEntry(
    onBackClick: () -> Unit = {},
) {
    entry<VoiceNavKey> {
        VoiceScreen(onBackClick = onBackClick)
    }
}
