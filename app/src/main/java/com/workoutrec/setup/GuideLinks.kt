package com.workoutrec.setup

import java.net.URLEncoder

object GuideLinks {
    const val APPS_SCRIPT_SETTINGS = "https://script.google.com/home/usersettings"

    fun appsScriptSettings(email: String?): String =
        email?.takeIf { it.isNotBlank() }
            ?.let { "$APPS_SCRIPT_SETTINGS?authuser=${URLEncoder.encode(it, Charsets.UTF_8.name())}" }
            ?: APPS_SCRIPT_SETTINGS

    fun enablePage(url: String, language: String): String = TODO()
}
