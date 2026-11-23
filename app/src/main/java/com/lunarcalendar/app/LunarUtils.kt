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

    private val cache = HashMap<LocalDate, Info>()

    @Synchronized
    fun info(date: LocalDate): Info = cache.getOrPut(date) {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar
        Info(
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

    /** 生成农历重复事件的文字描述，例如 "每年 农历十月十五"。 */
    fun lunarRuleText(year: Int, month: Int, day: Int): String = runCatching {
        val lunar = Lunar.fromYmd(year, month, day)
        "每年 农历${lunar.monthInChinese}月${lunar.dayInChinese}"
    }.getOrElse { "每年 农历" }
}
