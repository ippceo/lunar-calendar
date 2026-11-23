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
import com.lunarcalendar.app.EventRepository
import com.lunarcalendar.app.EventType
import com.lunarcalendar.app.Occurrences
import com.lunarcalendar.app.R
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
            appWidgetIds.forEach { remove(viewKey(it)) }
        }
    }

    companion object {

        const val PREFS = "lunar_widget"
        const val ACTION_REFRESH = "com.lunarcalendar.app.WIDGET_REFRESH"

        const val VIEW_SCHEDULE = "schedule"
        const val VIEW_BIRTHDAY = "birthday"
        const val VIEW_ANNIVERSARY = "anniversary"

        fun viewKey(widgetId: Int) = "view_$widgetId"

        fun setViewType(context: Context, widgetId: Int, viewType: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit { putString(viewKey(widgetId), viewType) }
        }

        fun getViewType(context: Context, widgetId: Int): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(viewKey(widgetId), null)

        fun update(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val viewType = getViewType(context, widgetId)
                ?: return renderPlaceholder(context, awm, widgetId)

            val events = EventRepository.load(context).filter { it.enabled }
            val next = filterAndSort(events, viewType).firstOrNull()
                ?: return renderEmpty(context, awm, widgetId, viewType)
            renderContent(context, awm, widgetId, next)
        }

        private fun filterAndSort(events: List<CalEvent>, viewType: String): List<CalEvent> =
            when (viewType) {
                VIEW_ANNIVERSARY -> events.filter { it.type == EventType.ANNIVERSARY }
                    .sortedByDescending { Occurrences.anniversaryDaysPassed(it) }
                VIEW_SCHEDULE -> events.filter { it.type == EventType.ONCE }
                    .sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
                else -> events.filter {
                    it.type == EventType.YEARLY_SOLAR || it.type == EventType.YEARLY_LUNAR
                }.sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
            }

        /** 新加的小组件：只显示一个月牙图形，点击打开配置页。 */
        private fun renderPlaceholder(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val rv = RemoteViews(context.packageName, R.layout.widget_main)
            rv.setOnClickPendingIntent(R.id.w_root, configIntent(context, widgetId))
            rv.setViewVisibility(R.id.w_icon, View.VISIBLE)
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

            if (event.type == EventType.ANNIVERSARY) {
                val days = Occurrences.anniversaryDaysPassed(event)
                val title = event.title.ifBlank { "纪念日" }
                rv.setTextViewText(R.id.w_header, "${title}已经")
                rv.setTextViewText(R.id.w_number, days.toString())
                rv.setTextViewText(R.id.w_unit, "天")
                rv.setTextViewText(
                    R.id.w_footer,
                    "${event.year}年${event.month}月${event.day}日"
                )
            } else {
                val next = Occurrences.nextTrigger(event)
                val days = next?.let {
                    ChronoUnit.DAYS.between(LocalDate.now(), it.toLocalDate())
                }
                val isOnce = event.type == EventType.ONCE
                val title = event.title.ifBlank { if (isOnce) "日程" else "生日" }
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
                rv.setTextViewText(
                    R.id.w_footer,
                    next?.toLocalDate()?.let { Occurrences.fullDateText(it) }
                        ?: "${event.month}月${event.day}日"
                )
            }
            awm.updateAppWidget(widgetId, rv)
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
