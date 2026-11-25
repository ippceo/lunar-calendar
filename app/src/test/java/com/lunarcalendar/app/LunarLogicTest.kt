package com.lunarcalendar.app

import com.nlf.calendar.Lunar
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
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
    fun lunarBirthdayAgeUsesSolarBirthYear() {
        val event = CalEvent(
            title = "腊月生日",
            type = EventType.YEARLY_LUNAR,
            year = 1985,
            month = 12,
            day = 3
        )
        val birthDate = event.anchorDate()
        assertEquals(1986, birthDate?.year)

        val from = ZonedDateTime.of(2026, 11, 24, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        val nextDate = Occurrences.nextTrigger(event, from)?.toLocalDate()
        assertEquals(2027, nextDate?.year)
        assertEquals(41, event.birthdayAge(nextDate!!))
    }

    @Test
    fun eventDatesMatchDayScan() {
        // 年视图打点改为按事件展开，必须与逐日判定完全一致
        val events = listOf(
            CalEvent(title = "农历生日", type = EventType.YEARLY_LUNAR, year = 1990, month = 10, day = 15),
            CalEvent(title = "闰月生日", type = EventType.YEARLY_LUNAR, year = 2014, month = -9, day = 1),
            CalEvent(title = "阳历生日", type = EventType.YEARLY_SOLAR, year = 2024, month = 2, day = 29),
            CalEvent(title = "一次性", type = EventType.ONCE, year = 2025, month = 6, day = 1),
            CalEvent(
                title = "每两周",
                type = EventType.ONCE,
                year = 2024,
                month = 1,
                day = 3,
                repeat = RepeatRule(RepeatUnit.WEEK, 2)
            ),
            CalEvent(
                title = "每月",
                type = EventType.ONCE,
                year = 2024,
                month = 1,
                day = 31,
                repeat = RepeatRule(RepeatUnit.MONTH, 1)
            ),
            CalEvent(title = "纪念日", type = EventType.ANNIVERSARY, year = 2023, month = 2, day = 5)
        )
        for (year in 2024..2026) {
            val expected = generateSequence(LocalDate.of(year, 1, 1)) { it.plusDays(1) }
                .takeWhile { it.year == year }
                .filter { date -> events.any { Occurrences.occursOn(it, date) } }
                .toSet()
            assertEquals("$year 年打点日期不一致", expected, Occurrences.datesIn(events, year..year))
        }
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
    fun repeatEveryTwoWeeksSkipsOffWeeks() {
        val event = CalEvent(
            title = "双周会",
            type = EventType.ONCE,
            year = 2026,
            month = 11,
            day = 2,
            timeMinutes = 10 * 60,
            repeat = RepeatRule(RepeatUnit.WEEK, 2)
        )
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 11, 2)))
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 11, 16)))
        assertFalse(Occurrences.occursOn(event, LocalDate.of(2026, 11, 9)))
        // 基准日之前不触发
        assertFalse(Occurrences.occursOn(event, LocalDate.of(2026, 10, 19)))

        val from = ZonedDateTime.of(2026, 11, 3, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(
            ZonedDateTime.of(2026, 11, 16, 10, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            Occurrences.nextTrigger(event, from)
        )
    }

    @Test
    fun repeatMonthlyFromMonthEndKeepsMonthEnd() {
        val event = CalEvent(
            title = "月底对账",
            type = EventType.ONCE,
            year = 2026,
            month = 1,
            day = 31,
            repeat = RepeatRule(RepeatUnit.MONTH, 1)
        )
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 2, 28)))
        assertFalse(Occurrences.occursOn(event, LocalDate.of(2026, 3, 28)))
        assertTrue(Occurrences.occursOn(event, LocalDate.of(2026, 3, 31)))
    }

    @Test
    fun repeatRuleSurvivesJsonRoundTrip() {
        val event = CalEvent(
            title = "每三天",
            type = EventType.ONCE,
            year = 2026,
            month = 1,
            day = 1,
            repeat = RepeatRule(RepeatUnit.DAY, 3)
        )
        val restored = CalEvent.fromJson(event.toJson())
        assertEquals(RepeatRule(RepeatUnit.DAY, 3), restored?.repeat)
        assertEquals(null, CalEvent.fromJson(
            CalEvent(title = "普通", type = EventType.ONCE, year = 2026, month = 1, day = 1).toJson()
        )?.repeat)
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

    @Test
    fun importClampsOutOfRangeTime() {
        val obj = JSONObject().apply {
            put("type", "ONCE")
            put("year", 2026)
            put("month", 6)
            put("day", 1)
            put("time", 1440)
        }
        val event = CalEvent.fromJson(obj)
        assertTrue(event != null)
        assertTrue(event!!.timeMinutes in 0..(24 * 60 - 1))
    }

    @Test
    fun backupRoundTripPreservesData() {
        val events = listOf(
            CalEvent(
                title = "测试",
                type = EventType.ONCE,
                year = 2026,
                month = 6,
                day = 1,
                note = "备注"
            )
        )
        val todos = listOf(
            TodoItem(title = "待办", note = "备注", done = true)
        )
        val text = DataBackup.exportJson(events, todos)
        val restored = DataBackup.parseJson(text)
        assertTrue(restored != null)
        assertEquals(1, restored!!.events.size)
        assertEquals("测试", restored.events[0].title)
        assertEquals(1, restored.todos.size)
        assertTrue(restored.todos[0].done)
    }

    @Test
    fun atTimeUsesWallClockNotElapsedMinutes() {
        // 纽约 2026-03-08 02:00 跳夏令时，本地 09:00 应仍然是 09:00，不是 10:00
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        val result = date.atTime(9, 0).atZone(zone)
        assertEquals(LocalDateTime.of(2026, 3, 8, 9, 0), result.toLocalDateTime())
    }

    @Test
    fun dailyRepeatReschedulesNextDay() {
        // 配合 Receivers 无条件续排的回归：到点触发 1 分钟后，应能算出明天的同一次时间
        val event = CalEvent(
            title = "吃药",
            type = EventType.ONCE,
            year = 2026,
            month = 5,
            day = 1,
            timeMinutes = 8 * 60,
            repeat = RepeatRule(RepeatUnit.DAY, 1)
        )
        val firedAt = ZonedDateTime.of(2026, 5, 10, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai"))
        assertEquals(
            ZonedDateTime.of(2026, 5, 11, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            Occurrences.nextTrigger(event, firedAt.plusMinutes(1))
        )
        // 一次性过期事件：nextTrigger 返回原时间（过去），ReminderScheduler.schedule()
        // 里的 isAfter(now) 检查会拦住它，不会设出过去的闹钟
        val once = CalEvent(
            title = "过期",
            type = EventType.ONCE,
            year = 2026,
            month = 5,
            day = 1,
            timeMinutes = 8 * 60
        )
        val pastTrigger = Occurrences.nextTrigger(once, firedAt.plusMinutes(1))
        assertEquals(
            ZonedDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai")),
            pastTrigger
        )
        assertTrue(pastTrigger!!.isBefore(firedAt))
    }

    @Test
    fun pruneBackupNamesKeepsLatest() {
        val names = (1..9).map { "auto-20261124-00000$it.json" }
        val removed = DataBackup.pruneBackupNames(names, 7)
        assertEquals(
            listOf("auto-20261124-000001.json", "auto-20261124-000002.json"),
            removed
        )
        assertTrue(DataBackup.pruneBackupNames(names.take(7), 7).isEmpty())
        // 非 auto 前缀的文件不参与清理
        assertTrue(
            DataBackup.pruneBackupNames(listOf("user.json", "auto-20261124-000001.json"), 0)
                .singleOrNull() == "auto-20261124-000001.json"
        )
    }

    @Test
    fun upcomingGroupsEventsAndTodosByDay() {
        val today = LocalDate.of(2026, 11, 24)
        val events = listOf(
            CalEvent(
                title = "今天日程",
                type = EventType.ONCE,
                year = 2026,
                month = 11,
                day = 24,
                timeMinutes = 9 * 60
            ),
            CalEvent(
                title = "后天日程",
                type = EventType.ONCE,
                year = 2026,
                month = 11,
                day = 26,
                timeMinutes = 10 * 60
            )
        )
        val todos = listOf(
            TodoItem(title = "过期待办", dueAt = LocalDateTime.of(2026, 11, 23, 18, 0)),
            TodoItem(title = "明天待办", dueAt = LocalDateTime.of(2026, 11, 25, 9, 30)),
            TodoItem(title = "已完成", dueAt = LocalDateTime.of(2026, 11, 24, 8, 0), done = true),
            TodoItem(title = "无时间待办")
        )
        val days = buildUpcomingDays(today, events, todos, 7)

        // 今天：过期待办置顶 + 今天日程
        val first = days.first()
        assertEquals(today, first.date)
        assertEquals(listOf("过期待办", "今天日程"), first.items.map { it.title })

        // 明天：只有待办
        val tomorrow = days.first { it.date == LocalDate.of(2026, 11, 25) }
        assertEquals(listOf("明天待办"), tomorrow.items.map { it.title })

        // 后天：只有日程
        val day26 = days.first { it.date == LocalDate.of(2026, 11, 26) }
        assertEquals(listOf("后天日程"), day26.items.map { it.title })

        // 无事项的日子被跳过，已完成/无时间待办不出现
        assertFalse(days.any { it.date == LocalDate.of(2026, 11, 27) })
        assertFalse(days.flatMap { it.items }.any { it.title == "已完成" })
        assertFalse(days.flatMap { it.items }.any { it.title == "无时间待办" })

        // 30 天窗口不会比 7 天窗口少
        assertTrue(buildUpcomingDays(today, events, todos, 30).size >= days.size)
    }

    @Test
    fun solarTermCountdownIsSane() {
        val result = LunarUtils.nextSolarTerm(LocalDate.of(2026, 12, 1))
        assertTrue(result != null)
        val (name, days) = result!!
        assertTrue("节气名不应为空", name.isNotEmpty())
        assertTrue("天数应在 0..366 之间: $days", days in 0..366)
        // 冬至前一周内的探测点，下一个节气不超过 90 天
        val around = LunarUtils.nextSolarTerm(LocalDate.of(2026, 12, 22))
        assertTrue(around != null && around!!.second in 0..90)
    }

    /**
     * 通知 action 的 extra key 必须与接收器读取的一致。
     * 同类问题（对象成员遮蔽同包顶层常量）曾让「稍后提醒」「完成」点了没反应。
     */
    @Test
    fun snoozePayloadUsesReceiverKeys() {
        val eventPayload = snoozePayload(eventId = "e1", todoId = null, minutes = 10, advanceDays = 3)
        assertEquals("e1", eventPayload[EXTRA_EVENT_ID])
        assertEquals(10, eventPayload[SnoozeReceiver.EXTRA_SNOOZE_MINUTES])
        assertEquals(3, eventPayload[EXTRA_ADVANCE_DAYS])
        assertFalse(eventPayload.containsKey(EXTRA_TODO_ID))

        val todoPayload = snoozePayload(eventId = null, todoId = "t1", minutes = 60, advanceDays = 0)
        assertEquals("t1", todoPayload[EXTRA_TODO_ID])
        assertEquals(60, todoPayload[SnoozeReceiver.EXTRA_SNOOZE_MINUTES])
        assertFalse(todoPayload.containsKey(EXTRA_EVENT_ID))
        assertFalse(todoPayload.containsKey(EXTRA_ADVANCE_DAYS))
    }

    @Test
    fun completePayloadUsesTodoKey() {
        val payload = completePayload("t1")
        assertEquals("t1", payload[EXTRA_TODO_ID])
        assertEquals(1, payload.size)
    }
}
