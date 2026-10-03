package com.workoutrec.data

/** data-model.md SelectedAccount: email "Required; non-empty"; the other fields may be missing. */
data class SelectedAccount(
    val email: String,
    val displayName: String?,
    val photoUrl: String?,
) {
    init {
        require(email.isNotBlank()) { "email is required" }
    }
}

/**
 * data-model.md SpreadsheetBinding: a cache of the account's spreadsheet and script.
 * `accountEmail` must equal `SelectedAccount.email`.
 */
data class SpreadsheetBinding(
    val accountEmail: String,
    val spreadsheetId: String,
    val scriptId: String?,
    val enableUrl: String?,
)
