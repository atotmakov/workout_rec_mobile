package com.workoutrec.log

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.SyncNotice
import com.workoutrec.workout.DaySummary
import com.workoutrec.workout.DisplaySet
import com.workoutrec.workout.Exercise
import com.workoutrec.workout.ExerciseGroup
import com.workoutrec.workout.Reps
import com.workoutrec.workout.SetKey
import com.workoutrec.workout.SetRef
import com.workoutrec.workout.SyncState
import com.workoutrec.workout.Weight
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// contracts/screens.md "Past days", "Day detail", "Edit set dialog", notices; US4; FR-013
@RunWith(AndroidJUnit4::class)
class CorrectionsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int) = context.getString(id)

    private val key = SetKey(1_768_240_800, "squat", Weight.ofHundredths(4000), Reps(8))
    private val set = DisplaySet(key, SyncState.SYNCED, SetRef.Row(3, key))

    @Test
    fun pastDaysAreListedAndOpen() {
        var opened: LocalDate? = null
        val days = listOf(DaySummary(LocalDate.of(2026, 1, 10), 2, 7), DaySummary(LocalDate.of(2026, 1, 8), 1, 3))
        compose.setContent { PastDaysScreen(days = days, onOpenDay = { opened = it }) }
        compose.onNodeWithTag(CorrectionTags.pastDay(LocalDate.of(2026, 1, 10))).assertTextContains("7", substring = true)
        compose.onNodeWithTag(CorrectionTags.pastDay(LocalDate.of(2026, 1, 8))).performClick()
        assertEquals(LocalDate.of(2026, 1, 8), opened)
    }

    @Test
    fun dayDetailShowsSetsAndOpensOne() {
        var tapped: DisplaySet? = null
        compose.setContent { DayDetailScreen(groups = listOf(ExerciseGroup("squat", listOf(set))), onEditSet = { tapped = it }) }
        compose.onNodeWithTag(CorrectionTags.DAY_DETAIL).assertIsDisplayed()
        compose.onNodeWithTag(CorrectionTags.daySet(0, 0)).performClick()
        assertEquals(set, tapped)
    }

    @Test
    fun editSetChangesValuesButNotTheTime() {
        var saved: Triple<String, String, String>? = null
        compose.setContent {
            EditSetDialog(
                set = set,
                exercises = listOf(Exercise("squat", "legs"), Exercise("bench", "chest")),
                onSave = { exercise, weight, reps -> saved = Triple(exercise, weight, reps) },
                onDelete = {},
                onDismiss = {},
            )
        }
        compose.onNodeWithTag(CorrectionTags.EDIT_SET).assertIsDisplayed()
        compose.onNodeWithTag(CorrectionTags.EDIT_REPS).performTextReplacement("10")
        compose.onNodeWithTag(CorrectionTags.EDIT_SAVE).performClick()
        assertEquals("squat", saved!!.first)
        assertEquals("10", saved!!.third)
    }

    @Test
    fun deleteAsksForConfirmation() {
        var deleted = 0
        compose.setContent { EditSetDialog(set = set, exercises = emptyList(), onSave = { _, _, _ -> }, onDelete = { deleted++ }, onDismiss = {}) }
        compose.onNodeWithTag(CorrectionTags.EDIT_DELETE).performClick()
        assertEquals(0, deleted)
        compose.onNodeWithTag(CorrectionTags.DELETE_CONFIRM).performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun noticesExplainAndCanBeDismissed() {
        var dismissed: Long? = null
        val notices = listOf(7L to SyncNotice(NoticeKind.CONFLICT_DROPPED, key), 8L to SyncNotice(NoticeKind.WORKOUT_ROW_NOT_UPDATED, key))
        compose.setContent { SyncNoticeList(notices = notices, onDismiss = { dismissed = it }) }
        compose.onNodeWithTag(CorrectionTags.notice(7)).assertTextContains(s(R.string.notice_conflict_dropped).substringBefore("%").trim(), substring = true)
        compose.onNodeWithTag(CorrectionTags.notice(8)).assertIsDisplayed()
        compose.onNodeWithTag(CorrectionTags.noticeDismiss(8)).performClick()
        assertEquals(8L, dismissed)
    }
}
