package com.example.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * "My week": Sun–Sat of the current week from [SmokeLog].
 * 😁 = smoke-free day, 🙁 = smoked (count below), ❓ = future / not tracked yet. 🔥 = current streak.
 * Tap a past day (or today) to correct its count.
 */
@Composable
internal fun MyWeekCard(modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val days = (0L..6L).map { weekStart.plusDays(it) }
    // Reading SmokeLog.counts / startDate here makes the card update after "I smoked" or an edit.
    val counts = SmokeLog.counts
    SmokeLog.startDate
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "My week",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text("🔥 ${SmokeLog.streak(today)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                days.forEach { day ->
                    val tracked = SmokeLog.isTracked(day, today)
                    val count = counts[day] ?: 0
                    val isToday = day == today
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isToday) Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                else Modifier,
                            )
                            .clickable(enabled = !day.isAfter(today)) { editing = day }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            text = when {
                                !tracked -> "❓"
                                count == 0 -> "😁"
                                else -> "🙁"
                            },
                            fontSize = 22.sp,
                        )
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            text = if (tracked) count.toString() else "-",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    editing?.let { day ->
        var value by remember(day) { mutableIntStateOf(SmokeLog.countOn(day)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Cigarettes on ${day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))}") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledTonalIconButton(onClick = { if (value > 0) value-- }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Less")
                    }
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp),
                    )
                    FilledTonalIconButton(onClick = { value++ }) {
                        Icon(Icons.Outlined.Add, contentDescription = "More")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    SmokeLog.setCount(day, value)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
