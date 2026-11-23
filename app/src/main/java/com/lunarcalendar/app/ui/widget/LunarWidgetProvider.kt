package com.lunarcalendar.app.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lunarcalendar.app.EventRepository
import com.lunarcalendar.app.EventType
import com.lunarcalendar.app.MainActivity
import com.lunarcalendar.app.Occurrences
import com.lunarcalendar.app.R
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 桌面 2×2 小组件。
 * 显示用户选定的一类事件（日程/生日/纪念日）中「下一次」或「累计」天数。
 *
 * 纪念日样式：[标题]已经  /  [大号数字]  /  [──── 天 ────]  /  [原始日期]
 * 倒计时样式：标题  /  还有 X 天  /  目标日期
 *
 * 用户选显示类型在 LunarWidgetConfigActivity 中保存到 SharedPreferences。
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

    companion object {

        const val PREFS = "lunar_widget"
        const val PREF_KEY_VIEW = "view"
        const val ACTION_REFRESH = "com.lunarcalendar.app.WIDGET_REFRESH"

        fun update(context: Context, awm: AppWidgetManager, widgetId: Int) {
            val viewType = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(PREF_KEY_VIEW, "birthday") ?: "birthday"
            val events = EventRepository.load(context).filter { it.enabled }
            val selected = filterAndSort(events, viewType)
            val next = selected.firstOrNull()
                ?: return renderEmpty(context, awm, widgetId, viewType)
            render(context, awm, widgetId, next, viewType)
        }

        private fun filterAndSort(
            events: List<com.lunarcalendar.app.CalEvent>,
            viewType: String
        ): List<com.lunarcalendar.app.CalEvent> = when (viewType) {
            "anniversary" -> events.filter { it.type == EventType.ANNIVERSARY }
                .sortedByDescending { Occurrences.anniversaryDaysPassed(it) }
            "schedule" -> events.filter { it.type == EventType.ONCE }
                .sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
            else -> events.filter {
                it.type == EventType.YEARLY_SOLAR || it.type == EventType.YEARLY_LUNAR
            }.sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
        }

        private fun render(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int,
            event: com.lunarcalendar.app.CalEvent,
            @Suppress("UNUSED_PARAMETER") viewType: String
        ) {
            val layoutRes = if (event.type == EventType.ANNIVERSARY) {
                R.layout.widget_anniversary
            } else {
                R.layout.widget_countdown
            }
            val rv = RemoteViews(context.packageName, layoutRes)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val openPi = android.app.PendingIntent.getActivity(
                context, 0, openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widget_root, openPi)

            when (event.type) {
                EventType.ANNIVERSARY -> {
                    val days = Occurrences.anniversaryDaysPassed(event)
                    val header = event.title.ifBlank { "纪念日" }
                    val footer = "${event.year}年${event.month}月${event.day}日"
                    rv.setTextViewText(R.id.anniv_header, "${header}已经")
                    rv.setTextViewText(R.id.anniv_number, days.toString())
                    rv.setTextViewText(R.id.anniv_footer, footer)
                }
                EventType.YEARLY_SOLAR, EventType.YEARLY_LUNAR -> {
                    val next = Occurrences.nextTrigger(event)
                    val days = next?.let { ChronoUnit.DAYS.between(LocalDate.now(), it.toLocalDate()) }
                    val header = event.title.ifBlank { "生日" }
                    val daysTxt = when {
                        days == null -> ""
                        days <= 0L -> "就是今天"
                        days == 1L -> "明天就到"
                        else -> "还有 ${days} 天"
                    }
                    rv.setTextViewText(R.id.cd_header, header)
                    rv.setTextViewText(R.id.cd_number, daysTxt)
                    rv.setTextViewText(
                        R.id.cd_footer,
                        next?.toLocalDate()?.let { Occurrences.fullDateText(it) }
                            ?: "${event.month}月${event.day}日"
                    )
                }
                EventType.ONCE -> {
                    val next = Occurrences.nextTrigger(event)
                    val days = next?.let { ChronoUnit.DAYS.between(LocalDate.now(), it.toLocalDate()) }
                    val header = event.title.ifBlank { "日程" }
                    val daysTxt = when {
                        days == null -> ""
                        days <= 0L -> "已经到期"
                        days == 1L -> "明天"
                        else -> "还有 ${days} 天"
                    }
                    rv.setTextViewText(R.id.cd_header, header)
                    rv.setTextViewText(R.id.cd_number, daysTxt)
                    rv.setTextViewText(
                        R.id.cd_footer,
                        next?.toLocalDate()?.let { Occurrences.fullDateText(it) }
                            ?: "${event.month}月${event.day}日"
                    )
                }
            }
            awm.updateAppWidget(widgetId, rv)
        }

        private fun renderEmpty(
            context: Context,
            awm: AppWidgetManager,
            widgetId: Int,
            viewType: String
        ) {
            val rv = RemoteViews(context.packageName, R.layout.widget_countdown)
            rv.setTextViewText(R.id.cd_header, when (viewType) {
                "anniversary" -> "还没有纪念日"
                "schedule" -> "还没有日程"
                else -> "还没有生日"
            })
            rv.setTextViewText(R.id.cd_number, "点我添加")
            rv.setTextViewText(R.id.cd_footer, "")
            awm.updateAppWidget(widgetId, rv)
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

        /** AppWidget 里点击「刷新」按钮或 ReminderScheduler 里事件变更时调用。 */
        fun forceRefreshAll(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setPackage(context.packageName)
            )
        }
    }
}
