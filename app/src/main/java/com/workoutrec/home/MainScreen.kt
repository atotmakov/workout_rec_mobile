package com.workoutrec.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.workoutrec.R
import com.workoutrec.data.SelectedAccount

const val MainScreenTag = "main_screen"
const val AccountAvatarTag = "account_avatar"

/** Main screen with the selected account in the top-right corner (FR-003). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    account: SelectedAccount,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
    content: @Composable (Modifier) -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(MainScreenTag),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        AccountAvatar(account = account, onClick = { menuOpen = true })
                        AccountMenu(
                            account = account,
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            onSwitchAccount = { menuOpen = false; onSwitchAccount() },
                            onSignOut = { menuOpen = false; onSignOut() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

/** Profile photo, or initials when there is none (FR-003). */
@Composable
fun AccountAvatar(account: SelectedAccount, onClick: () -> Unit) {
    val description = stringResource(R.string.account_menu_description)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(AccountAvatarTag),
        contentAlignment = Alignment.Center,
    ) {
        if (account.photoUrl != null) {
            AsyncImage(
                model = account.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials(account.displayName, account.email),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

/** Name, email, switch account, sign out (FR-005). */
@Composable
fun AccountMenu(
    account: SelectedAccount,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSwitchAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        account.displayName?.let { name ->
            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Text(
            account.email,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        DropdownMenuItem(text = { Text(stringResource(R.string.account_switch)) }, onClick = onSwitchAccount)
        DropdownMenuItem(text = { Text(stringResource(R.string.account_sign_out)) }, onClick = onSignOut)
    }
}
