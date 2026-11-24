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
    const val EXTRA_EVENT_ID = "com.lunarcalendar.app.extra.EVENT_ID"
    const val EXTRA_TODO_ID = "com.lunarcalendar.app.extra.TODO_ID"

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
            .addAction(0, "稍后 10 分钟", snoozeIntent(context, eventId = event.id, todoId = null, minutes = 10))
            .addAction(0, "稍后 1 小时", snoozeIntent(context, eventId = event.id, todoId = null, minutes = 60))
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

    /** 「稍后」操作：发到非导出的 [SnoozeReceiver]，设临时闹钟并收起当前通知。 */
    private fun snoozeIntent(context: Context, eventId: String?, todoId: String?, minutes: Int): PendingIntent {
        val intent = Intent(context, SnoozeReceiver::class.java).apply {
            action = SnoozeReceiver.ACTION_SNOOZE
            if (eventId != null) putExtra(EXTRA_EVENT_ID, eventId)
            if (todoId != null) putExtra(EXTRA_TODO_ID, todoId)
            putExtra(SnoozeReceiver.EXTRA_SNOOZE_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context,
            "snooze:${eventId ?: todoId}:$minutes".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 「完成」操作：复用非导出的待办勾选接收器，通知栏直接完成待办。 */
    private fun completeTodoIntent(context: Context, todoId: String): PendingIntent {
        val intent = Intent(context, com.lunarcalendar.app.ui.widget.TodoToggleReceiver::class.java).apply {
            action = com.lunarcalendar.app.ui.widget.TodoWidgetProvider.ACTION_TOGGLE
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            "complete:$todoId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
