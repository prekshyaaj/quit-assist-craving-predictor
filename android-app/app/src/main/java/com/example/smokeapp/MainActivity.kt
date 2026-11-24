package com.example.smokeapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.core.voice.VoiceListeningService
import com.example.core.voice.VoiceNavKey
import com.example.core.voice.voiceEntry
import com.example.feature.login.api.QuestionnaireApi
import com.example.feature.login.api.QuestionnaireApiException
import com.example.feature.chatbot.api.ChatbotNavKey
import com.example.feature.chatbot.impl.chatbotEntry
import com.example.feature.home.api.HomeNavKey
import com.example.feature.home.impl.homeEntry
import com.example.feature.home.impl.SmokeLog
import com.example.feature.login.api.AuthManager
import com.example.feature.login.api.LoginNavKey
import com.example.feature.login.api.ProfileNavKey
import com.example.feature.login.api.QuestionnaireNavKey
import com.example.feature.login.impl.loginEntry
import com.example.feature.login.impl.profileEntry
import com.example.feature.metalearning.api.MetaLearningNavKey
import com.example.feature.metalearning.impl.metaLearningEntry
import com.example.feature.login.impl.questionnaireEntry
import com.example.feature.settings.api.SettingsNavKey
import com.example.feature.settings.impl.settingsEntry
import com.example.feature.tools.impl.toolsEntry
import com.example.smokeapp.core.designsystem.AppNavigationSuiteScaffold
import com.example.smokeapp.core.designsystem.HeaderTopBar
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmokeAppTheme {
                SmokeAppApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The app is in the foreground now, so Android allows restarting the microphone service.
        VoiceListeningService.resumeIfEnabled(this)
    }
}

@PreviewScreenSizes
@Composable
fun SmokeAppApp() {
    val context = LocalContext.current
    var userToken by remember { mutableStateOf(AuthManager.getToken(context) ?: "") }

    val initialNavKey = if (userToken.isEmpty()) LoginNavKey else HomeNavKey
    val backStack = rememberNavBackStack(initialNavKey)
    val currentNavKey = backStack.last()

    fun navigateTo(navKey: NavKey) {
        if (backStack.last() != navKey) {
            backStack.clear()
            backStack.add(navKey)
        }
    }

    fun signOut() {
        AuthManager.signOut(context)
        userToken = ""
        navigateTo(LoginNavKey)
    }

    // Runs at startup and after every login/signup: send the user to the questionnaire until it's filled in.
    LaunchedEffect(userToken) {
        if (userToken.isEmpty()) return@LaunchedEffect
        val isComplete = try {
            QuestionnaireApi.getQuestionnaire(userToken).isComplete
        } catch (e: QuestionnaireApiException) {
            if (e.statusCode == 401) {
                signOut()
                return@LaunchedEffect
            }
            Log.w("SmokeApp", "Questionnaire check failed", e)
            true
        } catch (e: IOException) {
            Log.w("SmokeApp", "Questionnaire check failed", e)
            true // Offline: don't block the app.
        }
        when {
            !isComplete -> navigateTo(QuestionnaireNavKey)
            backStack.last() == LoginNavKey -> navigateTo(HomeNavKey)
        }
    }

    val entryProvider = remember(userToken) {
        entryProvider {
            loginEntry(onLoginSuccess = { token -> userToken = token })
            questionnaireEntry(
                token = userToken,
                onComplete = { navigateTo(HomeNavKey) },
                onUnauthorized = ::signOut,
            )
            homeEntry()
            toolsEntry(onOpenChatbot = { navigateTo(ChatbotNavKey) })
            voiceEntry(
                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            )
            settingsEntry(
                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                onVoiceDetectionClick = { backStack.add(VoiceNavKey) },
                onMetaLearningClick = { backStack.add(MetaLearningNavKey) },
                onAccountClick = { backStack.add(ProfileNavKey) },
                onHelpClick = { backStack.add(HelpNavKey) },
                onNotificationsClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                },
            )

            chatbotEntry(
                token = userToken,
                onUnauthorized = ::signOut,
            )
            metaLearningEntry(
                token = userToken,
                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                onUnauthorized = ::signOut,
            )
            entry<ExploreNavKey> { Text(text = "Explore") }

            profileEntry(
                token = userToken,
                onBackClick = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                onUnauthorized = ::signOut,
            )
            entry<FeedbackNavKey> { Text(text = "Send Feedback", Modifier.padding(0.dp, 16.dp)) }
            entry<AboutNavKey> { Text(text = "About", Modifier.padding(0.dp, 16.dp)) }
            entry<HelpNavKey> { Text(text = "Help", Modifier.padding(0.dp, 16.dp)) }
        }
    }

    val isKeyboardOpen = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp

    val layoutType = if (isKeyboardOpen || currentNavKey == LoginNavKey || currentNavKey == QuestionnaireNavKey || currentNavKey in DROP_DOWN_NAV_ITEMS) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    AppNavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            NAV_ITEMS.forEach { (navKey, navItem) ->
                item(
                    selected = navKey == currentNavKey,
                    onClick = {
                        if (currentNavKey != navKey) {
                            backStack.clear()
                            backStack.add(navKey)
                        }
                    },
                    icon = { Icon(imageVector = navItem.unselectedIcon, contentDescription = navItem.title) },
                    selectedIcon = { Icon(imageVector = navItem.selectedIcon, contentDescription = navItem.title) },
                    label = { Text(navItem.title) },
                )
            }
        },
    ) {
        Scaffold(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            topBar = {
                if (currentNavKey != LoginNavKey && currentNavKey != QuestionnaireNavKey) {
                    HeaderTopBar(
                        userName = AuthManager.getUserEmail(context)?.substringBefore("@") ?: "User",
                        subtitle = run {
                            SmokeLog.init(context) // loads once; coins update after "I smoked" / edits
                            "Sober coins: ${SmokeLog.soberCoins()}"
                        },
                        onProfileClick = { backStack.add(ProfileNavKey) },
                        onNotificationClick = { /* Handle notification click */ },
                        onSettingsClick = { backStack.add(SettingsNavKey) },
                        onFeedbackClick = { backStack.add(FeedbackNavKey) },
                        onAboutClick = { backStack.add(AboutNavKey) },
                        onHelpClick = { backStack.add(HelpNavKey) },
                        onLogoutClick = ::signOut,
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            NavDisplay(
                backStack = backStack,
                entryProvider = entryProvider,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
