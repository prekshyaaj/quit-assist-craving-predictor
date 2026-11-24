package com.example.feature.metalearning.impl

// Values match the training data exactly (column "Location" in the generated dataset).
// The server one-hot encodes these strings, so don't rename them.
internal val LOCATIONS = listOf(
    "Home",
    "Office",
    "Public Place",
    "Restaurant",
    "Entertainment",
    "Market",
    "Gym",
    "Construction Site",
    "Factory",
    "School",
    "Hospital",
)

internal const val DEFAULT_HEART_RATE = 72
internal const val DEFAULT_MINUTES_SINCE_CIGARETTE = 60

// Same codes and labels as the questionnaire (feature/login/impl/QuestionnaireOptions.kt).
private val SEX_LABELS = mapOf(1 to "Male", 2 to "Female")

private val EMPLOYMENT_LABELS = mapOf(
    10 to "Office or desk job",
    11 to "Service or retail",
    12 to "Office or desk job",
    13 to "Construction",
    14 to "Factory or transport",
    101 to "Unemployed",
    102 to "Between seasonal jobs",
    103 to "Retired",
    104 to "Unable to work (disability)",
    105 to "Caring for family",
    106 to "Student",
    108 to "Other",
)

internal fun sexLabel(code: Int?) = SEX_LABELS[code]

internal fun employmentLabel(code: Int?) = EMPLOYMENT_LABELS[code]
