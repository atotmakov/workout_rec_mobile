package com.workoutrec.log

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.workoutrec.R
import com.workoutrec.sync.NoticeKind
import com.workoutrec.sync.SyncNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Feature 004 research R5: notices about exercise changes the sync could not apply
@RunWith(AndroidJUnit4::class)
class TodayNoticesTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun exerciseNoticesNameTheExerciseAndCanBeDismissed() {
        var dismissed: Long? = null
        val notices = listOf(
            1L to SyncNotice.exercise(NoticeKind.EXERCISE_CHANGE_DROPPED, "Bench"),
            2L to SyncNotice.exercise(NoticeKind.EXERCISE_IN_USE, "Plank"),
        )
        compose.setContent { SyncNoticeList(notices = notices, onDismiss = { dismissed = it }) }
        compose.onNodeWithTag(CorrectionTags.notice(1)).assertTextContains(context.getString(R.string.notice_exercise_change_dropped, "Bench"))
        compose.onNodeWithTag(CorrectionTags.notice(2)).assertTextContains(context.getString(R.string.notice_exercise_in_use, "Plank"))
        compose.onNodeWithTag(CorrectionTags.noticeDismiss(2)).performClick()
        assertEquals(2L, dismissed)
    }
}
