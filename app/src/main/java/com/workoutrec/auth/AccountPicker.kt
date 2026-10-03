package com.workoutrec.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.workoutrec.data.SelectedAccount

sealed interface PickResult {
    data class Picked(val account: SelectedAccount) : PickResult
    data object Cancelled : PickResult
    data class Failed(val reason: String) : PickResult
}

/** Shows Google's account chooser (research R2). */
fun interface AccountPicker {
    suspend fun pick(): PickResult
}

/** Credential Manager "Sign in with Google"; [activityContext] must be an Activity. */
class CredentialManagerAccountPicker(
    private val activityContext: Context,
    private val webClientId: String,
) : AccountPicker {

    override suspend fun pick(): PickResult {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setServerClientId(webClientId)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
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
                PickResult.Failed("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            PickResult.Cancelled
        } catch (e: GetCredentialException) {
            PickResult.Failed(e.message ?: e.type)
        }
    }
}
