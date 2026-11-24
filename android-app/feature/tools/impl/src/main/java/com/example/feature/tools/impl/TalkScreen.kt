package com.example.feature.tools.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private data class Helpline(val name: String, val number: String, val note: String)

// India helplines (toll-free).
private val helplines = listOf(
    Helpline(
        "National Tobacco Quitline",
        "1800112356",
        "Free counselling to help you quit tobacco",
    ),
    Helpline(
        "Tele-MANAS (mental health)",
        "14416",
        "24×7 free support for stress, anxiety or low mood",
    ),
    Helpline(
        "Emergency",
        "112",
        "Police, ambulance or fire in an emergency",
    ),
)

/**
 * "Talk to Someone": open the chatbot, call a trusted person the user saves,
 * or call a helpline. Calls open the phone's dialer (no call permission needed).
 */
@Composable
fun TalkScreen(
    onBack: () -> Unit,
    onOpenChatbot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("talk_to_someone", Context.MODE_PRIVATE) }
    var contactName by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var contactNumber by remember { mutableStateOf(prefs.getString("number", "") ?: "") }
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Talk to Someone", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = "You don't have to handle cravings alone. Talking, even for a few minutes, " +
                "can help the urge pass.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // --- Chatbot ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Chat with our assistant",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Available any time. Tell it how you're feeling and it will help you " +
                        "get through the craving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Button(onClick = onOpenChatbot, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open chatbot")
                }
            }
        }

        // --- Trusted person ---
        Text(
            text = "My support person",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (contactNumber.isBlank()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Save a friend or family member you can call when a craving gets hard.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilledTonalButton(
                        onClick = { editing = true },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Icon(Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add contact")
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = contactName.ifBlank { "Support person" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = contactNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit contact")
                    }
                    CallButton { dial(context, contactNumber) }
                }
            }
        }

        // --- Helplines ---
        Text(
            text = "Helplines (India, free)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        helplines.forEach { h ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(h.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = h.number,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = h.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CallButton { dial(context, h.number) }
                }
            }
        }

        Text(
            text = "Tapping Call opens your phone's dialer. Nothing is called automatically.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (editing) {
        var name by remember { mutableStateOf(contactName) }
        var number by remember { mutableStateOf(contactNumber) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Support person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { v -> number = v.filter { it.isDigit() || it == '+' || it == ' ' } },
                        label = { Text("Phone number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    contactName = name.trim()
                    contactNumber = number.trim()
                    prefs.edit()
                        .putString("name", contactName)
                        .putString("number", contactNumber)
                        .apply()
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CallButton(onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text("Call")
    }
}

/** Opens the dialer with the number filled in (user still presses call). */
private fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.replace(" ", "")}")))
    } catch (_: Exception) {
        Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
    }
}
