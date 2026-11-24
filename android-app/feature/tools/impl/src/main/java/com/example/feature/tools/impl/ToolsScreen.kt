package com.example.feature.tools.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smokeapp.core.designsystem.DescriptionButton
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

private data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    onOpenChatbot: () -> Unit = {},
) {
    // Which tool page is open (null = the tools list).
    var openTool by rememberSaveable { mutableStateOf<String?>(null) }
    when (openTool) {
        "diary" -> {
            DiaryScreen(onBack = { openTool = null }, modifier = modifier)
            return
        }
        "breathing" -> {
            BreathingScreen(onBack = { openTool = null }, modifier = modifier)
            return
        }
        "why_i_quit" -> {
            WhyIQuitScreen(onBack = { openTool = null }, modifier = modifier)
            return
        }
        "talk" -> {
            TalkScreen(onBack = { openTool = null }, onOpenChatbot = onOpenChatbot, modifier = modifier)
            return
        }
    }

    val tools = listOf(
        ToolItem(
            id = "diary",
            title = "Diary",
            description = "Journal your thoughts",
            icon = Icons.Outlined.Book,
        ),
        ToolItem(
            id = "games",
            title = "Games",
            description = "Play games to bust",
            icon = Icons.Outlined.SportsEsports,
        ),
        ToolItem(
            id = "breathing",
            title = "Breathing Exercises",
            description = "Release stress with mindful breathing",
            icon = Icons.Outlined.Air,
        ),
        ToolItem(
            id = "why_i_quit",
            title = "Why I Quit?",
            description = "Note down your affirmations for quitting",
            icon = Icons.Outlined.AssignmentTurnedIn,
        ),
        ToolItem(
            id = "talk",
            title = "Talk to Someone",
            description = "You don't have to handle this alone",
            icon = Icons.Outlined.SupportAgent,
        ),
    )

    // Main Scrollable Screen Content
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        // Tools List
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            tools.forEach { tool ->
                DescriptionButton(
                    title = tool.title,
                    description = tool.description,
                    icon = tool.icon,
                    onClick = {
                        if (tool.id in setOf("diary", "breathing", "why_i_quit", "talk")) openTool = tool.id
                        /* other tools: not implemented yet */
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ToolsScreenPreview() {
    SmokeAppTheme {
        ToolsScreen()
    }
}
