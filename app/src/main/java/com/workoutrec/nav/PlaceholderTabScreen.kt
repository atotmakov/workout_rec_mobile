package com.workoutrec.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.workoutrec.R

/** A tab whose screens are not built yet (feature 004 tasks: MVP strategy). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderTabScreen(@StringRes title: Int, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(title)) }, actions = { actions() }) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag("tab_placeholder"), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.tab_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
