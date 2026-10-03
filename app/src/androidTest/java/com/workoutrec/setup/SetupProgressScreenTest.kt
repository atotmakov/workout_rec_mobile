package com.workoutrec.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.google.ApiError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// FR-011: clear message and Retry
@RunWith(AndroidJUnit4::class)
class SetupProgressScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    @Test
    fun progressIsShownWhileWorking() {
        compose.setContent { SetupProgressScreen(error = null, onRetry = {}) }
        compose.onNodeWithTag(SetupProgressTag).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.setup_working)).assertIsDisplayed()
    }

    @Test
    fun offlineShowsInternetNeededAndRetry() {
        var retries = 0
        compose.setContent { SetupProgressScreen(error = ApiError.Offline, onRetry = { retries++ }) }
        compose.onNodeWithText(s(R.string.setup_error_offline)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.setup_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test
    fun serviceAndAccessErrorsShowTheirMessages() {
        compose.setContent { SetupProgressScreen(error = ApiError.ServiceUnavailable(503), onRetry = {}) }
        compose.onNodeWithText(s(R.string.setup_error_service)).assertIsDisplayed()
    }

    @Test
    fun accessDeniedShowsItsMessage() {
        compose.setContent { SetupProgressScreen(error = ApiError.AccessDenied("no"), onRetry = {}) }
        compose.onNodeWithText(s(R.string.setup_error_access)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.setup_retry)).assertIsDisplayed()
    }
}
