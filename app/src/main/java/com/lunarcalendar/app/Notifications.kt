package com.lunarcalendar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate

/** 本地通知，不经过 FCM / 任何推送通道。 */
object Notifications {
    private const val CHANNEL_ID = "calendar_reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "日程与生日提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "本地闹钟提醒，不依赖网络推送"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context, event: CalEvent, advanceDays: Int = 0) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val targetDate = LocalDate.now().plusDays(advanceDays.toLong())
        val lunar = LunarUtils.info(targetDate)
        val dayText = if (advanceDays > 0) "${advanceDays} 天后" else "今天"
        val detail = buildString {
            append("$dayText ${targetDate.monthValue}月${targetDate.dayOfMonth}日 ${event.timeText}")
            append(" · 农历${lunar.monthLabel}${lunar.dayName}")
            if (event.note.isNotBlank()) {
                append("\n")
                append(event.note)
            }
        }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lunarcalendar://notification/event/${Uri.encode(event.id)}")
            putExtra(EXTRA_EVENT_ID, event.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title.ifBlank { "日程提醒" })
            .setContentText("$dayText ${targetDate.monthValue}月${targetDate.dayOfMonth}日 · ${event.typeLabel()}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "稍后 10 分钟", snoozeIntent(context, eventId = event.id, todoId = null, minutes = 10, advanceDays = advanceDays))
            .addAction(0, "稍后 1 小时", snoozeIntent(context, eventId = event.id, todoId = null, minutes = 60, advanceDays = advanceDays))
            .build()

        runCatching {
            // 以事件 id 作为 tag：不同事件的通知互不覆盖，避免 hashCode 碰撞时互相替换。
            NotificationManagerCompat.from(context).notify(event.id, 0, notification)
        }
    }

    fun showTodo(context: Context, todo: TodoItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)
        val due = todo.dueAt
        val detail = buildString {
            if (due != null) append("${due.monthValue}月${due.dayOfMonth}日 %02d:%02d".format(due.hour, due.minute))
            if (todo.note.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(todo.note)
            }
        }
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lunarcalendar://notification/todo/${Uri.encode(todo.id)}")
            putExtra(EXTRA_TODO_ID, todo.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            todo.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(todo.title.ifBlank { "待办提醒" })
            .setContentText("待办到时间了")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail.ifBlank { "待办到时间了" }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "完成", completeTodoIntent(context, todo.id))
            .addAction(0, "稍后 10 分钟", snoozeIntent(context, eventId = null, todoId = todo.id, minutes = 10))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify("todo:${todo.id}", 0, notification)
        }
    }

    /** 收起某条日程提醒（删除/编辑事件后避免留下僵尸通知）。 */
    fun cancelEvent(context: Context, eventId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(eventId, 0) }
    }

    /** 收起某条待办提醒（App 内完成或删除待办后）。 */
    fun cancelTodo(context: Context, todoId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel("todo:$todoId", 0) }
    }

    /** 「稍后」操作：发到非导出的 [SnoozeReceiver]，设临时闹钟并收起当前通知。 */
    private fun snoozeIntent(
        context: Context,
        eventId: String?,
        todoId: String?,
        minutes: Int,
        advanceDays: Int = 0
    ): PendingIntent {
        val target = eventId ?: todoId.orEmpty()
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE
            // data 让不同事件/待办、不同时长的 PendingIntent 互不覆盖
            data = Uri.parse("lunarcalendar://snooze/${Uri.encode(target)}/$minutes")
            putPayload(snoozePayload(eventId, todoId, minutes, advanceDays))
        }
        return PendingIntent.getBroadcast(
            context,
            "snooze:$target:$minutes".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 「完成」操作：发到非导出的待办勾选接收器，语义是「置为完成」而不是 toggle。 */
    private fun completeTodoIntent(context: Context, todoId: String): PendingIntent {
        val intent = Intent(context, com.lunarcalendar.app.ui.widget.TodoToggleReceiver::class.java).apply {
            action = com.lunarcalendar.app.ui.widget.TodoWidgetProvider.ACTION_COMPLETE
            data = Uri.parse("lunarcalendar://complete/${Uri.encode(todoId)}")
            putPayload(completePayload(todoId))
        }
        return PendingIntent.getBroadcast(
            context,
            "complete:$todoId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun Intent.putPayload(payload: Map<String, Any>) {
        payload.forEach { (key, value) ->
            when (value) {
                is Int -> putExtra(key, value)
                else -> putExtra(key, value.toString())
            }
        }
    }
}

/**
 * 「稍后」广播的 extra 载荷。抽成纯函数（不依赖 Android 运行时），
 * 便于单测锁定 key 与 [SnoozeReceiver] 读取的一致——同权重名常量遮蔽曾导致按钮无响应。
 */
internal fun snoozePayload(
    eventId: String?,
    todoId: String?,
    minutes: Int,
    advanceDays: Int
): Map<String, Any> = buildMap {
    if (eventId != null) put(EXTRA_EVENT_ID, eventId)
    if (todoId != null) put(EXTRA_TODO_ID, todoId)
    put(SnoozeReceiver.EXTRA_SNOOZE_MINUTES, minutes)
    if (advanceDays > 0) put(EXTRA_ADVANCE_DAYS, advanceDays)
}

/** 「完成」广播的 extra 载荷，key 必须与 [TodoToggleReceiver] 读取的一致。 */
internal fun completePayload(todoId: String): Map<String, Any> =
    mapOf(EXTRA_TODO_ID to todoId)
