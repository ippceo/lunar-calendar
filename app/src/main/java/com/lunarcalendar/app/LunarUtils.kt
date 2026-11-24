package com.lunarcalendar.app

import com.nlf.calendar.Lunar
import com.nlf.calendar.LunarYear
import com.nlf.calendar.Solar
import java.time.LocalDate
import kotlin.math.abs

/** 农历/节气/节日的查询工具，数据完全离线。 */
object LunarUtils {

    data class Info(
        val lunarYear: Int,
        val lunarMonth: Int,
        val lunarDay: Int,
        val monthName: String,
        val dayName: String,
        val ganZhiYear: String,
        val zodiac: String,
        val jieQi: String,
        val festivals: List<String>,
        val otherFestivals: List<String>
    ) {
        val monthLabel: String
            get() = if (lunarMonth < 0) "闰${monthName}月" else "${monthName}月"

        val fullLunarText: String
            get() = "农历${ganZhiYear}年 $monthLabel$dayName"

        val isHighlightDay: Boolean
            get() = jieQi.isNotEmpty() || festivals.isNotEmpty()
    }

    /** 最多缓存约 5 年的日期，避免左右浏览多年后无界增长。 */
    private const val MAX_CACHE = 2048

    private val cache = object : LinkedHashMap<LocalDate, Info>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, Info>?): Boolean =
            size > MAX_CACHE
    }

    @Synchronized
    fun info(date: LocalDate): Info {
        cache[date]?.let { return it }
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar
        val info = Info(
            lunarYear = lunar.year,
            lunarMonth = lunar.month,
            lunarDay = lunar.day,
            monthName = lunar.monthInChinese,
            dayName = lunar.dayInChinese,
            ganZhiYear = lunar.yearInGanZhi,
            zodiac = lunar.yearShengXiao,
            jieQi = lunar.jieQi,
            festivals = (lunar.festivals + solar.festivals).distinct(),
            otherFestivals = lunar.otherFestivals.distinct()
        )
        cache[date] = info
        return info
    }

    /** 日历格子里的农历小字：节日 > 节气 > 初一显示月份 > 农历日。 */
    fun gridLabel(info: Info): String = when {
        info.festivals.isNotEmpty() -> info.festivals.first()
        info.jieQi.isNotEmpty() -> info.jieQi
        info.lunarDay == 1 -> info.monthLabel
        else -> info.dayName
    }

    /**
     * 农历 -> 阳历。
     *
     * [month] 为负数表示闰月；若该农历年没有这个闰月，则退化为同名平月。
     * 若该月没有 30 天，则把日期收敛到当月最后一天。
     */
    fun solarFromLunar(lunarYear: Int, month: Int, day: Int): LocalDate? {
        if (month == 0 || day <= 0) return null
        val leap = month < 0
        val monthNumber = abs(month)
        val leapMonth = runCatching { LunarYear.fromYear(lunarYear).leapMonth }.getOrDefault(0)
        val useMonth = if (leap && leapMonth != monthNumber) monthNumber else month

        var useDay = day
        val dayCount = runCatching { LunarYear.fromYear(lunarYear).getMonth(useMonth)?.dayCount }
            .getOrNull()
        if (dayCount != null && useDay > dayCount) useDay = dayCount

        return runCatching {
            val solar = Lunar.fromYmd(lunarYear, useMonth, useDay).solar
            LocalDate.of(solar.year, solar.month, solar.day)
        }.getOrNull()
    }

    private val plainMonthNames: List<String> by lazy {
        (1..12).map { m ->
            runCatching { Lunar.fromYmd(2000, m, 1).monthInChinese }.getOrDefault(m.toString())
        }
    }

    private val lunarDayNames: List<String> by lazy {
        (1..30).map { d ->
            (1998..2002).asSequence()
                .flatMap { y -> (1..12).asSequence().map { m -> y to m } }
                .mapNotNull { (y, m) -> runCatching { Lunar.fromYmd(y, m, d).dayInChinese }.getOrNull() }
                .firstOrNull() ?: d.toString()
        }
    }

    /** 农历月名（含闰月前缀），与具体年份无关：10->"十月"，-2->"闰二月"。 */
    fun monthLabelOf(month: Int): String {
        val name = plainMonthNames[abs(month).coerceIn(1, 12) - 1]
        return if (month < 0) "闰${name}月" else "${name}月"
    }

    /** 农历日名，与年月无关：1->"初一"，15->"十五"，30->"三十"。 */
    fun dayNameOf(day: Int): String = lunarDayNames[day.coerceIn(1, 30) - 1]

    /** 生成农历重复事件的文字描述，例如 "每年 农历十月十五"。 */
    fun lunarRuleText(year: Int, month: Int, day: Int): String =
        "每年 农历${monthLabelOf(month)}${dayNameOf(day)}"

    /**
     * 距下一个节气（24 节气，含中气）的名称与天数。
     * 当天正好是节气时返回 (名称, 0)。库异常时返回 null。
     */
    fun nextSolarTerm(from: LocalDate): Pair<String, Int>? = runCatching {
        val lunar = Solar.fromYmd(from.year, from.monthValue, from.dayOfMonth).lunar
        val jq = lunar.nextJieQi
        val solar = jq.solar
        val target = LocalDate.of(solar.year, solar.month, solar.day)
        jq.name to java.time.temporal.ChronoUnit.DAYS.between(from, target).toInt()
    }.getOrNull()
}
