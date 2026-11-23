package com.lunarcalendar.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZonedDateTime

const val EXTRA_EVENT_ID = "event_id"

/**
 * 用 AlarmManager 设置系统本地闹钟。
 *
 * 不依赖任何推送通道：即使 App 进程被杀死，只要系统闹钟还在，到点仍会拉起
 * [ReminderReceiver] 发出通知；开机 / 改时间 / 更新安装后会重新排期。
 */
object ReminderScheduler {

    fun rescheduleAll(context: Context, events: List<CalEvent>) {
        events.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, event: CalEvent, from: ZonedDateTime = ZonedDateTime.now()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context, event.id)
        alarmManager.cancel(pendingIntent)
        if (!event.enabled) return

        val trigger = Occurrences.nextTrigger(event, from) ?: return
        if (!trigger.isAfter(ZonedDateTime.now())) return

        val triggerAtMillis = trigger.toInstant().toEpochMilli()
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

    fun cancel(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, eventId))
    }

    private fun pendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
