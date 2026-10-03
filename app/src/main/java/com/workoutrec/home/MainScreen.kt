package com.workoutrec.home

import androidx.compose.runtime.Composable
import com.workoutrec.data.SelectedAccount

const val MainScreenTag = "main_screen"
const val AccountAvatarTag = "account_avatar"

@Composable
fun MainScreen(account: SelectedAccount, onSwitchAccount: () -> Unit, onSignOut: () -> Unit) {
}

@Composable
fun AccountAvatar(account: SelectedAccount, onClick: () -> Unit) {
}
