package com.workoutrec.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.workout.DaySummary
import java.time.LocalDate

/** Test tags of feature 004 contracts/screens.md "History". */
object HistoryTags {
    const val PAST_WORKOUT = "history_past_workout"
    const val EMPTY = "past_days_empty"
    const val EMPTY_PAST_WORKOUT = "history_past_workout_empty"
}

/** The History tab's first screen: past days, and entering a past workout (feature 004 FR-006). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    days: List<DaySummary>,
    onOpenDay: (LocalDate) -> Unit,
    onPastWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    /** The sync icon and the account avatar. */
    actions: @Composable () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_history)) },
                actions = {
                    IconButton(onClick = onPastWorkout, modifier = Modifier.testTag(HistoryTags.PAST_WORKOUT)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.menu_past_workout))
                    }
                    actions()
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            Column(
                Modifier.padding(padding).padding(16.dp).testTag(HistoryTags.EMPTY),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.past_days_empty))
                FilledTonalButton(onClick = onPastWorkout, modifier = Modifier.testTag(HistoryTags.EMPTY_PAST_WORKOUT)) {
                    Text(stringResource(R.string.menu_past_workout))
                }
            }
        } else {
            PastDaysScreen(days, onOpenDay, Modifier.padding(padding))
        }
    }
}
