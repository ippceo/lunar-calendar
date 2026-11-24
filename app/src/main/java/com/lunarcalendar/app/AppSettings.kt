package com.lunarcalendar.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context

/**
 * 应用级偏好设置（独立于提醒数据），保存在同一个 SharedPreferences 文件中。
 */
object AppSettings {
    private const val PREFS = "lunar_calendar"
    private const val KEY_EXCLUDE_FROM_RECENTS = "exclude_from_recents"
    private const val KEY_WEEK_START = "week_start"
    private const val KEY_ACCENT = "accent"

    /** 一周起始日：0 = 周一，1 = 周日。 */
    fun weekStart(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_WEEK_START, 0)

    fun setWeekStart(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_WEEK_START, value)
            .apply()
    }

    /** 主题色序号，对应 MainActivity 中的预设列表。 */
    fun accent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_ACCENT, 0)

    fun setAccent(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCENT, value)
            .apply()
    }

    /** 是否从系统最近任务（多任务）列表中隐藏。默认不隐藏。 */
    fun excludeFromRecents(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXCLUDE_FROM_RECENTS, false)

    fun setExcludeFromRecents(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXCLUDE_FROM_RECENTS, value)
            .apply()
    }

    /**
     * 把「从最近任务隐藏」的设置应用到当前 Activity 所在任务。
     * 通过 ActivityManager.AppTask.setExcludeFromRecents 修改任务根 Intent 的 flag，
     * 即时生效（系统会立即从最近屏幕移除/恢复该任务），不需要重建 Activity。
     */
    fun applyExcludeFromRecents(activity: Activity) {
        val exclude = excludeFromRecents(activity)
        runCatching {
            val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val taskId = activity.taskId
            am.appTasks.forEach { task ->
                if (task.taskInfo.id == taskId) {
                    task.setExcludeFromRecents(exclude)
                }
            }
        }
    }
}
