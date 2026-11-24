package com.example.feature.settings.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.settings.api.SettingsNavKey

fun EntryProviderScope<NavKey>.settingsEntry(
    onBackClick: () -> Unit,
    onVoiceDetectionClick: () -> Unit = {},
    onMetaLearningClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
) {
    entry<SettingsNavKey> {
        SettingsScreen(
            onBackClick = onBackClick,
            onVoiceDetectionClick = onVoiceDetectionClick,
            onMetaLearningClick = onMetaLearningClick,
            onNotificationsClick = onNotificationsClick,
            onAccountClick = onAccountClick,
            onPrivacyClick = onPrivacyClick,
            onHelpClick = onHelpClick,
        )
    }
}
