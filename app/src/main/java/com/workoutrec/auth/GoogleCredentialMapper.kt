package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount

/** Maps a Sign in with Google credential to [SelectedAccount] (research R2). */
object GoogleCredentialMapper {
    fun toSelectedAccount(id: String, displayName: String?, profilePictureUri: String?): SelectedAccount =
        SelectedAccount(
            email = id.trim(),
            displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
            photoUrl = profilePictureUri?.takeIf { it.isNotBlank() },
        )
}
