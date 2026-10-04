package com.workoutrec.setup

import java.net.URLEncoder

/**
 * Links for the two one-time browser steps. Both must open as the account chosen in the app:
 * with several Google accounts in the browser, Apps Script otherwise uses the browser's default
 * account and shows "Sorry, unable to open the file at this time". `authuser=<email>` makes
 * script.google.com switch to that account (verified on a multi-account browser).
 */
object GuideLinks {
    const val APPS_SCRIPT_SETTINGS = "https://script.google.com/home/usersettings"

    /** Step 1: the user's Apps Script settings ("Google Apps Script API" switch). */
    fun appsScriptSettings(email: String?): String = withParams(APPS_SCRIPT_SETTINGS, accountParam(email))

    /** Step 2: the script's "Enable automation" page, localized by `lang` (contracts/apps-script.md). */
    fun enablePage(url: String, language: String, email: String?): String =
        withParams(url, listOf("lang" to if (language == "ru") "ru" else "en") + accountParam(email))

    private fun accountParam(email: String?) =
        email?.takeIf { it.isNotBlank() }?.let { listOf("authuser" to it) }.orEmpty()

    private fun withParams(url: String, params: List<Pair<String, String>>): String {
        if (params.isEmpty()) return url
        val query = params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
        return url + (if ('?' in url) "&" else "?") + query
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
