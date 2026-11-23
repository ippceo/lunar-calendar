package com.lunarcalendar.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodoLogicTest {

    private fun todo(
        title: String,
        due: LocalDateTime? = null,
        done: Boolean = false
    ) = TodoItem(id = title, title = title, dueAt = due, done = done)

    @Test
    fun `todo survives json round trip`() {
        val original = TodoItem(
            id = "t1",
            title = "交房租",
            note = "转给房东",
            dueAt = LocalDateTime.of(2026, 12, 1, 9, 30),
            done = false
        )
        val restored = TodoItem.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `todo without due date restores null`() {
        val restored = TodoItem.fromJson(todo("买牛奶").toJson())
        assertNull(restored?.dueAt)
    }

    @Test
    fun `sorting puts pending first by due date and done last`() {
        val list = listOf(
            todo("done", LocalDateTime.of(2026, 1, 1, 9, 0), done = true),
            todo("no date"),
            todo("later", LocalDateTime.of(2026, 5, 1, 9, 0)),
            todo("sooner", LocalDateTime.of(2026, 3, 1, 9, 0))
        )
        assertEquals(
            listOf("sooner", "later", "no date", "done"),
            list.sortedForDisplay().map { it.title }
        )
    }

    @Test
    fun `backup contains todos and parses back`() {
        val json = DataBackup.exportJson(
            events = emptyList(),
            todos = listOf(todo("买牛奶"), todo("交房租", LocalDateTime.of(2026, 12, 1, 9, 30)))
        )
        val parsed = DataBackup.parseJson(json)
        assertEquals(2, parsed?.todos?.size)
    }

    @Test
    fun `merge todos keeps existing and adds imported`() {
        val existing = listOf(todo("旧待办"))
        val imported = listOf(todo("旧待办", done = true), todo("新待办"))
        val merged = DataBackup.mergeTodos(existing, imported)
        assertEquals(2, merged.size)
        assertTrue(merged.first { it.title == "旧待办" }.done)
    }
}
