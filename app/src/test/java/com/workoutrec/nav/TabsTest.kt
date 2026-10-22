package com.workoutrec.nav

import com.workoutrec.Routes
import com.workoutrec.log.LogRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Feature 004 research R1, R2: tab order, where the bar shows, which tab a screen belongs to
class TabsTest {

    @Test
    fun tabsInBarOrder() =
        assertEquals(listOf(Tab.TODAY, Tab.HISTORY, Tab.EXERCISES, Tab.MONEY), Tab.entries.toList())

    @Test
    fun barHiddenOnLoggingPastWorkoutAndSetup() {
        assertFalse(barVisible(LogRoutes.LOGGING))
        assertFalse(barVisible(LogRoutes.PAST_WORKOUT))
        assertFalse(barVisible(Routes.SETUP))
        assertFalse(barVisible(null))
    }

    @Test
    fun barShownOnEveryOtherScreen() {
        listOf(Routes.HOME, LogRoutes.PICKER, LogRoutes.WORKOUTS, LogRoutes.DAY, TabRoutes.EXERCISES, TabRoutes.MONEY)
            .forEach { assertTrue(it, barVisible(it)) }
    }

    @Test
    fun everyScreenBelongsToItsTab() {
        assertEquals(Tab.TODAY, tabOf(Routes.HOME))
        assertEquals(Tab.TODAY, tabOf(LogRoutes.PICKER))
        assertEquals(Tab.TODAY, tabOf(LogRoutes.LOGGING))
        assertEquals(Tab.HISTORY, tabOf(LogRoutes.WORKOUTS))
        assertEquals(Tab.HISTORY, tabOf(LogRoutes.DAY))
        assertEquals(Tab.HISTORY, tabOf(LogRoutes.PAST_WORKOUT))
        assertEquals(Tab.EXERCISES, tabOf(TabRoutes.EXERCISES))
        assertEquals(Tab.MONEY, tabOf(TabRoutes.MONEY))
        assertEquals(null, tabOf(Routes.SETUP))
    }

    @Test
    fun eachTabStartsOnItsFirstScreen() {
        assertEquals(Routes.HOME, Tab.TODAY.startRoute)
        assertEquals(LogRoutes.WORKOUTS, Tab.HISTORY.startRoute)
        assertEquals(TabRoutes.EXERCISES, Tab.EXERCISES.startRoute)
        assertEquals(TabRoutes.MONEY, Tab.MONEY.startRoute)
    }
}
