package com.example.smokeapp.core.designsystem

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScope
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppNavigationSuiteScaffold(
    navigationSuiteItems: AppNavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    content: @Composable (() -> Unit) = {},
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppNavigationSuiteScope(navigationSuiteScope = this).run(navigationSuiteItems)
        },
        layoutType = layoutType,
        modifier = modifier,
    ) {
        content()
    }
}

class AppNavigationSuiteScope internal constructor(
    private val navigationSuiteScope: NavigationSuiteScope,
) {
    fun item(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: @Composable () -> Unit,
        selectedIcon: @Composable () -> Unit = icon,
        label: @Composable (() -> Unit)? = null,
    ) = navigationSuiteScope.item(
        selected = selected,
        onClick = onClick,
        icon = {
            if (selected) {
                selectedIcon()
            } else {
                icon()
            }
        },
        label = label,
        modifier = modifier,
    )
}
