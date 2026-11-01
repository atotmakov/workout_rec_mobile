package com.workoutrec.auth

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AccountPickerRequestTest {
    @Test
    fun `explicit account selection uses the Google button flow`() {
        val request = googleAccountRequest("test-client.apps.googleusercontent.com")
        assertEquals(1, request.credentialOptions.size)
        val option = request.credentialOptions.single()
        assertTrue(option is GetSignInWithGoogleOption)
        assertEquals("test-client.apps.googleusercontent.com", (option as GetSignInWithGoogleOption).serverClientId)
    }
}
