package com.workoutrec.setup

import org.junit.Assert.assertEquals
import org.junit.Test

// Step 1/2 pages must open as the account chosen in the app, not the browser's default account
// (otherwise Apps Script shows "Sorry, unable to open the file at this time").
class GuideLinksTest {

    private val exec = "https://script.google.com/macros/s/AKfy123/exec"

    @Test
    fun `enable page gets the language and the app account`() {
        assertEquals(
            "$exec?lang=ru&authuser=atotmakov%40gmail.com",
            GuideLinks.enablePage(exec, language = "ru", email = "atotmakov@gmail.com"),
        )
    }

    @Test
    fun `any language other than Russian uses English`() {
        assertEquals("$exec?lang=en&authuser=a%40b.com", GuideLinks.enablePage(exec, language = "de", email = "a@b.com"))
    }

    @Test
    fun `existing query parameters are kept`() {
        assertEquals("$exec?x=1&lang=en&authuser=a%40b.com", GuideLinks.enablePage("$exec?x=1", "en", "a@b.com"))
    }

    @Test
    fun `without an account no authuser is added`() {
        assertEquals("$exec?lang=ru", GuideLinks.enablePage(exec, "ru", email = null))
        assertEquals("https://script.google.com/home/usersettings", GuideLinks.appsScriptSettings(email = null))
    }

    @Test
    fun `Apps Script settings page opens as the app account`() {
        assertEquals(
            "https://script.google.com/home/usersettings?authuser=atotmakov%40gmail.com",
            GuideLinks.appsScriptSettings(email = "atotmakov@gmail.com"),
        )
    }

    @Test
    fun `plus sign in an email is encoded`() {
        assertEquals(
            "https://script.google.com/home/usersettings?authuser=me%2Bgym%40example.com",
            GuideLinks.appsScriptSettings(email = "me+gym@example.com"),
        )
    }
}
