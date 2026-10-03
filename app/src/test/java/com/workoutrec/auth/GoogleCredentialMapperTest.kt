package com.workoutrec.auth

import com.workoutrec.data.SelectedAccount
import org.junit.Assert.assertEquals
import org.junit.Test

// research.md R2; data-model.md SelectedAccount
class GoogleCredentialMapperTest {

    @Test
    fun `maps id, display name and picture`() {
        assertEquals(
            SelectedAccount(email = "a@example.com", displayName = "Alex T", photoUrl = "https://p/a.png"),
            GoogleCredentialMapper.toSelectedAccount(
                id = "a@example.com",
                displayName = "Alex T",
                profilePictureUri = "https://p/a.png",
            ),
        )
    }

    @Test
    fun `display name and picture may be missing`() {
        assertEquals(
            SelectedAccount(email = "a@example.com", displayName = null, photoUrl = null),
            GoogleCredentialMapper.toSelectedAccount(id = "a@example.com", displayName = null, profilePictureUri = null),
        )
    }

    @Test
    fun `blank display name is treated as missing`() {
        val account = GoogleCredentialMapper.toSelectedAccount(id = "a@example.com", displayName = "  ", profilePictureUri = null)
        assertEquals(null, account.displayName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank email is an error`() {
        GoogleCredentialMapper.toSelectedAccount(id = "", displayName = "X", profilePictureUri = null)
    }
}
