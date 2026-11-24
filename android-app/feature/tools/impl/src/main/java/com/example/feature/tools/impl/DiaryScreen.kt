package com.example.feature.tools.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diary: a list of entries (like chat threads). "New entry" creates one,
 * tapping an entry opens it for editing. Text is saved as you type.
 */
@Composable
fun DiaryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { DiaryStore(context) }
    var entries by remember { mutableStateOf(store.load()) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun update(newEntries: List<DiaryEntry>) {
        entries = newEntries.sortedByDescending { it.updatedAt }
        store.save(entries)
    }

    val open = entries.firstOrNull { it.id == openId }

    if (open == null) {
        BackHandler(onBack = onBack)
        DiaryList(
            entries = entries,
            onBack = onBack,
            onNew = {
                val now = System.currentTimeMillis()
                update(listOf(DiaryEntry(now, "", "", now)) + entries)
                openId = now
            },
            onOpen = { openId = it.id },
            modifier = modifier,
        )
    } else {
        BackHandler { openId = null }
        DiaryEditor(
            entry = open,
            onChange = { changed ->
                update(entries.map { if (it.id == changed.id) changed else it })
            },
            onDelete = {
                update(entries.filterNot { it.id == open.id })
                openId = null
            },
            onBack = { openId = null },
            modifier = modifier,
        )
    }
}

@Composable
private fun DiaryList(
    entries: List<DiaryEntry>,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (DiaryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text("Diary", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Button(onClick = onNew) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("New entry")
            }
        }

        if (entries.isEmpty()) {
            Text(
                text = "No entries yet. Tap \"New entry\" to write how you feel today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    Card(
                        onClick = { onOpen(entry) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = entry.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatDate(entry.updatedAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (entry.text.isNotBlank()) {
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryEditor(
    entry: DiaryEntry,
    onChange: (DiaryEntry) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to entries")
            }
            Text(
                text = formatDate(entry.id),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete entry")
            }
        }

        OutlinedTextField(
            value = entry.title,
            onValueChange = {
                onChange(entry.copy(title = it, updatedAt = System.currentTimeMillis()))
            },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = entry.text,
            onValueChange = {
                onChange(entry.copy(text = it, updatedAt = System.currentTimeMillis()))
            },
            label = { Text("Write your thoughts…") },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 12.dp, bottom = 16.dp),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
