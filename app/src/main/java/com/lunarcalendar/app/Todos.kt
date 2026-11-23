package com.lunarcalendar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.UUID

/** 待办事项；[dueAt] 为空表示不设时间，也就不会提醒。 */
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val dueAt: LocalDateTime? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("note", note)
        put("done", done)
        put("created", createdAt)
        if (dueAt != null) put("due", dueAt.toString())
    }

    companion object {
        fun fromJson(obj: JSONObject): TodoItem? = runCatching {
            TodoItem(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = obj.optString("title"),
                note = obj.optString("note"),
                dueAt = obj.optString("due").takeIf { it.isNotBlank() }
                    ?.let { LocalDateTime.parse(it) },
                done = obj.optBoolean("done", false),
                createdAt = obj.optLong("created", 0L)
            )
        }.getOrNull()
    }
}

/** 待办列表的本地存储，与提醒事件共用同一份 SharedPreferences 文件。 */
object TodoRepository {
    private const val PREFS = "lunar_calendar"
    private const val KEY_TODOS = "todos"

    fun load(context: Context): List<TodoItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TODOS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { TodoItem.fromJson(it) }?.let {
                    it.copy(
                        title = it.title.take(MAX_TEXT_LENGTH),
                        note = it.note.take(MAX_TEXT_LENGTH)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, todos: List<TodoItem>) {
        val array = JSONArray()
        todos.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TODOS, array.toString())
            .apply()
    }
}

/** 未完成在前（有时间的按时间升序，无时间的排后），已完成置底。 */
fun List<TodoItem>.sortedForDisplay(): List<TodoItem> = sortedWith(
    compareBy<TodoItem> { it.done }
        .thenBy { it.dueAt == null }
        .thenBy { it.dueAt }
)
