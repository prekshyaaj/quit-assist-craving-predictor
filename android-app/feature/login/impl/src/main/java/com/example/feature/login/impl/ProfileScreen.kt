package com.example.feature.login.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.feature.login.api.AuthManager
import com.example.feature.login.api.QuestionnaireApi
import com.example.feature.login.api.QuestionnaireApiException
import com.example.feature.login.api.QuestionnaireData
import com.example.smokeapp.core.designsystem.theme.SmokeAppTheme

/**
 * The signed-in user's name, email and questionnaire answers.
 */
@Composable
fun ProfileScreen(
    token: String,
    onBackClick: () -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val email = remember { AuthManager.getUserEmail(context).orEmpty() }

    var answers by remember { mutableStateOf<QuestionnaireData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(token) {
        try {
            answers = QuestionnaireApi.getQuestionnaire(token)
        } catch (e: QuestionnaireApiException) {
            if (e.statusCode == 401) {
                onUnauthorized()
            } else {
                error = "Couldn't load your profile."
            }
        } catch (_: Exception) {
            error = "Couldn't reach the server. Check your connection."
        }
    }

    ProfileContent(
        name = email.substringBefore("@").ifEmpty { "User" },
        email = email,
        answers = answers,
        error = error,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    name: String,
    email: String,
    answers: QuestionnaireData?,
    error: String?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Text(
                    text = name.first().uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (email.isNotEmpty()) {
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "About you",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        when {
            error != null -> Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            answers == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> AnswersCard(answers)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AnswersCard(answers: QuestionnaireData) {
    val rows = listOf(
        "Age" to answers.age?.let { if (it >= MAX_AGE) "$MAX_AGE+" else "$it" },
        "Sex" to sexLabel(answers.sex),
        "Cigarettes per day" to answers.cigarettesNow?.toInt()?.let {
            if (it >= MAX_CIGARETTES) "$MAX_CIGARETTES+" else "$it"
        },
        "Employment" to employmentLabel(answers.employmentStatus),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ProfileRow(label = label, value = value ?: "Not set")
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    SmokeAppTheme {
        ProfileContent(
            name = "alex",
            email = "alex@example.com",
            answers = QuestionnaireData(age = 34, sex = 1, cigarettesNow = 10.0, employmentStatus = 103),
            error = null,
            onBackClick = {},
        )
    }
}
