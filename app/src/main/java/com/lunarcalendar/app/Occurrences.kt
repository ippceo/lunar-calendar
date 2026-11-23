package com.lunarcalendar.app

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** 计算重复日程、生日下一次发生的时间和展示文案。 */
object Occurrences {

    /** 下一次触发时间；一次性日程即使已经过去也会返回原时间，由调用方判断。 */
    fun nextTrigger(event: CalEvent, from: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        if (!event.enabled) return null
        val zone = from.zone
        return when (event.type) {
            EventType.ONCE -> event.anchorDate()?.let { atTime(it, event.timeMinutes, zone) }

            EventType.YEARLY_SOLAR -> (0..8).asSequence()
                .map { offset -> safeSolarDate(from.year + offset, event.month, event.day) }
                .map { date -> atTime(date, event.timeMinutes, zone) }
                .firstOrNull { it.isAfter(from) }

            EventType.YEARLY_LUNAR -> {
                val startLunarYear = LunarUtils.info(from.toLocalDate()).lunarYear
                (0..8).asSequence()
                    .mapNotNull { offset ->
                        LunarUtils.solarFromLunar(startLunarYear + offset, event.month, event.day)
                    }
                    .map { date -> atTime(date, event.timeMinutes, zone) }
                    .firstOrNull { it.isAfter(from) }
            }
        }
    }

    /** 某一天是否会发生这个提醒（用于日历打点和当日列表）。 */
    fun occursOn(event: CalEvent, date: LocalDate): Boolean = when (event.type) {
        EventType.ONCE -> runCatching {
            LocalDate.of(event.year, event.month, event.day) == date
        }.getOrDefault(false)

        EventType.YEARLY_SOLAR -> safeSolarDate(date.year, event.month, event.day) == date

        EventType.YEARLY_LUNAR -> {
            val lunarYear = LunarUtils.info(date).lunarYear
            LunarUtils.solarFromLunar(lunarYear, event.month, event.day) == date
        }
    }

    /** 重复规则文案，例如 "每年 农历十月十五"。 */
    fun ruleText(event: CalEvent): String = when (event.type) {
        EventType.ONCE -> "${event.year}年${event.month}月${event.day}日"
        EventType.YEARLY_SOLAR -> "每年 ${event.month}月${event.day}日"
        EventType.YEARLY_LUNAR -> LunarUtils.lunarRuleText(event.year, event.month, event.day)
    }

    /** 提醒列表中的“下一次”文案。 */
    fun nextLabel(event: CalEvent, now: ZonedDateTime = ZonedDateTime.now()): String {
        if (!event.enabled) return "已关闭"
        val next = nextTrigger(event, now) ?: return "已结束"
        if (event.type == EventType.ONCE && !next.isAfter(now)) return "已过期"
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), next.toLocalDate())
        val prefix = when {
            days < 0 -> "已过期"
            days == 0L -> "今天"
            days == 1L -> "明天"
            days == 2L -> "后天"
            else -> "${days}天后"
        }
        return "$prefix ${next.monthValue}月${next.dayOfMonth}日 ${event.timeText}"
    }

    fun weekdayName(date: LocalDate): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]

    fun fullDateText(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayName(date)}"

    private fun atTime(date: LocalDate, minutes: Int, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(zone).plusMinutes(minutes.toLong())

    private fun safeSolarDate(year: Int, month: Int, day: Int): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        val length = YearMonth.of(year, safeMonth).lengthOfMonth()
        return LocalDate.of(year, safeMonth, min(day, length).coerceAtLeast(1))
    }

}
