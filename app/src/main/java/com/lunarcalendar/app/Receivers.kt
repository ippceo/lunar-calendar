package com.lunarcalendar.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 闹钟到点：发本地通知，并把“每年”类型的提醒排到下一年。 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: return
        val event = EventRepository.load(context).firstOrNull { it.id == eventId } ?: return
        if (!event.enabled) return

        Notifications.showReminder(context, event)

        if (event.type != EventType.ONCE) {
            // 刚触发的时间点已经过去，从 1 分钟后开始找下一次，避免重复提醒。
            ReminderScheduler.schedule(context, event, java.time.ZonedDateTime.now().plusMinutes(1))
        }
    }
}

/** 开机、改系统时间、时区变化、重装更新后重新排期所有提醒。 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.ensureChannel(context)
        ReminderScheduler.rescheduleAll(context, EventRepository.load(context))
    }
}
