package com.example.feature.home.impl

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.example.feature.home.api.HomeNavKey

fun EntryProviderScope<NavKey>.homeEntry() {
    entry<HomeNavKey> {
        HomeScreen()
    }
}
