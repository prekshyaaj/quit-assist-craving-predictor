package com.example.smokeapp.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme
/**
 * Reusable top header bar with profile button, user greeting, and notification bell button.
 *
 * @param userName The user's display name
 * @param modifier Optional [Modifier]
 * @param onProfileClick Callback when the profile menu item is clicked
 * @param onNotificationClick Callback when the notification bell icon/button is clicked
 * @param onSettingsClick Callback when the settings option is clicked
 * @param onFeedbackClick Callback when the feedback option is clicked
 * @param onAboutClick Callback when the about option is clicked
 * @param onHelpClick Callback when the help option is clicked
 */
@Composable
fun HeaderTopBar(
    userName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onProfileClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
) {
    var showProfileMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Profile & Greeting Group
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                IconButton(
                    onClick = { showProfileMenu = true },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(36.dp),
                    )
                }

                DropdownMenu(
                    expanded = showProfileMenu,
                    onDismissRequest = { showProfileMenu = false },
                ) {
                    // First section
                    DropdownMenuItem(
                        text = { Text("Profile") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        onClick = {
                            showProfileMenu = false
                            onProfileClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        onClick = {
                            showProfileMenu = false
                            onSettingsClick()
                        },
                    )

                    HorizontalDivider()

                    // Second section
                    DropdownMenuItem(
                        text = { Text("Send Feedback") },
                        leadingIcon = { Icon(Icons.Outlined.Feedback, contentDescription = null) },
                        trailingIcon = { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null) },
                        onClick = {
                            showProfileMenu = false
                            onFeedbackClick()
                        },
                    )

                    HorizontalDivider()

                    // Third section
                    DropdownMenuItem(
                        text = { Text("About") },
                        leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                        onClick = {
                            showProfileMenu = false
                            onAboutClick()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Help") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Help, contentDescription = null) },
                        trailingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                        onClick = {
                            showProfileMenu = false
                            onHelpClick()
                        },
                    )

                    HorizontalDivider()

                    // Fourth section: Log Out
                    DropdownMenuItem(
                        text = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = "Log Out",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            showProfileMenu = false
                            onLogoutClick()
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            androidx.compose.foundation.layout.Column {
                Text(
                    text = "Hi, $userName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Notification Bell Button
        IconButton(
            onClick = onNotificationClick,
        ) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HeaderTopBarPreview() {
    SmokeAppTheme {
        HeaderTopBar(
            userName = "Ashwini",
        )
    }
}

