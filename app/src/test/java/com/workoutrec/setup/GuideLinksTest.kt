package com.workoutrec.setup

import org.junit.Assert.assertEquals
import org.junit.Test

// Step 1 opens as the app account via authuser. Step 2 (the Apps Script web app) only works for
// the browser's first account, so it gets NO authuser: it is opened in a private tab (or pasted
// into one) where the app account is the only, first account. Verified on a real phone.
class GuideLinksTest {

    private val exec = "https://script.google.com/macros/s/AKfy123/exec"

    @Test
    fun `enable page gets only the language`() {
        assertEquals("$exec?lang=ru", GuideLinks.enablePage(exec, language = "ru"))
    }

    @Test
    fun `any language other than Russian uses English`() {
        assertEquals("$exec?lang=en", GuideLinks.enablePage(exec, language = "de"))
    }

    @Test
    fun `existing query parameters are kept`() {
        assertEquals("$exec?x=1&lang=en", GuideLinks.enablePage("$exec?x=1", "en"))
    }

    @Test
    fun `Apps Script settings page opens as the app account`() {
        assertEquals(
            "https://script.google.com/home/usersettings?authuser=atotmakov%40gmail.com",
            GuideLinks.appsScriptSettings(email = "atotmakov@gmail.com"),
        )
    }

    @Test
    fun `without an account the settings page has no authuser`() {
        assertEquals("https://script.google.com/home/usersettings", GuideLinks.appsScriptSettings(email = null))
    }

    @Test
    fun `plus sign in an email is encoded`() {
        assertEquals(
            "https://script.google.com/home/usersettings?authuser=me%2Bgym%40example.com",
            GuideLinks.appsScriptSettings(email = "me+gym@example.com"),
        )
    }
}
