package com.example.feature.login.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.login.api.ProfileNavKey

fun EntryProviderScope<NavKey>.profileEntry(
    token: String,
    onBackClick: () -> Unit,
    onUnauthorized: () -> Unit,
) {
    entry<ProfileNavKey> {
        ProfileScreen(token = token, onBackClick = onBackClick, onUnauthorized = onUnauthorized)
    }
}
