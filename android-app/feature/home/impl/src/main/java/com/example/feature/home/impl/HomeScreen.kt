package com.example.feature.home.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SmokeFree
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smokeapp.core.designsystem.AddNewTaskButton
import com.example.smokeapp.core.designsystem.ProgressCard
import com.example.smokeapp.core.designsystem.SimpleButtonLight
import com.example.smokeapp.core.designsystem.TaskButton
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

private data class ChallengeItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean,
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    val challenges = remember {
        mutableStateListOf(
            ChallengeItem(id = "1", title = "My positive affirmation", isCompleted = true),
            ChallengeItem(id = "2", title = "Drink 8 glasses of water", isCompleted = false),
            ChallengeItem(id = "3", title = "10 minute deep breathing", isCompleted = false),
            ChallengeItem(id = "4", title = "Go for a 15-min walk", isCompleted = false),
            ChallengeItem(id = "5", title = "Read 5 pages of a book", isCompleted = true),
            ChallengeItem(id = "6", title = "Write in journal", isCompleted = false),
        )
    }
    val context = LocalContext.current
    remember { SmokeLog.init(context) }

    // Main Scrollable Screen Content
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        // My week: smoke-free days, cigarettes per day and streak
        MyWeekCard(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))

        // Quick Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            SimpleButtonLight(
                onClick = { /* Handle action */ },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
            ) {
                Text(
                    text = "I feel like smoking",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            SimpleButtonLight(
                onClick = {
                    val n = SmokeLog.logCigarette()
                    Toast.makeText(
                        context,
                        "Logged. Today: $n cigarette${if (n == 1) "" else "s"}",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp),
            ) {
                Text(
                    text = "I smoked",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Challenges Section Title
        Text(
            text = "Challenges",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // 3-Row Horizontally Scrollable Grid (Compact Height)
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
        ) {
            items(challenges, key = { it.id }) { challenge ->
                TaskButton(
                    title = challenge.title,
                    isCompleted = challenge.isCompleted,
                    onCompletedChange = { completed ->
                        val index = challenges.indexOfFirst { it.id == challenge.id }
                        if (index != -1) {
                            challenges[index] = challenge.copy(isCompleted = completed)
                        }
                    },
                    modifier = Modifier.width(220.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // AddNewTaskButton kept separate taking full width
        AddNewTaskButton(
            text = "Add a new challenge",
            onClick = { /* Handle add challenge */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // My Progress Section Title
        Text(
            text = "My Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Progress Cards 2x2 Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProgressCard(
                    title = "Money",
                    value = "903",
                    icon = Icons.Outlined.Savings,
                    modifier = Modifier.weight(1f),
                )
                ProgressCard(
                    title = "Cigarettes",
                    value = "10",
                    icon = Icons.Outlined.SmokeFree,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ProgressCard(
                    title = "Life",
                    value = "1 hour",
                    icon = Icons.Outlined.FavoriteBorder,
                    modifier = Modifier.weight(1f),
                )
                ProgressCard(
                    title = "Time",
                    value = "10",
                    icon = Icons.Outlined.AccessTime,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    SmokeAppTheme {
        HomeScreen()
    }
}
