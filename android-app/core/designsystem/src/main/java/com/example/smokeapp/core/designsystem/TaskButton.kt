package com.example.smokeapp.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

/**
 * Contains default values used by [TaskButton].
 */
object TaskButtonDefaults {
    val completedContainerColor: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    val completedContentColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
}

/**
 * TaskButton represents an interactive task item with a tick mark (checkmark) 
 * to indicate when a task is completed, alongside text for the task definition.
 */
@Composable
fun TaskButton(
    title: String,
    isCompleted: Boolean,
    onCompletedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val containerColor by animateColorAsState(
        targetValue = if (isCompleted) {
            TaskButtonDefaults.completedContainerColor
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "taskContainerColor",
    )

    Card(
        onClick = { onCompletedChange(!isCompleted) },
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isCompleted) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Circle
                },
                contentDescription = if (isCompleted) "Task completed" else "Task incomplete",
                tint = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCompleted) {
                    TaskButtonDefaults.completedContentColor
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * AddNewTaskButton displays an interactive button with a plus icon to add or create a new task.
 */
@Composable
fun AddNewTaskButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Add a new challenge",
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = "Add task",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskButtonIncompletePreview() {
    SmokeAppTheme {
        TaskButton(
            title = "Drink 8 glasses of water today",
            isCompleted = false,
            onCompletedChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskButtonCompletedPreview() {
    SmokeAppTheme {
        TaskButton(
            title = "Drink 8 glasses of water today",
            isCompleted = true,
            onCompletedChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AddNewTaskButtonPreview() {
    SmokeAppTheme {
        AddNewTaskButton(
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
