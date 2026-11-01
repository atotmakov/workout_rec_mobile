package com.workoutrec.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.workoutrec.data.SelectedAccount

sealed interface PickResult {
    data class Picked(val account: SelectedAccount) : PickResult
    data object Cancelled : PickResult
    /** [type]: the platform's error type, never user data (feature 005: reported). */
    data class Failed(val type: String) : PickResult
}

/** A failed account pick as a non-fatal event (feature 005); [type] names the platform error. */
class PickFailed(type: String) : Exception("Account pick failed: $type")

/** Shows Google's account chooser (research R2). */
fun interface AccountPicker {
    suspend fun pick(): PickResult
}

/** An explicit sign-in/switch button must also allow adding or reauthenticating an account. */
internal fun googleAccountRequest(webClientId: String): GetCredentialRequest =
    GetCredentialRequest.Builder().addCredentialOption(
        GetSignInWithGoogleOption.Builder(webClientId).build(),
    ).build()

/** Credential Manager "Sign in with Google"; [activityContext] must be an Activity. */
class CredentialManagerAccountPicker(
    private val activityContext: Context,
    private val webClientId: String,
) : AccountPicker {

    override suspend fun pick(): PickResult {
        val request = googleAccountRequest(webClientId)
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                PickResult.Picked(
                    GoogleCredentialMapper.toSelectedAccount(
                        id = google.id,
                        displayName = google.displayName,
                        profilePictureUri = google.profilePictureUri?.toString(),
                    ),
                )
            } else {
                PickResult.Failed("unexpected-credential: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            PickResult.Cancelled
        } catch (e: GetCredentialException) {
            PickResult.Failed(e.type)
        }
    }
}
