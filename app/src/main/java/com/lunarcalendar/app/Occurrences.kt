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
        val rule = event.repeat
        if (rule != null && event.type == EventType.ONCE) {
            val base = event.anchorDate() ?: return null
            var index = repeatIndexOnOrAfter(base, rule, from.toLocalDate())
            var guard = 0
            while (guard < 8) {
                val at = atTime(repeatOccurrence(base, rule, index), event.timeMinutes, zone)
                if (at.isAfter(from)) return at
                index++
                guard++
            }
            return null
        }
        return when (event.type) {
            EventType.ONCE -> event.anchorDate()?.let { atTime(it, event.timeMinutes, zone) }

            EventType.YEARLY_SOLAR,
            EventType.ANNIVERSARY -> (0..8).asSequence()
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

    /**
     * 某条「提前提醒」下一次的触发时间（事件日期前 [AdvanceReminder.daysBefore] 天的指定时刻）。
     * 一年前的日期也纳入候选，避免跨年时漏算尚未触发的那一次。
     */
    fun nextAdvanceTrigger(
        event: CalEvent,
        reminder: AdvanceReminder,
        from: ZonedDateTime = ZonedDateTime.now()
    ): ZonedDateTime? {
        if (!event.enabled) return null
        val year = from.toLocalDate().year
        val rule = event.repeat
        val dates = if (rule != null && event.type == EventType.ONCE) {
            (year - 1..year + 9).flatMap { datesIn(event, it) }
        } else when (event.type) {
            EventType.ONCE -> event.anchorDate()?.let { listOf(it) } ?: emptyList()

            EventType.YEARLY_SOLAR,
            EventType.ANNIVERSARY -> (year - 1..year + 9)
                .map { safeSolarDate(it, event.month, event.day) }

            EventType.YEARLY_LUNAR -> {
                val base = LunarUtils.info(from.toLocalDate()).lunarYear
                (base - 1..base + 9)
                    .mapNotNull { LunarUtils.solarFromLunar(it, event.month, event.day) }
            }
        }
        return dates
            .map { atTime(it, reminder.timeMinutes, from.zone).minusDays(reminder.daysBefore.toLong()) }
            .filter { it.isAfter(from) }
            .minOrNull()
    }

    /** 某一天是否会发生这个提醒（用于日历打点和当日列表）。 */
    fun occursOn(event: CalEvent, date: LocalDate): Boolean {
        val rule = event.repeat
        if (rule != null && event.type == EventType.ONCE) {
            val base = event.anchorDate() ?: return false
            if (date < base) return false
            return repeatOccurrence(base, rule, repeatIndexOnOrAfter(base, rule, date)) == date
        }
        return when (event.type) {
            EventType.ONCE -> runCatching {
                LocalDate.of(event.year, event.month, event.day) == date
            }.getOrDefault(false)

            EventType.YEARLY_SOLAR,
            EventType.ANNIVERSARY -> safeSolarDate(date.year, event.month, event.day) == date

            EventType.YEARLY_LUNAR -> {
                val lunarYear = LunarUtils.info(date).lunarYear
                LunarUtils.solarFromLunar(lunarYear, event.month, event.day) == date
            }
        }
    }

    /**
     * 某条提醒在公历 [year] 年内会发生的日期。
     *
     * 由事件反推日期，避免对 42 个格子逐日调用 [occursOn]（年视图 12 个月会放大 12 倍）。
     * 农历事件的候选农历年覆盖公历年边界，最后仍用 [occursOn] 校验，保持与逐日判定一致。
     */
    fun datesIn(event: CalEvent, year: Int): List<LocalDate> {
        val rule = event.repeat
        if (rule != null && event.type == EventType.ONCE) {
            val base = event.anchorDate() ?: return emptyList()
            val start = maxOf(base, LocalDate.of(year, 1, 1))
            val end = LocalDate.of(year, 12, 31)
            if (start > end) return emptyList()
            val result = ArrayList<LocalDate>()
            var index = repeatIndexOnOrAfter(base, rule, start)
            var guard = 0
            while (guard < 400) {
                val date = repeatOccurrence(base, rule, index)
                if (date > end) break
                result.add(date)
                index++
                guard++
            }
            return result
        }
        val candidates = when (event.type) {
            EventType.ONCE -> event.anchorDate()?.let { listOf(it) } ?: emptyList()

            EventType.YEARLY_SOLAR,
            EventType.ANNIVERSARY -> listOf(safeSolarDate(year, event.month, event.day))

            EventType.YEARLY_LUNAR -> (year - 1..year + 1)
                .mapNotNull { LunarUtils.solarFromLunar(it, event.month, event.day) }
        }
        return candidates.filter { it.year == year && occursOn(event, it) }
    }

    /** 多条提醒在若干公历年内的发生日期集合，供日历打点使用。 */
    fun datesIn(events: List<CalEvent>, years: IntRange): Set<LocalDate> {
        val result = HashSet<LocalDate>()
        for (year in years) {
            for (event in events) {
                result.addAll(datesIn(event, year))
            }
        }
        return result
    }

    /** 重复规则文案，例如 "每年 农历十月十五"。 */
    fun ruleText(event: CalEvent): String {
        val rule = event.repeat
        if (rule != null && event.type == EventType.ONCE) {
            return "${event.year}年${event.month}月${event.day}日起 ${rule.text}"
        }
        return when (event.type) {
            EventType.ONCE -> "${event.year}年${event.month}月${event.day}日"
            EventType.YEARLY_SOLAR -> "每年 ${event.month}月${event.day}日"
            EventType.YEARLY_LUNAR -> LunarUtils.lunarRuleText(event.year, event.month, event.day)
            EventType.ANNIVERSARY -> "每年 ${event.month}月${event.day}日（${event.year}年开始）"
        }
    }

    /** 提醒列表中的“下一次”文案。 */
    fun nextLabel(event: CalEvent, now: ZonedDateTime = ZonedDateTime.now()): String {
        if (!event.enabled) return "已关闭"
        if (event.type == EventType.ANNIVERSARY) {
            return anniversaryNextLabel(event, now)
        }
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

    /** 纪念日专用：显示"已经 X 天"或"已经 X 天 · N 周年"。 */
    private fun anniversaryNextLabel(event: CalEvent, now: ZonedDateTime): String {
        val start = runCatching { LocalDate.of(event.year, event.month, event.day) }.getOrNull()
            ?: return ruleText(event)
        val today = now.toLocalDate()
        val daysPassed = ChronoUnit.DAYS.between(start, today)
        if (daysPassed < 0) return "还有 ${-daysPassed} 天"
        // 已满整年数：正好满一年当天算 1 周年（不是 2 周年）。
        val yearsPassed = today.year - start.year -
            if (today.monthValue < start.monthValue ||
                (today.monthValue == start.monthValue && today.dayOfMonth < start.dayOfMonth)) 1 else 0
        return if (yearsPassed <= 0) {
            "已经 $daysPassed 天"
        } else {
            "已经 $daysPassed 天 · $yearsPassed 周年"
        }
    }

    /** 纪念日原始日期到今天的累计天数，可能为负数（还没到起始日）。 */
    fun anniversaryDaysPassed(event: CalEvent, today: LocalDate = LocalDate.now()): Long {
        val start = runCatching { LocalDate.of(event.year, event.month, event.day) }.getOrNull()
            ?: return 0L
        return ChronoUnit.DAYS.between(start, today)
    }

    fun weekdayName(date: LocalDate): String =
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[date.dayOfWeek.value - 1]

    fun fullDateText(date: LocalDate): String =
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 ${weekdayName(date)}"

    private fun atTime(date: LocalDate, minutes: Int, zone: ZoneId): ZonedDateTime =
        date.atStartOfDay(zone).plusMinutes(minutes.toLong())

    /** 重复事件的第 [index] 次（从 0 起）发生日期；月末日期由 plusMonths 自动收敛。 */
    private fun repeatOccurrence(base: LocalDate, rule: RepeatRule, index: Long): LocalDate {
        val step = rule.interval.coerceAtLeast(1).toLong()
        return when (rule.unit) {
            RepeatUnit.DAY -> base.plusDays(index * step)
            RepeatUnit.WEEK -> base.plusWeeks(index * step)
            RepeatUnit.MONTH -> base.plusMonths(index * step)
        }
    }

    /** 从 [from]（含当天）起第一次符合重复规则的序号，供 [repeatOccurrence] 换算日期。 */
    private fun repeatIndexOnOrAfter(base: LocalDate, rule: RepeatRule, from: LocalDate): Long {
        if (from <= base) return 0L
        val step = rule.interval.coerceAtLeast(1).toLong()
        val index = when (rule.unit) {
            RepeatUnit.DAY -> {
                val days = ChronoUnit.DAYS.between(base, from)
                (days + step - 1) / step
            }

            RepeatUnit.WEEK -> {
                val weeks = ChronoUnit.WEEKS.between(base, from)
                (weeks + step - 1) / step
            }

            RepeatUnit.MONTH -> {
                val months = ChronoUnit.MONTHS.between(base, from)
                (months + step - 1) / step
            }
        }
        // plusMonths 会把 31 日收敛到月末，个别情况下结果仍早于 from，最多再向后挪几次
        var current = index
        var guard = 0
        while (repeatOccurrence(base, rule, current) < from && guard < 4) {
            current++
            guard++
        }
        return current
    }

    private fun safeSolarDate(year: Int, month: Int, day: Int): LocalDate {
        val safeMonth = month.coerceIn(1, 12)
        val length = YearMonth.of(year, safeMonth).lengthOfMonth()
        return LocalDate.of(year, safeMonth, min(day, length).coerceAtLeast(1))
    }

}
