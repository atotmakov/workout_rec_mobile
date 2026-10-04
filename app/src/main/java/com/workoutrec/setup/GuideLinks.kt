package com.workoutrec.setup

import java.net.URLEncoder

/**
 * Links for the two one-time browser steps (research R14).
 *
 * Step 1 (Apps Script settings) is a normal Google page: `authuser=<email>` opens it as the app
 * account. Step 2 (the script's "Enable automation" web app) only works for the browser's FIRST
 * signed-in account; `authuser` cannot fix that (it would select a later account and fail with
 * "unable to open the file"). So step 2 gets no account parameter and is opened in a private tab,
 * or pasted into one, where the app account is the only account. Verified on a real phone.
 */
object GuideLinks {
    const val APPS_SCRIPT_SETTINGS = "https://script.google.com/home/usersettings"

    fun appsScriptSettings(email: String?): String =
        withParams(APPS_SCRIPT_SETTINGS, email?.takeIf { it.isNotBlank() }?.let { listOf("authuser" to it) }.orEmpty())

    /** The Enable page is localized by `lang` (contracts/apps-script.md). */
    fun enablePage(url: String, language: String): String =
        withParams(url, listOf("lang" to if (language == "ru") "ru" else "en"))

    private fun withParams(url: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return url
        val query = params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name())}" }
        return url + (if ('?' in url) "&" else "?") + query
    }
}
