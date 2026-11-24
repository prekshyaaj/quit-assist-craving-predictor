package com.example.feature.login.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.login.api.LoginNavKey

fun EntryProviderScope<NavKey>.loginEntry(
    onLoginSuccess: (String) -> Unit,
) {
    entry<LoginNavKey> {
        LoginScreen(
            onLoginSuccess = onLoginSuccess,
        )
    }
}
