package com.lunarcalendar.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.ZonedDateTime

const val EXTRA_EVENT_ID = "event_id"

/** 提前提醒的天数；主提醒不带此参数（默认 0 = 当天）。 */
const val EXTRA_ADVANCE_DAYS = "advance_days"

/** 主提醒的槽位编号，提前提醒用 1~7 表示提前天数。 */
private const val SLOT_MAIN = 0
private const val MAX_ADVANCE_DAYS = 7

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
        // 先清空该事件的所有槽位（主提醒 + 1~7 天前），避免删除提前提醒后残留旧闹钟。
        for (slot in SLOT_MAIN..MAX_ADVANCE_DAYS) {
            alarmManager.cancel(pendingIntent(context, event.id, slot))
        }
        if (!event.enabled) return

        val trigger = Occurrences.nextTrigger(event, from) ?: return
        if (trigger.isAfter(ZonedDateTime.now())) {
            setAlarm(alarmManager, trigger.toInstant().toEpochMilli(), pendingIntent(context, event.id, SLOT_MAIN))
        }

        event.advanceReminders.forEach { reminder ->
            val advanceTrigger = Occurrences.nextAdvanceTrigger(event, reminder, from) ?: return@forEach
            if (!advanceTrigger.isAfter(ZonedDateTime.now())) return@forEach
            setAlarm(
                alarmManager,
                advanceTrigger.toInstant().toEpochMilli(),
                pendingIntent(context, event.id, reminder.daysBefore)
            )
        }
    }

    fun cancel(context: Context, eventId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in SLOT_MAIN..MAX_ADVANCE_DAYS) {
            alarmManager.cancel(pendingIntent(context, eventId, slot))
        }
    }

    /**
     * 稍后提醒：另设一个一次性临时闹钟到点重发通知，不改动原日程时间，
     * 也不影响正常排期的下一次提醒。走主提醒通道（[ReminderReceiver]），
     * 到点后同样会自动续排下一次。
     */
    fun snooze(context: Context, event: CalEvent, minutes: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val at = java.time.ZonedDateTime.now().plusMinutes(minutes.toLong().coerceAtLeast(1))
        setAlarm(alarmManager, at.toInstant().toEpochMilli(), snoozePendingIntent(context, event.id))
    }

    private fun snoozePendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // data 与正常槽位不同，取消/重建时互不影响
            data = Uri.parse("lunarcalendar://event/$eventId/snooze")
            putExtra(EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            "$eventId#snooze".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

    /**
     * @param slot 0 = 当天主提醒，1~7 = 提前 N 天提醒。
     */
    private fun pendingIntent(context: Context, eventId: String, slot: Int): PendingIntent {
        // 用 data 字段带上事件 id，使不同事件即使 requestCode（hashCode）碰撞、
        // 也会被系统视为不同的 PendingIntent，避免一个闹钟覆盖另一个。
        val uri = if (slot == SLOT_MAIN) {
            "lunarcalendar://event/$eventId"
        } else {
            "lunarcalendar://event/$eventId/advance/$slot"
        }
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse(uri)
            putExtra(EXTRA_EVENT_ID, eventId)
            if (slot != SLOT_MAIN) putExtra(EXTRA_ADVANCE_DAYS, slot)
        }
        return PendingIntent.getBroadcast(
            context,
            if (slot == SLOT_MAIN) eventId.hashCode() else "$eventId#advance$slot".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
