package com.workoutrec

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The launch screen is drawn from the window theme before Compose; it must match the app's colors. */
@RunWith(AndroidJUnit4::class)
class LaunchThemeTest {

    private fun windowBackground(nightMode: Int): Int {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val context = app.createConfigurationContext(config)
        val theme = context.resources.newTheme().apply {
            applyStyle(app.packageManager.getApplicationInfo(app.packageName, 0).theme, true)
        }
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.windowBackground, value, true)
        return value.data
    }

    @Test
    fun theLaunchScreenIsDarkInDarkMode() {
        assertEquals(0xFF1C1B1F.toInt(), windowBackground(Configuration.UI_MODE_NIGHT_YES))
    }

    @Test
    fun theLaunchScreenIsLightInLightMode() {
        assertEquals(0xFFFFFBFE.toInt(), windowBackground(Configuration.UI_MODE_NIGHT_NO))
    }
}
