package com.example.core.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.smokeapp.core.designsystem.SimpleButtonLight
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

/**
 * Background listening toggle, plus the latest analysed speech so you can see it working.
 */
@Composable
fun VoiceScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val isListening by VoiceListeningService.isRunning.collectAsState()
    val lastHeard by VoiceListeningService.lastHeard.collectAsState()
    val lastError by VoiceListeningService.lastError.collectAsState()

    var notificationsAllowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var micDenied by remember { mutableStateOf(false) }

    // Re-check when coming back from the system settings screen.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsAllowed(context)
        onPauseOrDispose { }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsAllowed = granted }

    fun startListening() {
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        VoiceListeningService.start(context)
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micDenied = !granted
        if (granted) startListening()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Voice craving check",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background listening",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Keeps listening when the app is closed and alerts you on a craving",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isListening,
                onCheckedChange = { checked ->
                    when {
                        !checked -> VoiceListeningService.stop(context)
                        !micAllowed(context) -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else -> startListening()
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (micDenied) {
            Text(
                text = "Microphone permission is needed to listen for cravings.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        if (!notificationsAllowed) {
            NotificationsOffWarning(onOpenSettings = { openNotificationSettings(context) })
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = if (isListening) "Listening… speak normally" else "Turn on background listening to get craving alerts",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        lastError?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        lastHeard?.let { heard ->
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Last heard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ResultCard(heard.result)
            if (heard.result.isCraving) {
                Text(
                    text = if (heard.notificationSent) {
                        "Support notification sent"
                    } else {
                        "Notification skipped (1 min cooldown or notifications off)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun NotificationsOffWarning(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Notifications are off",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "You won't get craving alerts until you allow notifications for this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            SimpleButtonLight(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Open notification settings", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultCard(result: CravingResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.isCraving) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (result.isCraving) "Craving detected" else "No craving",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "\"${result.text}\"", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Score: ${"%.2f".format(result.score)}" +
                    (result.match?.let { "  ·  closest: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun micAllowed(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

@Preview(showBackground = true)
@Composable
private fun VoiceScreenPreview() {
    SmokeAppTheme {
        VoiceScreen()
    }
}
