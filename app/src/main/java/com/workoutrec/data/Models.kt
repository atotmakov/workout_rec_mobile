package com.workoutrec.data

/** data-model.md SelectedAccount */
data class SelectedAccount(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
)

/** data-model.md SpreadsheetBinding */
data class SpreadsheetBinding(
    val accountEmail: String,
    val spreadsheetId: String,
    val scriptId: String?,
    val enableUrl: String?,
)
