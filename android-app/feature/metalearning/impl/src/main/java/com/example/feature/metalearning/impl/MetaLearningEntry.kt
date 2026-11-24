package com.example.feature.metalearning.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.metalearning.api.MetaLearningNavKey

fun EntryProviderScope<NavKey>.metaLearningEntry(
    token: String,
    onBackClick: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    entry<MetaLearningNavKey> {
        MetaLearningScreen(token = token, onBackClick = onBackClick, onUnauthorized = onUnauthorized)
    }
}
