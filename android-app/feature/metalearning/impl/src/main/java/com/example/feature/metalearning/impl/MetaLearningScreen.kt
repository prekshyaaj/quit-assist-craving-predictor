package com.example.feature.metalearning.impl

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.core.voice.CravingNotifier
import com.example.feature.login.api.AuthManager
import com.example.feature.login.api.QuestionnaireApi
import com.example.feature.login.api.QuestionnaireApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

private const val PREFS = "metalearning_demo"
private const val KEY_LAST_SMOKED_AT = "last_smoked_at"

private val HighRiskColor = Color(0xFFD32F2F)
private val LowRiskColor = Color(0xFF2E7D32)

/** Holds one side of the comparison: either loading, a result, or an error. */
private data class PanelState(
    val loading: Boolean = false,
    val result: PredictionResult? = null,
    val basedOn: String? = null,
    val error: String? = null,
)

/**
 * Meta Learning demo: the logged-in demo user's profile, hand-entered "sensor" values,
 * and cold-start vs. MAML-adapted predictions side by side.
 */
@Composable
fun MetaLearningScreen(
    token: String,
    onBackClick: () -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(token) { PredictionApi(token) }
    val email = remember { AuthManager.getUserEmail(context).orEmpty() }
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    // Resting HR from the last /adapt for this account (worked out from day 1), reused for both models.
    val restingHrKey = "resting_hr_${email.lowercase()}"
    var restingHr by remember(email) {
        mutableStateOf(prefs.getFloat(restingHrKey, -1f).takeIf { it > 0f }?.toDouble())
    }

    // Static profile from the questionnaire
    var profile by remember { mutableStateOf<DemoProfile?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }

    // Simulated sensors
    val now = remember { Calendar.getInstance() }
    var hour by remember { mutableStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(now.get(Calendar.MINUTE)) }
    var location by remember { mutableStateOf(LOCATIONS.first()) }
    var heartRateText by remember {
        mutableStateOf((restingHr?.toInt() ?: DEFAULT_HEART_RATE).toString())
    }
    var noWatch by remember { mutableStateOf(false) }
    var sleeping by remember { mutableStateOf(false) }
    var lastSmokedAt by remember { mutableLongStateOf(prefs.getLong(KEY_LAST_SMOKED_AT, 0L)) }
    var tslsText by remember { mutableStateOf(DEFAULT_MINUTES_SINCE_CIGARETTE.toString()) }
    var sendAlerts by remember { mutableStateOf(true) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // Results
    var cold by remember { mutableStateOf(PanelState()) }
    var adapting by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var adaptResult by remember { mutableStateOf<AdaptResult?>(null) }
    var adaptError by remember { mutableStateOf<String?>(null) }
    var adapted by remember { mutableStateOf(PanelState()) }
    var evaluation by remember { mutableStateOf(EvalState()) }

    // Android 13+ needs permission to show the high-risk alert; ask here so it works
    // even if the Voice screen (which also asks) was never opened.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            alertMessage = "Notifications are off, so high-risk alerts can't be shown. " +
                "Turn them on in Settings → Notifications."
        }
    }
    LaunchedEffect(sendAlerts) {
        if (sendAlerts &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(token) {
        try {
            val answers = QuestionnaireApi.getQuestionnaire(token)
            profile = DemoProfile(answers.age, answers.sex, answers.cigarettesNow, answers.employmentStatus)
        } catch (e: QuestionnaireApiException) {
            if (e.statusCode == 401) onUnauthorized() else profileError = "Couldn't load the profile."
        } catch (_: Exception) {
            profileError = "Couldn't reach the server. Check your connection."
        }
    }

    // After "I just smoked", keep the minutes counting up on their own.
    LaunchedEffect(lastSmokedAt) {
        while (lastSmokedAt > 0L) {
            tslsText = ((System.currentTimeMillis() - lastSmokedAt) / 60_000L).toString()
            delay(15_000)
        }
    }

    val heartRate = heartRateText.toIntOrNull()
    val heartRateValid = noWatch || sleeping || (heartRate != null && heartRate in 30..220)
    val tsls = tslsText.toIntOrNull()
    val tslsValid = tsls != null && tsls in 0..10_000
    val inputsValid = heartRateValid && tslsValid && profile != null

    fun liveContext() = LiveContext(
        hour = hour,
        minute = minute,
        location = location,
        heartRateBpm = if (noWatch || sleeping) null else heartRate,
        isSleeping = sleeping,
        minutesSinceLastCigarette = tsls ?: 0,
    )

    fun errorText(e: Exception): String = when {
        e is PredictionApiException && e.statusCode == 401 -> {
            onUnauthorized() // token expired (about 1 hour): log in again
            "Session expired. Please log in again."
        }
        e is PredictionApiException && e.statusCode == 404 ->
            "No personalised model saved for this account yet. Tap Adapt first."
        e is PredictionApiException -> "Server error ${e.statusCode}: ${e.detail}"
        else -> "Couldn't reach the prediction server. Check your connection and try again."
    }

    // Recall / precision / F1 for this user, computed by the server on their days after day 1.
    fun loadEvaluation() {
        evaluation = EvalState(loading = true)
        scope.launch {
            evaluation = try {
                EvalState(result = api.evaluate())
            } catch (e: Exception) {
                EvalState(error = when {
                    e is PredictionApiException && e.statusCode == 401 -> {
                        onUnauthorized()
                        "Session expired. Please log in again."
                    }
                    e is PredictionApiException && e.statusCode == 422 -> e.detail
                    e is PredictionApiException -> "Server error ${e.statusCode}: ${e.detail}"
                    else -> "Couldn't reach the prediction server. Tap Refresh to try again."
                })
            }
        }
    }

    LaunchedEffect(token) { loadEvaluation() }

    fun maybeAlert(result: PredictionResult) {
        if (!sendAlerts || !result.willSmoke) return
        alertMessage = if (CravingNotifier.notifyCraving(context)) {
            "High risk: intervention notification sent."
        } else {
            "High risk, but the notification was blocked (1-min cooldown or permission off)."
        }
    }

    fun predictCold() {
        val live = liveContext()
        val p = profile ?: return
        cold = PanelState(loading = true)
        scope.launch {
            cold = try {
                val resting = restingHr ?: PredictionConfig.DEFAULT_RESTING_HR
                PanelState(result = api.predictColdStart(p, live, resting), basedOn = live.summary())
            } catch (e: Exception) {
                PanelState(error = errorText(e))
            }
        }
    }

    fun runAdaptation() {
        adapting = true
        adaptError = null
        scope.launch {
            try {
                val result = api.adapt() // server reads day 1 from Supabase sensor_data
                adaptResult = result
                result.restingHr?.let { hr ->
                    restingHr = hr
                    prefs.edit { putFloat(restingHrKey, hr.toFloat()) }
                }
                adapted = PanelState()
                loadEvaluation() // personalised column now uses the new model
            } catch (e: Exception) {
                adaptError = errorText(e)
            } finally {
                adapting = false
            }
        }
    }

    fun resetPersonalModel() {
        resetting = true
        adaptError = null
        scope.launch {
            try {
                api.deletePersonalModel()
                // Back to "never adapted": Predict now says "Tap Adapt first".
                // The resting HR is kept, so cold-start numbers stay the same.
                adaptResult = null
                adapted = PanelState()
                alertMessage = null
                loadEvaluation() // personalised column goes back to "—"
            } catch (e: Exception) {
                adaptError = errorText(e)
            } finally {
                resetting = false
            }
        }
    }

    fun predictAdapted() {
        val live = liveContext()
        val p = profile ?: return
        adapted = PanelState(loading = true)
        alertMessage = null
        scope.launch {
            adapted = try {
                // null: the server uses the resting HR it saved during /adapt
                val result = api.predictAdapted(p, live, restingHr)
                maybeAlert(result)
                PanelState(result = result, basedOn = live.summary())
            } catch (e: Exception) {
                PanelState(error = errorText(e))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Delete personal model?") },
            text = { Text("The model adapted to this user will be deleted. Tap Adapt to create it again.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    resetPersonalModel()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            },
        )
    }

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
            Text(text = "Meta Learning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        SectionTitle("User")
        ProfileCard(email = email, profile = profile, error = profileError, restingHr = restingHr)

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Live context (simulated sensors)")
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeRow(
                    hour = hour,
                    minute = minute,
                    onPick = {
                        TimePickerDialog(context, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                    },
                    onNow = {
                        val c = Calendar.getInstance()
                        hour = c.get(Calendar.HOUR_OF_DAY)
                        minute = c.get(Calendar.MINUTE)
                    },
                )

                LocationField(selected = location, onSelect = { location = it })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sleeping", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = sleeping, onCheckedChange = { sleeping = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = if (sleeping || noWatch) "" else heartRateText,
                        onValueChange = { input ->
                            if (input.length <= 3 && input.all(Char::isDigit)) heartRateText = input
                        },
                        enabled = !sleeping && !noWatch,
                        label = { Text("Heart rate (bpm)") },
                        placeholder = { if (sleeping) Text("Asleep") else if (noWatch) Text("No watch") },
                        isError = !heartRateValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = noWatch, onCheckedChange = { noWatch = it }, enabled = !sleeping)
                        Text("No watch", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = tslsText,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all(Char::isDigit)) {
                                tslsText = input
                                lastSmokedAt = 0L // typed by hand: stop the auto counter
                                prefs.edit { remove(KEY_LAST_SMOKED_AT) }
                            }
                        },
                        label = { Text("Last cigarette (min ago)") },
                        isError = !tslsValid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        val t = System.currentTimeMillis()
                        lastSmokedAt = t
                        tslsText = "0"
                        prefs.edit { putLong(KEY_LAST_SMOKED_AT, t) }
                    }) {
                        Text("I just smoked")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle("Prediction: smoking in the next 10 minutes")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultPanel(
                title = "Cold-start",
                subtitle = "General model, no personal data",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = ::predictCold,
                    enabled = inputsValid && !cold.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Predict") }
                ResultBody(cold)
            }

            ResultPanel(
                title = "Personalised",
                subtitle = "MAML adapted to this user's day 1",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                OutlinedButton(
                    onClick = ::runAdaptation,
                    enabled = !adapting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (adapting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adapting…")
                    } else {
                        Text(if (adaptResult == null) "Adapt" else "Adapt again")
                    }
                }
                adaptError?.let { ErrorText(it) }
                adaptResult?.let { AdaptSummary(it) }
                TextButton(
                    onClick = { showResetDialog = true },
                    enabled = !adapting && !resetting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (resetting) "Deleting…" else "Delete personal model")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = ::predictAdapted,
                    // Not gated on adaptResult: a model saved earlier (in Supabase) still works.
                    enabled = inputsValid && !adapted.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Predict") }
                ResultBody(adapted)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Send an alert when personalised risk is high",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = sendAlerts, onCheckedChange = { sendAlerts = it })
        }
        alertMessage?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = HighRiskColor)
        }

        Spacer(modifier = Modifier.height(16.dp))
        ModelResultsCard(state = evaluation, onRefresh = ::loadEvaluation)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ProfileCard(
    email: String,
    profile: DemoProfile?,
    error: String?,
    restingHr: Double?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = email.substringBefore("@").ifEmpty { "User" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (email.isNotEmpty()) {
                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(12.dp))
            when {
                error != null -> ErrorText(error)
                profile == null -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else -> {
                    val rows = listOf(
                        "Age" to profile.age?.toString(),
                        "Sex" to sexLabel(profile.sex),
                        "Cigarettes per day" to profile.cigarettesPerDay?.toInt()?.toString(),
                        "Employment" to employmentLabel(profile.employmentStatus),
                        "Resting HR" to (restingHr?.let { "${it.toInt()} bpm" } ?: "Set by Adapt"),
                    )
                    rows.forEachIndexed { i, (label, value) ->
                        if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row {
                            Text(
                                label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(value ?: "Not set", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(hour: Int, minute: Int, onPick: () -> Unit, onNow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Time", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onPick) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(String.format(Locale.US, "%02d:%02d", hour, minute))
        }
        TextButton(onClick = onNow) { Text("Now") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationField(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Location") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LOCATIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun AdaptSummary(result: AdaptResult) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "✓ Personalised on ${result.rowsUsed} slots (day 1)",
            style = MaterialTheme.typography.bodySmall,
            color = LowRiskColor,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "5 MAML steps · saved ${shortTime(result.updatedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2026-11-17T16:10:05.123+00:00" -> "17 Nov, 21:40" in the phone's time zone; raw text if it can't be parsed. */
private fun shortTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.US))
    }.getOrDefault(iso)
}

@Composable
private fun ResultBody(state: PanelState) {
    when {
        state.loading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        state.error != null -> ErrorText(state.error)
        state.result != null -> {
            val r = state.result
            val color = if (r.willSmoke) HighRiskColor else LowRiskColor
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${(r.probability * 100).toInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                LinearProgressIndicator(
                    progress = { r.probability.toFloat().coerceIn(0f, 1f) },
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (r.willSmoke) "High risk" else "Low risk",
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
                if (r.mode == "saved_model" && r.modelUpdatedAt != null) {
                    Text(
                        text = "model from ${shortTime(r.modelUpdatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "threshold ${(r.threshold * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.basedOn?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
