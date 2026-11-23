package com.lunarcalendar.app

import android.content.Context
import org.json.JSONArray

/** 用 SharedPreferences + JSON 保存提醒，无需数据库，也不联网。 */
object EventRepository {
    private const val PREFS = "lunar_calendar"
    private const val KEY_EVENTS = "events"

    fun load(context: Context): List<CalEvent> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EVENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { CalEvent.fromJson(it) }?.let {
                    it.copy(
                        title = it.title.take(MAX_TEXT_LENGTH),
                        note = it.note.take(MAX_TEXT_LENGTH)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, events: List<CalEvent>) {
        val array = JSONArray()
        events.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, array.toString())
            .apply()
    }
}
