package com.example.feature.login.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.feature.login.api.QuestionnaireApi
import com.example.feature.login.api.QuestionnaireApiException
import com.example.feature.login.api.QuestionnaireData
import kotlinx.coroutines.launch

@Composable
fun QuestionnaireScreen(
    token: String,
    onComplete: () -> Unit,
    onUnauthorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var ageText by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<Int?>(null) }
    var cigarettesText by rememberSaveable { mutableStateOf("") }
    var employment by rememberSaveable { mutableStateOf<Int?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val age = ageText.toIntOrNull()
    val cigarettes = cigarettesText.toIntOrNull()
    val canSubmit = age != null && sex != null && cigarettes != null && employment != null && !isSaving

    fun submit() {
        if (age == null || age < 18) {
            error = "You need to be 18 or older to use this app."
            return
        }
        if (cigarettes == null || cigarettes < 1) {
            error = "Enter at least 1 cigarette per day."
            return
        }
        val answers = QuestionnaireData(
            age = age.coerceAtMost(MAX_AGE),
            sex = sex,
            cigarettesNow = cigarettes.coerceAtMost(MAX_CIGARETTES).toDouble(),
            employmentStatus = employment,
        )
        error = null
        isSaving = true
        scope.launch {
            try {
                QuestionnaireApi.saveQuestionnaire(token, answers)
                onComplete()
            } catch (e: QuestionnaireApiException) {
                if (e.statusCode == 401) {
                    onUnauthorized()
                } else {
                    error = "Couldn't save your answers. Try again."
                }
            } catch (_: Exception) {
                error = "Couldn't reach the server. Check your connection."
            } finally {
                isSaving = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About you", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Four quick questions to tailor your plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            NumberField(label = "Age", value = ageText, onValueChange = { ageText = it })

            Field(label = "Sex") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SEX_OPTIONS.forEachIndexed { index, (code, label) ->
                        SegmentedButton(
                            selected = sex == code,
                            onClick = { sex = code },
                            shape = SegmentedButtonDefaults.itemShape(index, SEX_OPTIONS.size),
                        ) { Text(label) }
                    }
                }
            }

            NumberField(
                label = "Cigarettes per day",
                value = cigarettesText,
                onValueChange = { cigarettesText = it },
            )

            EmploymentField(selected = employment, onSelect = { employment = it })

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Button(
            onClick = ::submit,
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(52.dp),
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 3 && input.all(Char::isDigit)) onValueChange(input) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmploymentField(selected: Int?, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = employmentLabel(selected).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Employment") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EMPLOYMENT_CHOICES.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}
