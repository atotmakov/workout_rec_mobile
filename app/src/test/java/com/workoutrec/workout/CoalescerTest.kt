package com.workoutrec.workout

import org.junit.Assert.assertEquals
import org.junit.Test

// data-model.md "Coalescing" table; FR-006 (time never edited), FR-013a
class CoalescerTest {

    private val row = SetKey(1_700_000_000, "squat", Weight.ofHundredths(2000), Reps(10))
    private val values = SetValues("bench", Weight.ofHundredths(2500), Reps(8))
    private val edited = SetKey(row.time, "bench", Weight.ofHundredths(2500), Reps(8))

    private fun new(key: SetKey = row) = PendingChange("n1", ChangeKind.NEW, key, createdAt = 1)
    private fun edit() = PendingChange("e1", ChangeKind.EDIT, row.copy(reps = Reps(12)), lastSeen = row, rowHint = 4, createdAt = 1)

    @Test
    fun `editing a synced row creates an edit that remembers the row`() {
        val change = Coalescer.edit(SetRef.Row(4, row), emptyList(), values, newId = "x", now = 9)
        assertEquals(PendingUpdate.Upsert(PendingChange("x", ChangeKind.EDIT, edited, lastSeen = row, rowHint = 4, createdAt = 9)), change)
    }

    @Test
    fun `deleting a synced row creates a delete of that row`() {
        val change = Coalescer.delete(SetRef.Row(4, row), emptyList(), newId = "x", now = 9)
        assertEquals(PendingUpdate.Upsert(PendingChange("x", ChangeKind.DELETE, row, lastSeen = row, rowHint = 4, createdAt = 9)), change)
    }

    @Test
    fun `editing a new set changes its values and keeps it new`() {
        val change = Coalescer.edit(SetRef.Pending("n1"), listOf(new()), values, newId = "x", now = 9)
        assertEquals(PendingUpdate.Upsert(new(edited)), change)
    }

    @Test
    fun `deleting a new set removes it`() {
        assertEquals(PendingUpdate.Remove("n1"), Coalescer.delete(SetRef.Pending("n1"), listOf(new()), newId = "x", now = 9))
    }

    @Test
    fun `editing an edit keeps the row it remembers`() {
        val change = Coalescer.edit(SetRef.Pending("e1"), listOf(edit()), values, newId = "x", now = 9)
        assertEquals(PendingUpdate.Upsert(edit().copy(key = edited)), change)
    }

    @Test
    fun `deleting an edit becomes a delete of the remembered row`() {
        val change = Coalescer.delete(SetRef.Pending("e1"), listOf(edit()), newId = "x", now = 9)
        assertEquals(PendingUpdate.Upsert(edit().copy(kind = ChangeKind.DELETE, key = row)), change)
    }

    @Test
    fun `the date-time is never changed by an edit`() {
        val change = Coalescer.edit(SetRef.Row(4, row), emptyList(), values, newId = "x", now = 9) as PendingUpdate.Upsert
        assertEquals(row.time, change.change.key.time)
    }
}
