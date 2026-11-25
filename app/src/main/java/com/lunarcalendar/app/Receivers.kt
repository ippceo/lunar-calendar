package com.lunarcalendar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟到点：发本地通知，并让重复日程续排下一次。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val event = EventRepository.load(context).firstOrNull { it.id == eventId } ?: return
        if (!event.enabled) return

        val targetDate = intent.getStringExtra(EXTRA_TARGET_DATE)
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            ?: java.time.LocalDate.now().plusDays(intent.getIntExtra(EXTRA_ADVANCE_DAYS, 0).toLong())
        Notifications.showReminder(context, event, targetDate)

        // 无条件尝试续排：重复日程从 1 分钟后找到下一次；
        // 一次性事件时间已过去，schedule() 自然不会再设新闹钟。
        ReminderScheduler.schedule(context, event, java.time.ZonedDateTime.now().plusMinutes(1))
    }
}

/** 待办闹钟到点：发本地通知。 */
class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getStringExtra(EXTRA_TODO_ID) ?: return
        val todo = TodoRepository.load(context).firstOrNull { it.id == todoId } ?: return
        if (todo.done) return
        Notifications.showTodo(context, todo)
        com.lunarcalendar.app.ui.widget.TodoWidgetProvider.forceRefreshAll(context)
    }
}

/**
 * 稍后提醒：通知栏点击「稍后」后，按分钟数设一个临时闹钟重新提醒。
 * 不改动原日程/待办时间，只补发一次通知。
 */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val minutes = intent.getIntExtra(EXTRA_SNOOZE_MINUTES, 10)
        val advanceDays = intent.getIntExtra(EXTRA_ADVANCE_DAYS, 0)
        val targetDate = intent.getStringExtra(EXTRA_TARGET_DATE)
            ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val todoId = intent.getStringExtra(EXTRA_TODO_ID)
        when {
            eventId != null -> {
                EventRepository.load(context).firstOrNull { it.id == eventId }?.let { event ->
                    if (event.enabled) {
                        ReminderScheduler.snooze(context, event, minutes, advanceDays, targetDate)
                    }
                }
                Notifications.cancelEvent(context, eventId)
            }

            todoId != null -> {
                TodoRepository.load(context).firstOrNull { it.id == todoId }?.let { todo ->
                    if (!todo.done) TodoScheduler.snooze(context, todo, minutes)
                }
                Notifications.cancelTodo(context, todoId)
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "com.lunarcalendar.app.action.SNOOZE"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
    }
}

/** 开机、改系统时间、时区变化、重装更新后重新排期所有提醒。 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ensureChannel(context)
        ReminderScheduler.rescheduleAll(context, EventRepository.load(context))
        TodoScheduler.rescheduleAll(context, TodoRepository.load(context))
        com.lunarcalendar.app.ui.widget.TodoWidgetProvider.forceRefreshAll(context)
    }
}
