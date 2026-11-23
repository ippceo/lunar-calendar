package com.lunarcalendar.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** 一份备份文件的内容：提醒事件 + 待办。 */
data class BackupData(
    val events: List<CalEvent>,
    val todos: List<TodoItem>
)

/**
 * 提醒与待办数据的导出/导入。
 *
 * 导出为带版本号的 JSON，通过 SAF（用户选文件位置）写入，不需要存储权限。
 * 导入时按 id 合并：同 id 覆盖，新 id 追加，避免换机重复导入产生重复。
 */
object DataBackup {
    private const val APP = "lunar-calendar"
    private const val VERSION = 2

    fun exportJson(events: List<CalEvent>, todos: List<TodoItem>): String = JSONObject().apply {
        put("app", APP)
        put("version", VERSION)
        put("exportedAt", Instant.now().toString())
        val eventArr = JSONArray()
        events.forEach { eventArr.put(it.toJson()) }
        put("events", eventArr)
        val todoArr = JSONArray()
        todos.forEach { todoArr.put(it.toJson()) }
        put("todos", todoArr)
    }.toString(2)

    /** 解析备份 JSON，失败返回 null。旧版（只有 events）也能导入。 */
    fun parseJson(text: String): BackupData? = runCatching {
        val obj = JSONObject(text)
        if (obj.optString("app") != APP) return null
        val events = obj.optJSONArray("events")?.let { arr ->
            (0 until arr.length()).mapNotNull { index ->
                arr.optJSONObject(index)?.let { CalEvent.fromJson(it) }
            }
        } ?: emptyList()
        val todos = obj.optJSONArray("todos")?.let { arr ->
            (0 until arr.length()).mapNotNull { index ->
                arr.optJSONObject(index)?.let { TodoItem.fromJson(it) }
            }
        } ?: emptyList()
        if (obj.optJSONArray("events") == null && obj.optJSONArray("todos") == null) return null
        BackupData(events, todos)
    }.getOrNull()

    fun writeToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        true
    }.getOrDefault(false)

    fun readFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()

    /** 按 id 合并提醒：同 id 用导入版覆盖，新 id 追加。 */
    fun merge(existing: List<CalEvent>, imported: List<CalEvent>): List<CalEvent> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        imported.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    /** 按 id 合并待办：同 id 用导入版覆盖，新 id 追加。 */
    fun mergeTodos(existing: List<TodoItem>, imported: List<TodoItem>): List<TodoItem> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        imported.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun backupFileName(): String =
        "lunar-calendar-backup-${java.time.LocalDate.now()}.json"
}
