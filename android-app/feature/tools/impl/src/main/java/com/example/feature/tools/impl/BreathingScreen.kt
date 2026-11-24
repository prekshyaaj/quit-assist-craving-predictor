package com.example.feature.tools.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class BreathingExercise(
    val name: String,
    val duration: String,
    val whenToUse: String,
    val steps: List<String>,
)

private val exercises = listOf(
    BreathingExercise(
        name = "Box Breathing (4-4-4-4)",
        duration = "About 2 minutes",
        whenToUse = "When a craving hits suddenly. It keeps your mind busy counting.",
        steps = listOf(
            "Breathe in through your nose for 4 seconds.",
            "Hold your breath for 4 seconds.",
            "Breathe out slowly through your mouth for 4 seconds.",
            "Hold again for 4 seconds.",
            "Repeat 4 to 6 times.",
        ),
    ),
    BreathingExercise(
        name = "4-7-8 Breathing",
        duration = "About 1–2 minutes",
        whenToUse = "When you feel stressed or anxious, or can't fall asleep.",
        steps = listOf(
            "Sit or lie down comfortably and close your eyes.",
            "Breathe in quietly through your nose for 4 seconds.",
            "Hold your breath for 7 seconds.",
            "Breathe out fully through your mouth for 8 seconds.",
            "Repeat 4 times.",
        ),
    ),
    BreathingExercise(
        name = "Deep Belly Breathing",
        duration = "3–5 minutes",
        whenToUse = "Any time you want to relax and slow down your heart rate.",
        steps = listOf(
            "Put one hand on your chest and one on your belly.",
            "Breathe in slowly through your nose so your belly rises, not your chest.",
            "Breathe out slowly through pursed lips and feel your belly fall.",
            "Keep a slow, steady pace for a few minutes.",
        ),
    ),
    BreathingExercise(
        name = "Craving Wave (Urge Surfing)",
        duration = "5 minutes",
        whenToUse = "When a strong urge to smoke won't go away.",
        steps = listOf(
            "Notice the craving and where you feel it in your body. Don't fight it.",
            "Breathe slowly and imagine the craving as a wave rising.",
            "With each breath out, picture the wave getting smaller.",
            "Remind yourself: cravings usually pass in 5 to 10 minutes.",
        ),
    ),
)

private val tips = listOf(
    "Most cravings peak and fade within 5–10 minutes. Breathing helps you ride them out.",
    "Slow breathing calms your body, the same relaxed feeling people look for in a cigarette.",
    "Drink a glass of water or go for a short walk after the exercise.",
    "Practise once a day even without a craving, so it becomes easy when you need it.",
)

/** Static breathing-exercise guide (no timers, no data stored). */
@Composable
fun BreathingScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

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
            Text("Breathing Exercises", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = "Feeling a craving or stressed? Pick one exercise below and follow the steps. " +
                "A few slow breaths can help the urge pass.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        exercises.forEach { ex ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = ex.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ex.duration,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = ex.whenToUse,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                    )
                    ex.steps.forEachIndexed { i, step ->
                        Text(
                            text = "${i + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Tips",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                tips.forEach { tip ->
                    Text(
                        text = "• $tip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        Text(
            text = "If you feel dizzy, stop and breathe normally.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
