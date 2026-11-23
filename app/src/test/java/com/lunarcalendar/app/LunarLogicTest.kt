package com.lunarcalendar.app

import com.nlf.calendar.Lunar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LunarLogicTest {

    @Test
    fun lunarInfoIsCorrect() {
        val info = LunarUtils.info(LocalDate.of(2026, 11, 23))
        assertEquals(2026, info.lunarYear)
        assertEquals(10, info.lunarMonth)
        assertEquals(15, info.lunarDay)
        assertEquals("十月", info.monthLabel)
        assertEquals("十五", info.dayName)
        assertEquals("丙午", info.ganZhiYear)
        assertEquals("农历丙午年 十月十五", info.fullLunarText)
    }

    @Test
    fun lunarToSolarRoundTrip() {
        val solar = LunarUtils.solarFromLunar(2026, 10, 15)
        assertEquals(LocalDate.of(2026, 11, 23), solar)

        val leap = LunarUtils.solarFromLunar(2023, -2, 1)
        assertEquals(LocalDate.of(2023, 3, 22), leap)
    }

    @Test
    fun gridLabelPrefersFestival() {
        val springFestival = LunarUtils.info(LocalDate.of(2027, 2, 6))
        assertEquals("春节", LunarUtils.gridLabel(springFestival))
    }

    @Test
    fun yearlyLunarEventOccursOnLunarDate() {
        val event = CalEvent(
            title = "生日",
            type = EventType.YEARLY_LUNAR,
            year = 2026,
            month = 10,
            day = 15
        )
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 11, 23)))
        assertFalse(Occurrences.occursOn(event, LocalDate.of(2026, 11, 24)))

        val expected2027 = Lunar.fromYmd(2027, 10, 15).solar
        val solar2027 = LocalDate.of(expected2027.year, expected2027.month, expected2027.day)
        assertTrue(Occurrences.occursOn(event, solar2027))
    }

    @Test
    fun leapMonthFallsBackWhenAbsent() {
        val event = CalEvent(
            title = "闰月生日",
            type = EventType.YEARLY_LUNAR,
            year = 2023,
            month = -2,
            day = 1
        )
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2023, 3, 22)))

        val regular = Lunar.fromYmd(2024, 2, 1).solar
        val regularDate = LocalDate.of(regular.year, regular.month, regular.day)
        assertTrue(Occurrences.occursOn(event, regularDate))
    }

    @Test
    fun leapDayBirthdayUsesFebruary28() {
        val event = CalEvent(
            title = "闰日生日",
            type = EventType.YEARLY_SOLAR,
            year = 2024,
            month = 2,
            day = 29
        )
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 2, 28)))
        assertFalse(Occurrences.occursOn(event, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun nextTriggerForLunarBirthday() {
        val event = CalEvent(
            title = "生日",
            type = EventType.YEARLY_LUNAR,
            year = 2026,
            month = 10,
            day = 15,
            timeMinutes = 9 * 60
        )
        val from = ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val next = Occurrences.nextTrigger(event, from)
        assertEquals(
            ZonedDateTime.of(2026, 11, 23, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            next
        )
    }

    @Test
    fun nextTriggerForYearlySolarSkipsToNextYear() {
        val event = CalEvent(
            title = "纪念日",
            type = EventType.YEARLY_SOLAR,
            year = 2020,
            month = 3,
            day = 1,
            timeMinutes = 20 * 60
        )
        val from = ZonedDateTime.of(2026, 3, 1, 21, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val next = Occurrences.nextTrigger(event, from)
        assertEquals(
            ZonedDateTime.of(2027, 3, 1, 20, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            next
        )
    }

    @Test
    fun lunarMonthAndDayLabelsAreStable() {
        assertEquals("十月", LunarUtils.monthLabelOf(10))
        assertEquals("闰二月", LunarUtils.monthLabelOf(-2))
        (1..12).forEach { m ->
            assertTrue(LunarUtils.monthLabelOf(m).isNotEmpty())
            assertTrue(LunarUtils.monthLabelOf(-m).startsWith("闰"))
        }
        assertEquals("初一", LunarUtils.dayNameOf(1))
        assertEquals("十五", LunarUtils.dayNameOf(15))
        (1..30).forEach { d -> assertTrue(LunarUtils.dayNameOf(d).isNotEmpty()) }
    }

    @Test
    fun lunarRuleTextDoesNotCrashForAbsentLeapMonth() {
        // 2024 年没有闰二月，ruleText 不应崩溃，且仍带上闰月标记
        val text = LunarUtils.lunarRuleText(2024, -2, 1)
        assertTrue(text.contains("闰"))
        assertTrue(text.contains("月"))
    }

    @Test
    fun anniversaryDaysPassedCountsFromOriginalDate() {
        val event = CalEvent(
            title = "love",
            type = EventType.ANNIVERSARY,
            year = 2023,
            month = 2,
            day = 5
        )
        assertEquals(0L, Occurrences.anniversaryDaysPassed(event, LocalDate.of(2023, 2, 5)))
        // 2023-02-05 到 2024-02-05 共 365 天（2024 闰日在 2 月之后已跨过）
        assertEquals(365L, Occurrences.anniversaryDaysPassed(event, LocalDate.of(2024, 2, 5)))
    }

    @Test
    fun anniversaryLabelUsesCompletedYearsOnly() {
        val event = CalEvent(
            title = "love",
            type = EventType.ANNIVERSARY,
            year = 2024,
            month = 1,
            day = 1
        )
        val startDay = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val startLabel = Occurrences.nextLabel(event, startDay)
        assertTrue(startLabel.contains("已经 0 天"))
        assertFalse("起始日不应出现周年", startLabel.contains("周年"))

        // 正好满一年：应是 1 周年，而不是 2 周年（修复前的 off-by-one）
        val firstAnniversary = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val label = Occurrences.nextLabel(event, firstAnniversary)
        assertTrue(label.contains("1 周年"))
        assertFalse(label.contains("2 周年"))
    }

    @Test
    fun advanceReminderTriggersDaysBeforeEvent() {
        val reminder = AdvanceReminder(daysBefore = 3, timeMinutes = 9 * 60)
        val event = CalEvent(
            title = "生日",
            type = EventType.YEARLY_SOLAR,
            year = 1990,
            month = 3,
            day = 10,
            advanceReminders = listOf(reminder)
        )
        val from = ZonedDateTime.of(2026, 3, 1, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(
            ZonedDateTime.of(2026, 3, 7, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            Occurrences.nextAdvanceTrigger(event, reminder, from)
        )
    }

    @Test
    fun advanceReminderRollsToNextYearWhenPassed() {
        val reminder = AdvanceReminder(daysBefore = 3, timeMinutes = 9 * 60)
        val event = CalEvent(
            title = "生日",
            type = EventType.YEARLY_SOLAR,
            year = 1990,
            month = 3,
            day = 10,
            advanceReminders = listOf(reminder)
        )
        val from = ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(
            ZonedDateTime.of(2027, 3, 7, 9, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            Occurrences.nextAdvanceTrigger(event, reminder, from)
        )
    }
}
