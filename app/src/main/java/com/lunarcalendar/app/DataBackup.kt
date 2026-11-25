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

    /** 用 "wt" 模式截断写入，避免覆盖长文件时残留尾部字节；流为 null 时返回失败。 */
    fun writeToUri(context: Context, uri: Uri, text: String): Boolean = runCatching {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: return@runCatching false
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
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

    /** 导入策略：覆盖 = 用文件内容整体替换；合并 = 同 id 以文件为准、其余保留。 */
    enum class ImportStrategy { OVERWRITE, MERGE }

    fun applyImport(
        existing: List<CalEvent>,
        imported: List<CalEvent>,
        strategy: ImportStrategy
    ): List<CalEvent> = when (strategy) {
        ImportStrategy.OVERWRITE -> imported
        ImportStrategy.MERGE -> merge(existing, imported)
    }

    fun applyImportTodos(
        existing: List<TodoItem>,
        imported: List<TodoItem>,
        strategy: ImportStrategy
    ): List<TodoItem> = when (strategy) {
        ImportStrategy.OVERWRITE -> imported
        ImportStrategy.MERGE -> mergeTodos(existing, imported)
    }

    fun backupFileName(): String =
        "lunar-calendar-backup-${java.time.LocalDate.now()}.json"

    // ============ 自动备份：应用私有目录，滚动保留最近数份 ============

    private const val BACKUP_DIR = "backups"
    private const val AUTO_PREFIX = "auto-"
    const val AUTO_KEEP = 7

    /** 一份自动备份的概要。 */
    data class AutoBackup(
        val fileName: String,
        val events: Int,
        val todos: Int,
        val time: java.time.LocalDateTime
    )

    /**
     * 数据变化后自动备份到应用私有目录（filesDir/backups），
     * 滚动保留最近 [AUTO_KEEP] 份。失败静默，不影响主流程。
     */
    fun autoBackup(context: Context, events: List<CalEvent>, todos: List<TodoItem>) {
        runCatching {
            val dir = java.io.File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
            val stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            java.io.File(dir, "$AUTO_PREFIX$stamp.json").writeText(exportJson(events, todos))
            pruneBackups(dir)
        }
    }

    /**
     * 纯函数：按文件名升序保留最新 [keep] 份，返回应删除的文件名。
     * 只处理 auto-*.json，避免误删用户放进去的其他文件。
     */
    fun pruneBackupNames(names: List<String>, keep: Int): List<String> {
        val valid = names.filter { it.startsWith(AUTO_PREFIX) && it.endsWith(".json") }.sorted()
        return if (valid.size <= keep) emptyList() else valid.take(valid.size - keep)
    }

    private fun pruneBackups(dir: java.io.File) {
        pruneBackupNames(dir.list()?.toList() ?: emptyList(), AUTO_KEEP).forEach { name ->
            runCatching { java.io.File(dir, name).delete() }
        }
    }

    /** 列出所有自动备份概要，最新在前；解析失败的文件跳过。 */
    fun listAutoBackups(context: Context): List<AutoBackup> = runCatching {
        val dir = java.io.File(context.filesDir, BACKUP_DIR)
        val files = dir.listFiles { f ->
            f.name.startsWith(AUTO_PREFIX) && f.name.endsWith(".json")
        } ?: return emptyList()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        files.mapNotNull { file ->
            val data = parseJson(file.readText()) ?: return@mapNotNull null
            AutoBackup(
                fileName = file.name,
                events = data.events.size,
                todos = data.todos.size,
                time = runCatching {
                    java.time.LocalDateTime.parse(
                        file.name.removePrefix(AUTO_PREFIX).removeSuffix(".json"),
                        formatter
                    )
                }.getOrDefault(java.time.LocalDateTime.MIN)
            )
        }.sortedByDescending { it.time }
    }.getOrDefault(emptyList())

    /** 读取一份自动备份；只接受合法文件名，避免路径穿越。 */
    fun readAutoBackup(context: Context, fileName: String): BackupData? {
        if (!fileName.startsWith(AUTO_PREFIX) || !fileName.endsWith(".json")) return null
        if (fileName.contains('/') || fileName.contains('\\')) return null
        return runCatching {
            parseJson(java.io.File(java.io.File(context.filesDir, BACKUP_DIR), fileName).readText())
        }.getOrNull()
    }
}
