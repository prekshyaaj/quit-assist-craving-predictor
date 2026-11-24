package com.example.feature.login.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.login.api.QuestionnaireNavKey

fun EntryProviderScope<NavKey>.questionnaireEntry(
    token: String,
    onComplete: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    entry<QuestionnaireNavKey> {
        QuestionnaireScreen(
            token = token,
            onComplete = onComplete,
            onUnauthorized = onUnauthorized,
        )
    }
}
