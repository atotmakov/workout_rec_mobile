package com.workoutrec.home

import org.junit.Assert.assertEquals
import org.junit.Test

// data-model.md: "first letter of the first two words of displayName, else the first letter
// of email, upper-cased"
class InitialsTest {

    @Test
    fun `two words give two letters`() {
        assertEquals("AT", initials("Alexey Totmakov", "a@example.com"))
    }

    @Test
    fun `only the first two words count`() {
        assertEquals("JR", initials("john ronald reuel", "j@example.com"))
    }

    @Test
    fun `single word gives one letter`() {
        assertEquals("M", initials("madonna", "m@example.com"))
    }

    @Test
    fun `extra spaces are ignored`() {
        assertEquals("AB", initials("  anna   bell ", "x@example.com"))
    }

    @Test
    fun `null or blank name falls back to the email`() {
        assertEquals("Z", initials(null, "zed@example.com"))
        assertEquals("Z", initials("   ", "zed@example.com"))
    }

    @Test
    fun `non-latin names are upper-cased`() {
        assertEquals("АТ", initials("алексей тотмаков", "a@example.com"))
    }
}
