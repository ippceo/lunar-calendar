package com.lunarcalendar.app

import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class EventType {
    /** 一次性日程 */
    ONCE,

    /** 每年按阳历重复（阳历生日） */
    YEARLY_SOLAR,

    /** 每年按农历重复（农历生日） */
    YEARLY_LUNAR
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
    val enabled: Boolean = true
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
    }

    companion object {
        fun fromJson(obj: JSONObject): CalEvent? = runCatching {
            CalEvent(
                id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = obj.optString("title"),
                type = EventType.valueOf(obj.optString("type", EventType.ONCE.name)),
                year = obj.optInt("year"),
                month = obj.optInt("month"),
                day = obj.optInt("day"),
                timeMinutes = obj.optInt("time", 9 * 60),
                note = obj.optString("note"),
                enabled = obj.optBoolean("enabled", true)
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
}
