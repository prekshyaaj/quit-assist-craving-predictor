package com.example.feature.tools.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.tools.api.ToolsNavKey

fun EntryProviderScope<NavKey>.toolsEntry(
    onOpenChatbot: () -> Unit = {},
) {
    entry<ToolsNavKey> {
        ToolsScreen(onOpenChatbot = onOpenChatbot)
    }
}
