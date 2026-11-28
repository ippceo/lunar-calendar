package com.lunarcalendar.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.lunarcalendar.app.AppSettings
import com.lunarcalendar.app.EXTRA_TODO_ID
import com.lunarcalendar.app.EXTRA_WIDGET_SUBTAB
import com.lunarcalendar.app.EXTRA_WIDGET_TAB
import com.lunarcalendar.app.MainActivity
import com.lunarcalendar.app.R
import com.lunarcalendar.app.TodoRepository
import com.lunarcalendar.app.TodoScheduler
import com.lunarcalendar.app.sortedForDisplay
import java.time.LocalDateTime

/**
 * 待办小组件：显示未完成的待办，点左侧圆圈即可在桌面勾选完成；
 * 点标题进入「提醒 - 待办」并定位到该条，点表头 / 底部进入待办列表。
 * 支持 2×2 / 2×4 / 4×4，按高度自动决定显示条数（2 / 4 / 6）。
 */
class TodoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                handleToggle(context, intent.getStringExtra(EXTRA_TODO_ID))
                return
            }

            ACTION_REFRESH -> {
                forceRefreshAll(context)
                return
            }

            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                forceRefreshAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE = "com.lunarcalendar.app.action.TODO_TOGGLE"

        /** 通知栏「完成」：置为已完成，不再走 toggle，避免误把已完成的任务重新打开。 */
        const val ACTION_COMPLETE = "com.lunarcalendar.app.action.TODO_COMPLETE"
        const val ACTION_REFRESH = "com.lunarcalendar.app.action.TODO_REFRESH"

        fun forceRefreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodoWidgetProvider::class.java)
            )
            ids.forEach { updateWidget(context, manager, it) }
        }

        /** 供 [TodoToggleReceiver] 调用的公开入口：桌面组件行点击 = 切换完成状态。 */
        fun handleToggle(context: Context, todoId: String?) {
            updateDone(context, todoId, forcedDone = null)
        }

        /** 供 [TodoToggleReceiver] 调用的公开入口：通知栏「完成」= 只置为完成。 */
        fun handleComplete(context: Context, todoId: String?) {
            updateDone(context, todoId, forcedDone = true)
        }

        private fun updateDone(context: Context, todoId: String?, forcedDone: Boolean?) {
            if (todoId == null) return
            val todos = TodoRepository.load(context)
            val updated = todos.map {
                if (it.id == todoId) it.copy(done = forcedDone ?: !it.done) else it
            }
            TodoRepository.save(context, updated)
            TodoScheduler.rescheduleAll(context, updated)
            forceRefreshAll(context)
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            // 头部约 30dp，底部「查看全部」约 20dp，每行约 38dp
            val showFooter = minHeight >= 150
            val capacity = ((minHeight - if (showFooter) 66 else 30) / 38).coerceIn(2, 6)

            val todos = TodoRepository.load(context).sortedForDisplay()
            val showDone = AppSettings.todoWidgetShowDone(context)
            val display = if (showDone) todos else todos.filter { !it.done }
            val visible = display.take(capacity)
            val remaining = todos.count { !it.done }
            val done = todos.count { it.done }
            val now = LocalDateTime.now()

            val rv = RemoteViews(context.packageName, R.layout.widget_todo)
            rv.setTextViewText(
                R.id.todo_count,
                when {
                    remaining > 0 -> "还有 $remaining 项"
                    done > 0 -> "全部完成"
                    else -> ""
                }
            )
            rv.removeAllViews(R.id.todo_rows)
            val open = openAppIntent(context, widgetId)

            if (visible.isEmpty()) {
                rv.setViewVisibility(R.id.todo_rows, View.GONE)
                rv.setViewVisibility(R.id.todo_footer, View.GONE)
                rv.setViewVisibility(R.id.todo_empty, View.VISIBLE)
                rv.setTextViewText(
                    R.id.todo_empty,
                    if (done > 0) "全部完成\n点一下，记下新的事" else "还没有待办\n点一下，添加一条"
                )
                rv.setOnClickPendingIntent(R.id.todo_empty, open)
            } else {
                rv.setViewVisibility(R.id.todo_empty, View.GONE)
                rv.setViewVisibility(R.id.todo_rows, View.VISIBLE)
                rv.setViewVisibility(R.id.todo_footer, if (showFooter) View.VISIBLE else View.GONE)
                visible.forEachIndexed { index, todo ->
                    val row = RemoteViews(context.packageName, R.layout.widget_todo_row)
                    row.setTextViewText(R.id.row_title, todo.title.ifBlank { "未命名待办" })
                    row.setImageViewResource(
                        R.id.row_check,
                        if (todo.done) R.drawable.ic_todo_checked else R.drawable.ic_todo_unchecked
                    )
                    if (todo.done) {
                        row.setTextColor(
                            R.id.row_title,
                            context.getColor(R.color.widget_faint)
                        )
                    }
                    val due = todo.dueAt
                    if (due != null) {
                        val (dueLabel, overdue) = dueText(due, todo.done, now)
                        row.setViewVisibility(R.id.row_due, View.VISIBLE)
                        row.setTextViewText(R.id.row_due, dueLabel)
                        if (overdue) {
                            row.setTextColor(
                                R.id.row_due,
                                context.getColor(R.color.widget_overdue)
                            )
                        }
                    } else {
                        row.setViewVisibility(R.id.row_due, View.GONE)
                    }
                    // 圆圈：切换完成；标题区：打开「提醒 - 待办」并定位到这条
                    row.setOnClickPendingIntent(
                        R.id.row_check_touch,
                        toggleIntent(context, widgetId, index, todo.id)
                    )
                    row.setOnClickPendingIntent(
                        R.id.row_text,
                        openTodoIntent(context, widgetId, index, todo.id)
                    )
                    rv.addView(R.id.todo_rows, row)
                }
                rv.setOnClickPendingIntent(R.id.todo_footer, open)
            }

            rv.setOnClickPendingIntent(R.id.todo_header, open)
            manager.updateAppWidget(widgetId, rv)
        }

        /** 待办时间文案：今天 / 明天 / 具体日期；逾期用醒目色。 */
        private fun dueText(
            due: LocalDateTime,
            done: Boolean,
            now: LocalDateTime
        ): Pair<String, Boolean> {
            val time = "%02d:%02d".format(due.hour, due.minute)
            val overdue = !done && due.isBefore(now)
            val text = when {
                overdue -> "已逾期 · ${due.monthValue}月${due.dayOfMonth}日 $time"
                due.toLocalDate() == now.toLocalDate() -> "今天 $time"
                due.toLocalDate() == now.toLocalDate().plusDays(1) -> "明天 $time"
                due.year == now.year -> "${due.monthValue}月${due.dayOfMonth}日 $time"
                else -> "${due.year}年${due.monthValue}月${due.dayOfMonth}日 $time"
            }
            return text to overdue
        }

        private fun toggleIntent(
            context: Context,
            widgetId: Int,
            index: Int,
            todoId: String
        ): PendingIntent {
            val intent = Intent(context, TodoToggleReceiver::class.java).apply {
                action = ACTION_TOGGLE
                data = Uri.parse("lunarcalendar://todo-widget/$widgetId/$index")
                putExtra(EXTRA_TODO_ID, todoId)
            }
            return PendingIntent.getBroadcast(
                context,
                widgetId * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 点表头 / 空状态：「查看全部」→ 提醒-待办页。 */
        private fun openAppIntent(context: Context, widgetId: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("lunarcalendar://todo-widget-open/$widgetId")
                putExtra(EXTRA_WIDGET_TAB, 1)
                putExtra(EXTRA_WIDGET_SUBTAB, 3)
            }
            return PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** 点标题：打开这条待办的编辑页（仍停留在提醒-待办列表）。 */
        private fun openTodoIntent(
            context: Context,
            widgetId: Int,
            index: Int,
            todoId: String
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("lunarcalendar://todo-widget-detail/$widgetId/$index")
                putExtra(EXTRA_TODO_ID, todoId)
            }
            return PendingIntent.getActivity(
                context,
                500_000 + widgetId * 100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

/**
 * 非导出的内部接收器：只有本应用能触发待办勾选。
 * 外部应用无法通过显式广播进入此路径。
 */
class TodoToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TodoWidgetProvider.ACTION_TOGGLE -> {
                val todoId = intent.getStringExtra(EXTRA_TODO_ID)
                TodoWidgetProvider.handleToggle(context, todoId)
                cancelNotification(context, todoId)
            }

            TodoWidgetProvider.ACTION_COMPLETE -> {
                val todoId = intent.getStringExtra(EXTRA_TODO_ID)
                TodoWidgetProvider.handleComplete(context, todoId)
                cancelNotification(context, todoId)
            }
        }
    }

    private fun cancelNotification(context: Context, todoId: String?) {
        if (todoId == null) return
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context).cancel("todo:$todoId", 0)
        }
    }
}
