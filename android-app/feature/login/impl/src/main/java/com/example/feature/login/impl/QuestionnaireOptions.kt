package com.example.feature.login.impl

// Codes match the NHIS training data the model was built on.
internal const val MAX_AGE = 85          // 85 means "85 or older"
internal const val MAX_CIGARETTES = 95   // 95 means "95 or more"

internal val SEX_OPTIONS = listOf(1 to "Male", 2 to "Female")

internal val EMPLOYMENT_OPTIONS = listOf(
    10 to "Office or desk job",
    11 to "Service or retail",
    12 to "Office or desk job",  // Same persona as 10; never offered, but may be stored.
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

/** Options shown in the questionnaire dropdown. */
internal val EMPLOYMENT_CHOICES = EMPLOYMENT_OPTIONS.filter { it.first != 12 }

internal fun sexLabel(code: Int?) = SEX_OPTIONS.firstOrNull { it.first == code }?.second

internal fun employmentLabel(code: Int?) = EMPLOYMENT_OPTIONS.firstOrNull { it.first == code }?.second
