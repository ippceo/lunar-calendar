package com.lunarcalendar.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 提醒数据的导出/导入。
 *
 * 导出为带版本号的 JSON，通过 SAF（用户选文件位置）写入，不需要存储权限。
 * 导入时按事件 id 合并：同 id 覆盖，新 id 追加，避免换机重复导入产生重复。
 */
object DataBackup {
    private const val APP = "lunar-calendar"
    private const val VERSION = 1

    fun exportJson(events: List<CalEvent>): String = JSONObject().apply {
        put("app", APP)
        put("version", VERSION)
        put("exportedAt", Instant.now().toString())
        val arr = JSONArray()
        events.forEach { arr.put(it.toJson()) }
        put("events", arr)
    }.toString(2)

    /** 解析备份 JSON，失败返回 null。 */
    fun parseJson(text: String): List<CalEvent>? = runCatching {
        val obj = JSONObject(text)
        if (obj.optString("app") != APP) return null
        val arr = obj.optJSONArray("events") ?: return null
        (0 until arr.length()).mapNotNull { index ->
            arr.optJSONObject(index)?.let { CalEvent.fromJson(it) }
        }
    }.getOrNull()

    fun writeToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        true
    }.getOrDefault(false)

    fun readFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()

    /**
     * 把导入的事件按 id 合并到现有列表：同 id 用导入版覆盖，新 id 追加。
     * 返回合并后的列表。
     */
    fun merge(existing: List<CalEvent>, imported: List<CalEvent>): List<CalEvent> {
        val byId = existing.associateBy { it.id }.toMutableMap()
        imported.forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun backupFileName(): String =
        "lunar-calendar-backup-${java.time.LocalDate.now()}.json"
}
