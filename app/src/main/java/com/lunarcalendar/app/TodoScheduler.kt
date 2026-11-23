package com.lunarcalendar.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.ZoneId

const val EXTRA_TODO_ID = "todo_id"

/** 待办提醒：只在设置了日期时间且未完成时排一个本地闹钟。 */
object TodoScheduler {

    fun rescheduleAll(context: Context, todos: List<TodoItem>) {
        todos.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, todo: TodoItem) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, todo.id))
        if (todo.done || todo.dueAt == null) return
        val triggerAt = todo.dueAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return
        setAlarm(alarmManager, triggerAt, pendingIntent(context, todo.id))
    }

    fun cancel(context: Context, todoId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, todoId))
    }

    private fun setAlarm(alarmManager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun pendingIntent(context: Context, todoId: String): PendingIntent {
        val intent = Intent(context, TodoReminderReceiver::class.java).apply {
            data = Uri.parse("lunarcalendar://todo/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            todoId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
