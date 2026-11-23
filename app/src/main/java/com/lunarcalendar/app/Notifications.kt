package com.lunarcalendar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
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
            .build()

        runCatching {
            // 以事件 id 作为 tag：不同事件的通知互不覆盖，避免 hashCode 碰撞时互相替换。
            NotificationManagerCompat.from(context).notify(event.id, 0, notification)
        }
    }
}
