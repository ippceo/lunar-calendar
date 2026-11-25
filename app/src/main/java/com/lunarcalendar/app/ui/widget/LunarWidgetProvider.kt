package com.lunarcalendar.app.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.lunarcalendar.app.CalEvent
import com.lunarcalendar.app.EXTRA_EVENT_ID
import com.lunarcalendar.app.EventRepository
import com.lunarcalendar.app.EventType
import com.lunarcalendar.app.MainActivity
import com.lunarcalendar.app.Occurrences
import com.lunarcalendar.app.R
import com.lunarcalendar.app.birthdayAge
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import androidx.core.content.edit

/**
 * 桌面 2×2 小组件，所有状态共用 [R.layout.widget_main]：
 *  - 未选择内容：展示月牙图形，点击进入配置页选择「日程 / 生日 / 纪念日」
 *  - 纪念日：标题 + 大号累计天数 + 「天」+ 原始日期
 *  - 日程 / 生日：标题 + 大号剩余天数 + 单位 + 目标日期
 *  - 该类目下暂无事件：提示添加，点击打开 App
 *
 * 每个小组件独立保存自己的显示类型（key = view_<appWidgetId>）。
 */
class LunarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val awm = AppWidgetManager.getInstance(context)
        awm.getAppWidgetIds(ComponentName(context, LunarWidgetProvider::class.java))
            .forEach { update(context, awm, it) }
        scheduleMidnightRefresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            appWidgetIds.forEach {
                remove(viewKey(it))
                remove(modeKey(it))
                remove(eventKey(it))
                remove(annivKey(it))
                remove(layoutKey(it))
            }
        }
    }

    companion object {

        const val PREFS = "lunar_widget"
        const val ACTION_REFRESH = "com.lunarcalendar.app.WIDGET_REFRESH"

        const val VIEW_SCHEDULE = "schedule"
        const val VIEW_BIRTHDAY = "birthday"
        const val VIEW_ANNIVERSARY = "anniversary"

        /** 自动：取距离现在最近的一条；手动：用户指定一条。 */
        const val MODE_AUTO = "auto"
        const val MODE_MANUAL = "manual"

        /** 纪念日卡片主显示：已经过的天数 / 距离下次还有的天数。 */
        const val ANNIV_PASSED = "passed"
        const val ANNIV_REMAINING = "remaining"

        /** 日程卡布局：单条大号倒计时 / 多条列表。 */
        const val LAYOUT_SINGLE = "single"
        const val LAYOUT_LIST = "list"

        fun viewKey(widgetId: Int) = "view_$widgetId"
        fun modeKey(widgetId: Int) = "mode_$widgetId"
        fun eventKey(widgetId: Int) = "event_$widgetId"
        fun annivKey(widgetId: Int) = "anniv_$widgetId"
        fun layoutKey(widgetId: Int) = "layout_$widgetId"

        fun setLayoutMode(context: Context, widgetId: Int, mode: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(layoutKey(widgetId), mode) }
        }

        fun getLayoutMode(context: Context, widgetId: Int): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(layoutKey(widgetId), LAYOUT_SINGLE) ?: LAYOUT_SINGLE

        fun setViewType(context: Context, widgetId: Int, viewType: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(viewKey(widgetId), viewType) }
        }

        fun getViewType(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(viewKey(widgetId), null)

        fun setMode(context: Context, widgetId: Int, mode: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(modeKey(widgetId), mode) }
        }

        fun getMode(context: Context, widgetId: Int): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(modeKey(widgetId), MODE_AUTO) ?: MODE_AUTO

        fun setEventId(context: Context, widgetId: Int, eventId: String?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { if (eventId == null) remove(eventKey(widgetId)) else putString(eventKey(widgetId), eventId) }
        }

        fun getEventId(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(eventKey(widgetId), null)

        fun setAnnivPrimary(context: Context, widgetId: Int, primary: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(annivKey(widgetId), primary) }
        }

        fun getAnnivPrimary(context: Context, widgetId: Int): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(annivKey(widgetId), ANNIV_PASSED) ?: ANNIV_PASSED

        fun update(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val viewType = getViewType(context, widgetId)
                ?: return renderPlaceholder(context, awm, widgetId)

            // 日程卡可切换为「多条列表」布局
            if (viewType == VIEW_SCHEDULE && getLayoutMode(context, widgetId) == LAYOUT_LIST) {
                return renderList(context, awm, widgetId)
            }

            val events = EventRepository.load(context).filter { it.enabled }
            val candidates = filterAndSort(events, viewType)
            if (candidates.isEmpty()) {
                return renderEmpty(context, awm, widgetId, viewType)
            }
            val manualId = getEventId(context, widgetId)
            val manual = getMode(context, widgetId) == MODE_MANUAL && manualId != null
            val selected = if (manual) {
                candidates.firstOrNull { it.id == manualId } ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            } ?: return renderEmpty(context, awm, widgetId, viewType)

            renderContent(context, awm, widgetId, selected)
        }

        private fun filterAndSort(events: List<CalEvent>, viewType: String): List<CalEvent> =
            when (viewType) {
                VIEW_ANNIVERSARY -> events.filter { it.type == EventType.ANNIVERSARY }
                    .sortedByDescending { Occurrences.anniversaryDaysPassed(it) }
                VIEW_SCHEDULE -> events.filter { it.type == EventType.ONCE }
                    .filter { event ->
                        // 自动模式下只取未过期的日程
                        val next = Occurrences.nextTrigger(event) ?: return@filter false
                        next.isAfter(ZonedDateTime.now())
                    }
                    .sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
                else -> events.filter {
                    it.type == EventType.YEARLY_SOLAR || it.type == EventType.YEARLY_LUNAR
                }.sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
            }

        /** 日程列表模式：按时间列出接下来最多 4 条日程，点一行直接打开对应日程。 */
        private fun renderList(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val now = ZonedDateTime.now()
            val events = EventRepository.load(context).filter { it.enabled }
            val candidates = filterAndSort(events, VIEW_SCHEDULE)
            val minHeight = awm.getAppWidgetOptions(widgetId)
                .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            // 头部约 24dp、每行约 30dp，按可用高度自适应条数
            val capacity = ((minHeight - 24) / 30).coerceIn(1, 4)
            val visible = candidates.take(capacity)

            val rv = RemoteViews(context.packageName, R.layout.widget_event_list)
            rv.setOnClickPendingIntent(R.id.ev_header, configIntent(context, widgetId))
            rv.removeAllViews(R.id.ev_rows)
            if (visible.isEmpty()) {
                rv.setViewVisibility(R.id.ev_rows, View.GONE)
                rv.setViewVisibility(R.id.ev_empty, View.VISIBLE)
                rv.setOnClickPendingIntent(R.id.ev_empty, openAppIntent(context, widgetId))
            } else {
                rv.setViewVisibility(R.id.ev_empty, View.GONE)
                rv.setViewVisibility(R.id.ev_rows, View.VISIBLE)
                visible.forEachIndexed { index, event ->
                    val row = RemoteViews(context.packageName, R.layout.widget_event_row)
                    row.setTextViewText(R.id.ev_row_title, event.title.ifBlank { "日程" })
                    row.setTextViewText(R.id.ev_row_sub, Occurrences.nextLabel(event, now))
                    row.setOnClickPendingIntent(
                        R.id.ev_row_root,
                        openEventIntent(context, widgetId, index, event.id)
                    )
                    rv.addView(R.id.ev_rows, row)
                }
            }
            awm.updateAppWidget(widgetId, rv)
        }

        private fun openEventIntent(
            context: Context,
            widgetId: Int,
            index: Int,
            eventId: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("lunarcalendar://event-widget/$widgetId/$index")
                putExtra(EXTRA_EVENT_ID, eventId)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("lunarcalendar://widget-open/$widgetId")
            }
            return PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 新加的小组件：只显示一个月牙图形，点击打开配置页。 */
        private fun renderPlaceholder(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_main)
            rv.setOnClickPendingIntent(R.id.w_root, configIntent(context, widgetId))
            rv.setViewVisibility(R.id.w_icon, View.VISIBLE)
            rv.setViewVisibility(R.id.w_secondary, View.GONE)
            rv.setTextViewText(R.id.w_header, "拾光历")
            rv.setTextViewText(R.id.w_number, "＋")
            rv.setTextViewText(R.id.w_unit, "点我选择显示内容")
            rv.setTextViewText(R.id.w_footer, "日程 · 生日 · 纪念日")
            awm.updateAppWidget(widgetId, rv)
        }

        private fun renderContent(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int,
            event: CalEvent
        ) {
            val rv = RemoteViews(context.packageName, R.layout.widget_main)
            rv.setOnClickPendingIntent(R.id.w_root, configIntent(context, widgetId))
            rv.setViewVisibility(R.id.w_icon, View.GONE)
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()

            when (event.type) {
                EventType.ANNIVERSARY -> {
                    val passed = Occurrences.anniversaryDaysPassed(event, today).coerceAtLeast(0)
                    val remaining = daysUntilNextAnniversary(event, today)
                    val primaryPassed = getAnnivPrimary(context, widgetId) == ANNIV_PASSED
                    val title = event.title.ifBlank { "纪念日" }
                    rv.setTextViewText(R.id.w_header, if (primaryPassed) "${title}已经" else "${title}还有")
                    rv.setTextViewText(R.id.w_number, (if (primaryPassed) passed else remaining).toString())
                    rv.setTextViewText(R.id.w_unit, "天")
                    rv.setViewVisibility(R.id.w_secondary, View.VISIBLE)
                    rv.setTextViewText(
                        R.id.w_secondary,
                        if (primaryPassed) "还有 $remaining 天" else "已经 $passed 天"
                    )
                    rv.setTextViewText(
                        R.id.w_footer,
                        "${event.year}年${event.month}月${event.day}日"
                    )
                }

                EventType.ONCE -> {
                    val next = Occurrences.nextTrigger(event)
                    val title = event.title.ifBlank { "日程" }
                    rv.setTextViewText(R.id.w_header, title)
                    if (next == null || !next.isAfter(now)) {
                        rv.setTextViewText(R.id.w_number, "—")
                        rv.setTextViewText(R.id.w_unit, "已结束")
                    } else {
                        val minutes = java.time.Duration.between(now, next).toMinutes()
                        val hours = ((minutes + 59) / 60).coerceAtLeast(1)
                        if (hours < 24) {
                            rv.setTextViewText(R.id.w_number, hours.toString())
                            rv.setTextViewText(R.id.w_unit, "小时")
                        } else {
                            rv.setTextViewText(
                                R.id.w_number,
                                ChronoUnit.DAYS.between(today, next.toLocalDate()).toString()
                            )
                            rv.setTextViewText(R.id.w_unit, "天")
                        }
                    }
                    rv.setTextViewText(
                        R.id.w_footer,
                        next?.toLocalDate()?.let { Occurrences.fullDateText(it) }
                            ?: "${event.month}月${event.day}日"
                    )
                }

                else -> {
                    val next = Occurrences.nextTrigger(event)
                    val days = next?.let { ChronoUnit.DAYS.between(today, it.toLocalDate()) }
                    val title = event.title.ifBlank { "生日" }
                    val numberText: String
                    val unitText: String
                    when {
                        days == null -> { numberText = ""; unitText = "" }
                        days < 0L -> { numberText = "0"; unitText = "已过期" }
                        days == 0L -> { numberText = "0"; unitText = "今天" }
                        days == 1L -> { numberText = "1"; unitText = "明天" }
                        else -> { numberText = days.toString(); unitText = "天" }
                    }
                    rv.setTextViewText(R.id.w_header, title)
                    rv.setTextViewText(R.id.w_number, numberText)
                    rv.setTextViewText(R.id.w_unit, unitText)
                    val age = next?.let { event.birthdayAge(it.toLocalDate()) }
                    if (age != null) {
                        rv.setViewVisibility(R.id.w_secondary, View.VISIBLE)
                        rv.setTextViewText(R.id.w_secondary, "$age 岁")
                    }
                    rv.setTextViewText(
                        R.id.w_footer,
                        next?.toLocalDate()?.let { Occurrences.fullDateText(it) }
                            ?: "${event.month}月${event.day}日"
                    )
                }
            }
            awm.updateAppWidget(widgetId, rv)
        }

        /** 距离下一次周年（同月同日）还有多少天；当天为 0。 */
        private fun daysUntilNextAnniversary(event: CalEvent, today: LocalDate): Long {
            val thisYear = safeDate(today.year, event.month, event.day)
            val next = if (thisYear.isBefore(today)) safeDate(today.year + 1, event.month, event.day) else thisYear
            return ChronoUnit.DAYS.between(today, next)
        }

        private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
            val safeMonth = month.coerceIn(1, 12)
            val length = java.time.YearMonth.of(year, safeMonth).lengthOfMonth()
            return LocalDate.of(year, safeMonth, day.coerceIn(1, length))
        }

        private fun renderEmpty(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int,
            viewType: String
        ) {
            val rv = RemoteViews(context.packageName, R.layout.widget_main)
            rv.setOnClickPendingIntent(R.id.w_root, configIntent(context, widgetId))
            rv.setViewVisibility(R.id.w_icon, View.GONE)
            rv.setViewVisibility(R.id.w_secondary, View.GONE)
            rv.setTextViewText(
                R.id.w_header,
                when (viewType) {
                    VIEW_ANNIVERSARY -> "还没有纪念日"
                    VIEW_SCHEDULE -> "还没有日程"
                    else -> "还没有生日"
                }
            )
            rv.setTextViewText(R.id.w_number, "＋")
            rv.setTextViewText(R.id.w_unit, "点我添加")
            rv.setTextViewText(R.id.w_footer, "在拾光历中创建后会显示在这里")
            awm.updateAppWidget(widgetId, rv)
        }

        private fun configIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, LunarWidgetConfigActivity::class.java).apply {
                data = Uri.parse("lunarcalendar://widget/$widgetId/config")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleMidnightRefresh(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val now = ZonedDateTime.now()
            val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_REFRESH).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.set(AlarmManager.RTC, midnight.toInstant().toEpochMilli(), pi)
        }

        /** 事件变更、跨天等场景刷新全部小组件。 */
        fun forceRefreshAll(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setPackage(context.packageName)
            )
        }
    }
}
