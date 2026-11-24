package com.example.feature.metalearning.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

/** State of the per-user evaluation (GET /evaluate). */
internal data class EvalState(
    val loading: Boolean = false,
    val result: EvaluationResult? = null,
    val error: String? = null,
)

/**
 * Recall, precision and macro F1 of cold-start vs personalised, computed by the server on the
 * logged-in user's own data after day 1 (day 1 is what Adapt learns from).
 */
@Composable
internal fun ModelResultsCard(
    state: EvalState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Model results for this user",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRefresh, enabled = !state.loading) { Text("Refresh") }
            }

            val r = state.result
            when {
                state.loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Scoring this user's days after day 1…", style = MaterialTheme.typography.bodyMedium)
                }

                state.error != null -> Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )

                r != null -> ResultsTable(r)

                else -> Text(
                    "Tap Refresh to score this user's data.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultsTable(r: EvaluationResult) {
    val threshold = (r.cold.threshold * 100).toInt()
    Text(
        "Unseen days after day 1 · ${r.testSlots} ten-minute slots · " +
            "${r.smokingEvents} smoking events · threshold $threshold%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Metric", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.4f))
        Text("Cold-start", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("Personalised", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    val c = r.cold
    val p = r.personalised
    MetricRow(
        name = "Recall", meaning = "smoking events caught",
        cold = "${pct(c.recall)}\n(${c.caught}/${c.events})",
        pers = p?.let { "${pct(it.recall)}\n(${it.caught}/${it.events})" },
        coldValue = c.recall, persValue = p?.recall,
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    MetricRow(
        name = "Precision", meaning = "alerts that were real",
        cold = pct(c.precision), pers = p?.let { pct(it.precision) },
        coldValue = c.precision, persValue = p?.precision,
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    MetricRow(
        name = "Macro F1", meaning = "overall balance",
        cold = String.format(Locale.US, "%.1f", c.macroF1 * 100),
        pers = p?.let { String.format(Locale.US, "%.1f", it.macroF1 * 100) },
        coldValue = c.macroF1, persValue = p?.macroF1,
    )

    Text(
        text = if (p == null) {
            "False alarms: cold-start ${c.falseAlarms}. Tap Adapt to see the personalised results."
        } else {
            "False alarms: cold-start ${c.falseAlarms} · personalised ${p.falseAlarms}"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun MetricRow(
    name: String,
    meaning: String,
    cold: String,
    pers: String?,
    coldValue: Double,
    persValue: Double?,
) {
    // Highlight the better value; no highlight on a tie or without a personalised model.
    val diff = if (persValue == null) 0.0 else persValue - coldValue
    val coldBetter = diff < -1e-9
    val persBetter = diff > 1e-9
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.4f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(meaning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MetricValue(cold, highlight = coldBetter, modifier = Modifier.weight(1f))
        MetricValue(pers ?: "—", highlight = persBetter, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricValue(text: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private fun pct(x: Double): String = String.format(Locale.US, "%.1f%%", x * 100)
