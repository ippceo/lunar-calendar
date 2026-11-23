package com.lunarcalendar.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** 提前提醒：在事件发生前 [daysBefore] 天的 [timeMinutes]（当天 0 点起的分钟数）提醒。 */
data class AdvanceReminder(
    val daysBefore: Int,
    val timeMinutes: Int
) {
    val timeText: String
        get() = "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60)
}

enum class EventType {
    /** 一次性日程 */
    ONCE,

    /** 每年按阳历重复（阳历生日） */
    YEARLY_SOLAR,

    /** 每年按农历重复（农历生日） */
    YEARLY_LUNAR,

    /** 纪念日：每年重复，同时显示「已经 X 天」（从原始事件日期算到今天的累计天数） */
    ANNIVERSARY
}

/**
 * 事件/提醒。
 *
 * - [EventType.ONCE] / [EventType.YEARLY_SOLAR]：[year]/[month]/[day] 是阳历日期。
 * - [EventType.YEARLY_LUNAR]：[year] 是农历年，[month] 是农历月（负数表示闰月），[day] 是农历日。
 */
data class CalEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: EventType,
    val year: Int,
    val month: Int,
    val day: Int,
    val timeMinutes: Int = 9 * 60,
    val note: String = "",
    val enabled: Boolean = true,
    /** 提前提醒列表（最多 7 条，分别对应 1~7 天前）。 */
    val advanceReminders: List<AdvanceReminder> = emptyList()
) {
    val timeText: String
        get() = "%02d:%02d".format(timeMinutes / 60, timeMinutes % 60)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("type", type.name)
        put("year", year)
        put("month", month)
        put("day", day)
        put("time", timeMinutes)
        put("note", note)
        put("enabled", enabled)
        if (advanceReminders.isNotEmpty()) {
            put("advance", JSONArray().apply {
                advanceReminders.forEach { item ->
                    put(JSONObject().put("d", item.daysBefore).put("t", item.timeMinutes))
                }
            })
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): CalEvent? = runCatching {
            val advance = obj.optJSONArray("advance")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val item = arr.optJSONObject(i) ?: return@mapNotNull null
                    AdvanceReminder(
                        daysBefore = item.optInt("d").coerceIn(1, 7),
                        timeMinutes = item.optInt("t", 9 * 60).coerceIn(0, 24 * 60 - 1)
                    )
                }.distinctBy { it.daysBefore }.sortedBy { it.daysBefore }
            } ?: emptyList()
            CalEvent(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = obj.optString("title"),
                type = EventType.valueOf(obj.optString("type", EventType.ONCE.name)),
                year = obj.optInt("year"),
                month = obj.optInt("month"),
                day = obj.optInt("day"),
                timeMinutes = obj.optInt("time", 9 * 60),
                note = obj.optString("note"),
                enabled = obj.optBoolean("enabled", true),
                advanceReminders = advance
            )
        }.getOrNull()
    }
}

/** 事件对应的阳历锚点日期（农历生日会转换成当年对应的阳历日期）。 */
fun CalEvent.anchorDate(): LocalDate? = when (type) {
    EventType.YEARLY_LUNAR -> LunarUtils.solarFromLunar(year, month, day)
    else -> runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

fun CalEvent.typeLabel(): String = when (type) {
    EventType.ONCE -> "一次性日程"
    EventType.YEARLY_SOLAR -> "每年 · 阳历"
    EventType.YEARLY_LUNAR -> "每年 · 农历"
    EventType.ANNIVERSARY -> "纪念日（每年重复）"
}
