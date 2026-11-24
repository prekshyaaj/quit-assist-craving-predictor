package com.example.smokeapp

import androidx.navigation3.runtime.NavKey
import com.example.core.voice.VoiceNavKey
import com.example.feature.chatbot.api.ChatbotNavKey
import com.example.feature.home.api.HomeNavKey
import com.example.feature.login.api.ProfileNavKey
import com.example.feature.metalearning.api.MetaLearningNavKey
import com.example.feature.settings.api.SettingsNavKey
import com.example.feature.tools.api.ToolsNavKey
import com.example.smokeapp.core.designsystem.NavUIElements
import com.example.smokeapp.core.designsystem.NavigationItem
import kotlinx.serialization.Serializable

@Serializable
object ExploreNavKey : NavKey

// Dropdown Menu NavKeys
@Serializable
object FeedbackNavKey : NavKey
@Serializable
object AboutNavKey : NavKey
@Serializable
object HelpNavKey : NavKey

val HOME = NavigationItem(
    title = "Home",
    selectedIcon = NavUIElements.HomeSelected,
    unselectedIcon = NavUIElements.HomeUnselected,
)

val TOOLS = NavigationItem(
    title = "Tools",
    selectedIcon = NavUIElements.PsychologySelected,
    unselectedIcon = NavUIElements.PsychologyUnselected,
)

val CHATBOT = NavigationItem(
    title = "Chatbot",
    selectedIcon = NavUIElements.SmartToySelected,
    unselectedIcon = NavUIElements.SmartToyUnselected,
)

val EXPLORE = NavigationItem(
    title = "Explore",
    selectedIcon = NavUIElements.ExploreSelected,
    unselectedIcon = NavUIElements.ExploreUnselected,
)

val NAV_ITEMS = mapOf(
    HomeNavKey to HOME,
    ToolsNavKey to TOOLS,
    ChatbotNavKey to CHATBOT,
    ExploreNavKey to EXPLORE,
)

val DROP_DOWN_NAV_ITEMS = setOf(
    ProfileNavKey,
    SettingsNavKey,
    FeedbackNavKey,
    AboutNavKey,
    HelpNavKey,
    VoiceNavKey,
    MetaLearningNavKey,
)
