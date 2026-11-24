package com.example.feature.chatbot.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.chatbot.api.ChatbotNavKey

fun EntryProviderScope<NavKey>.chatbotEntry(
    token: String,
    onUnauthorized: () -> Unit = {},
) {
    entry<ChatbotNavKey> {
        ChatbotScreen(
            token = token,
            onUnauthorized = onUnauthorized,
        )
    }
}
