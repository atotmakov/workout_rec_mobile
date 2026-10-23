package com.workoutrec.log

import androidx.annotation.StringRes
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.workoutrec.R
import com.workoutrec.workout.DayStatus

@get:StringRes
val DayStatus.label: Int
    get() = if (this == DayStatus.FREE) R.string.day_status_free else R.string.day_status_paid

/**
 * Feature 006 contracts/screens.md: Paid | Free for one date; a tap on the other option saves at
 * once. Options are tagged [tag] + `_paid` / `_free`.
 */
@Composable
fun DayStatusControl(status: DayStatus, onChange: (DayStatus) -> Unit, tag: String, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.day_status_title)
    SingleChoiceSegmentedButtonRow(modifier.testTag(tag).semantics { contentDescription = title }) {
        listOf(DayStatus.PAID, DayStatus.FREE).forEachIndexed { index, option ->
            SegmentedButton(
                selected = status == option,
                onClick = { if (status != option) onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                modifier = Modifier.testTag("${tag}_${option.name.lowercase()}"),
            ) { Text(stringResource(option.label)) }
        }
    }
}
