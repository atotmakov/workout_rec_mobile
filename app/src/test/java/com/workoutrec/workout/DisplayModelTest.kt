package com.workoutrec.workout

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// data-model.md "Display model", research R9, FR-010, FR-013
class DisplayModelTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 1, 12)

    private fun t(local: String) = LocalDateTime.parse(local).toEpochSecond(zone)
    private fun key(time: String, exercise: String, weight: Int = 20, reps: Int = 10) =
        SetKey(t(time), exercise, Weight.ofHundredths(weight * 100L), Reps(reps))

    private fun row(index: Int, key: SetKey) = LogRow(index, key)

    private fun new(id: String, key: SetKey, createdAt: Long = 0, draftId: String? = null) =
        PendingChange(id, ChangeKind.NEW, key, createdAt = createdAt, draftId = draftId)

    private fun edit(id: String, lastSeen: SetKey, hint: Int, newKey: SetKey) =
        PendingChange(id, ChangeKind.EDIT, newKey, lastSeen = lastSeen, rowHint = hint, createdAt = 0)

    private fun delete(id: String, lastSeen: SetKey, hint: Int) =
        PendingChange(id, ChangeKind.DELETE, lastSeen, lastSeen = lastSeen, rowHint = hint, createdAt = 0)

    private val a1 = key("2026-01-12T18:00:00", "squat")
    private val a2 = key("2026-01-12T18:03:00", "squat", reps = 8)
    private val b1 = key("2026-01-12T18:10:00", "bench")
    private val old = key("2026-01-10T19:00:00", "bench", weight = 40)

    @Test
    fun `cache rows are synced and pending new sets are not synced`() {
        val p = key("2026-01-12T18:20:00", "row")
        val sets = DisplayModel.sets(listOf(row(1, old), row(2, a1)), listOf(new("n1", p)))
        assertEquals(listOf(old, a1, p), sets.map { it.key })
        assertEquals(listOf(SyncState.SYNCED, SyncState.SYNCED, SyncState.NOT_SYNCED), sets.map { it.syncState })
        assertEquals(SetRef.Row(2, a1), sets[1].ref)
        assertEquals(SetRef.Pending("n1"), sets[2].ref)
    }

    @Test
    fun `a pending edit replaces the row values and is not synced`() {
        val edited = a1.copy(reps = Reps(12))
        val sets = DisplayModel.sets(listOf(row(1, a1), row(2, a2)), listOf(edit("e1", a1, 1, edited)))
        assertEquals(listOf(edited, a2), sets.map { it.key })
        assertEquals(SyncState.NOT_SYNCED, sets[0].syncState)
        assertEquals(SetRef.Pending("e1"), sets[0].ref)
    }

    @Test
    fun `a pending delete hides the row`() {
        val sets = DisplayModel.sets(listOf(row(1, a1), row(2, a2)), listOf(delete("d1", a1, 1)))
        assertEquals(listOf(a2), sets.map { it.key })
    }

    @Test
    fun `an edit or delete finds its row by values after rows moved in the sheet`() {
        // A row was inserted above in the web UI: a1 is now row 2, not row 1.
        val inserted = key("2026-01-12T17:00:00", "plank")
        val rows = listOf(row(1, inserted), row(2, a1), row(3, a2))
        assertEquals(listOf(inserted, a2), DisplayModel.sets(rows, listOf(delete("d1", a1, 1))).map { it.key })
    }

    @Test
    fun `draft sets of a past workout are not shown`() {
        val draft = key("2026-01-11T18:00:00", "squat")
        assertEquals(emptyList<SetKey>(), DisplayModel.sets(emptyList(), listOf(new("n1", draft, draftId = "d"))).map { it.key })
    }

    @Test
    fun `today groups sets by exercise in order of the first set`() {
        val a3 = key("2026-01-12T18:15:00", "squat", reps = 6)
        val sets = DisplayModel.sets(listOf(row(1, old), row(2, a1), row(3, a2), row(4, b1), row(5, a3)), emptyList())
        val groups = DisplayModel.today(sets, today, zone)
        assertEquals(listOf("squat", "bench"), groups.map { it.exercise })
        assertEquals(listOf(a1, a2, a3), groups[0].sets.map { it.key })
        assertEquals(listOf(b1), groups[1].sets.map { it.key })
    }

    @Test
    fun `past days are listed newest first with exercise and set counts`() {
        val older = key("2026-01-08T10:00:00", "squat")
        val olderB = key("2026-01-08T10:05:00", "squat")
        val sets = DisplayModel.sets(listOf(row(1, older), row(2, olderB), row(3, old), row(4, a1)), emptyList())
        assertEquals(
            listOf(DaySummary(LocalDate.of(2026, 1, 10), 1, 1), DaySummary(LocalDate.of(2026, 1, 8), 1, 2)),
            DisplayModel.pastDays(sets, today, zone),
        )
    }

    @Test
    fun `one day groups like today`() {
        val sets = DisplayModel.sets(listOf(row(1, old), row(2, a1)), emptyList())
        assertEquals(listOf("bench"), DisplayModel.day(sets, LocalDate.of(2026, 1, 10), zone).map { it.exercise })
    }

    @Test
    fun `recent exercises are ordered by last use, at most five`() {
        val keys = listOf("a", "b", "c", "d", "e", "f").mapIndexed { i, name -> key("2026-01-0${i + 1}T10:00:00", name) } +
            key("2026-01-09T10:00:00", "b")
        val sets = DisplayModel.sets(keys.mapIndexed { i, k -> row(i + 1, k) }, emptyList())
        assertEquals(listOf("b", "f", "e", "d", "c"), DisplayModel.recentExercises(sets))
    }

    @Test
    fun `pending count ignores drafts`() {
        val pending = listOf(new("n1", a1), delete("d1", old, 1), new("n2", b1, draftId = "d"))
        assertEquals(2, DisplayModel.pendingCount(pending))
    }

    @Test
    fun `row locator picks the matching row closest to the hint`() {
        val rows = listOf(row(1, a1), row(5, a2), row(9, a1))
        assertEquals(9, RowLocator.locate(rows, a1, hint = 8)?.rowIndex)
        assertEquals(1, RowLocator.locate(rows, a1, hint = 2)?.rowIndex)
        assertNull(RowLocator.locate(rows, b1, hint = 1))
    }
}
