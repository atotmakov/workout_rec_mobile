package com.workoutrec

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theAppHasItsOwnAdaptiveLauncherIcon() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotEquals("android:icon is not set", 0, info.icon)
        assertTrue(context.packageManager.getApplicationIcon(info) is AdaptiveIconDrawable)
    }
}
