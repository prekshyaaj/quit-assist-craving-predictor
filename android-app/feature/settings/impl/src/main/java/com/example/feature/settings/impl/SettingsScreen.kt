package com.example.feature.settings.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

/**
 * Settings Screen displaying options list with left icon, title, and right chevron arrow button.
 */
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onVoiceDetectionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMetaLearningClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                SettingsRowItem(
                    icon = Icons.Outlined.Mic,
                    title = "Craving Voice Detection",
                    onClick = onVoiceDetectionClick,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRowItem(
                    icon = Icons.Outlined.ModelTraining,
                    title = "Meta Learning",
                    onClick = onMetaLearningClick,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRowItem(
                    icon = Icons.Outlined.Person,
                    title = "Account & Profile",
                    onClick = onAccountClick,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRowItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    onClick = onNotificationsClick,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRowItem(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy & Security",
                    onClick = onPrivacyClick,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                SettingsRowItem(
                    icon = Icons.AutoMirrored.Outlined.Help,
                    title = "Help & Support",
                    onClick = onHelpClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Reusable Settings row item button with left icon, title, and right chevron arrow.
 */
@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SmokeAppTheme {
        SettingsScreen(
            onBackClick = {},
            onVoiceDetectionClick = {},
        )
    }
}
