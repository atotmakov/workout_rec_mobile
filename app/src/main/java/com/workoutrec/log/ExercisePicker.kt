package com.workoutrec.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workoutrec.R
import com.workoutrec.workout.Exercise

/** Test tags of contracts/screens.md "Exercise picker". */
object PickerTags {
    const val SCREEN = "exercise_picker"
    const val SEARCH = "exercise_search"
    const val RECENT = "exercise_recent"
    const val EMPTY = "exercise_list_empty"
    fun item(name: String) = "exercise_item_$name"
    fun recentItem(name: String) = "exercise_recent_item_$name"
}

/** Exercises of the drills tab, grouped by muscle group, with search and recent ones (FR-001). */
@Composable
fun ExercisePicker(
    exercises: List<Exercise>,
    recent: List<String>,
    onPick: (String) -> Unit,
    onOpenDrills: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(16.dp).testTag(PickerTags.SCREEN)) {
        if (exercises.isEmpty()) {
            Column(Modifier.testTag(PickerTags.EMPTY)) {
                Text(stringResource(R.string.picker_empty))
                TextButton(onClick = onOpenDrills) { Text(stringResource(R.string.picker_open_drills)) }
            }
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag(PickerTags.SEARCH),
        )
        val other = stringResource(R.string.picker_other_group)
        val filtered = exercises.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        val groups = filtered.groupBy { it.muscleGroup.ifBlank { other } }
        val recentShown = if (query.isBlank()) recent.filter { name -> exercises.any { it.name == name } } else emptyList()
        LazyColumn(Modifier.fillMaxWidth()) {
            if (recentShown.isNotEmpty()) {
                item {
                    Column(Modifier.testTag(PickerTags.RECENT)) {
                        Header(stringResource(R.string.picker_recent))
                        recentShown.forEach { name -> Row(name, PickerTags.recentItem(name), onPick) }
                    }
                }
            }
            groups.forEach { (group, items) ->
                item(key = "group:$group") { Header(group) }
                items(items, key = { "item:${it.name}" }) { Row(it.name, PickerTags.item(it.name), onPick) }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Row(name: String, tag: String, onPick: (String) -> Unit) {
    Text(
        name,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 12.dp).testTag(tag),
    )
}
