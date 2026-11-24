package com.lunarcalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 近期列表里的一行事项。 */
sealed interface UpcomingItem {
    val title: String

    /** 日程：[date] 是本次发生的日期。 */
    data class EventItem(val event: CalEvent, val date: LocalDate) : UpcomingItem {
        override val title: String get() = event.title
    }

    /** 待办：[overdue] 表示已过期未完成，归入今天分组置顶。 */
    data class TodoRow(val todo: TodoItem, val overdue: Boolean) : UpcomingItem {
        override val title: String get() = todo.title
    }
}

/** 按日期分组的一天：当天所有日程与待办。 */
data class UpcomingDay(val date: LocalDate, val items: List<UpcomingItem>)

/**
 * 汇总未来 [days] 天的日程与待办，按日期分组，无事项的日子跳过。
 * 过期未完成的待办归入今天，排在最前。
 * 纯函数，无 Android 依赖，便于单元测试。
 */
fun buildUpcomingDays(
    today: LocalDate,
    events: List<CalEvent>,
    todos: List<TodoItem>,
    days: Int
): List<UpcomingDay> {
    val enabled = events.filter { it.enabled }
    val unfinished = todos.filter { !it.done && it.dueAt != null }
    return (0 until days).map { offset ->
        val date = today.plusDays(offset.toLong())
        val items = mutableListOf<UpcomingItem>()
        if (offset == 0) {
            unfinished.filter { it.dueAt!!.toLocalDate().isBefore(today) }
                .sortedBy { it.dueAt }
                .forEach { items.add(UpcomingItem.TodoRow(it, overdue = true)) }
        }
        unfinished.filter { it.dueAt!!.toLocalDate() == date }
            .sortedBy { it.dueAt }
            .forEach { items.add(UpcomingItem.TodoRow(it, overdue = false)) }
        enabled.filter { Occurrences.occursOn(it, date) }
            .sortedBy { it.timeMinutes }
            .forEach { items.add(UpcomingItem.EventItem(it, date)) }
        UpcomingDay(date, items)
    }.filter { it.items.isNotEmpty() }
}

/** 「近期」视图：未来 7 / 30 天的日程与待办，按日期分组展示。 */
@Composable
fun UpcomingView(
    today: LocalDate,
    events: List<CalEvent>,
    todos: List<TodoItem>,
    onSelectDate: (LocalDate) -> Unit,
    onOpenTodo: () -> Unit
) {
    var range by rememberSaveable { mutableIntStateOf(7) }
    val days = remember(today, events, todos, range) {
        buildUpcomingDays(today, events, todos, range)
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = range == 7,
                onClick = { range = 7 },
                label = { Text("近 7 天") }
            )
            FilterChip(
                selected = range == 30,
                onClick = { range = 30 },
                label = { Text("近 30 天") }
            )
        }

        if (days.isEmpty()) {
            Text(
                text = "接下来没有日程或待办",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        days.forEach { day ->
            Text(
                text = dayHeader(today, day.date),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDate(day.date) }
                    .padding(top = 6.dp, bottom = 2.dp)
            )
            day.items.forEach { item ->
                UpcomingRow(
                    item = item,
                    onClick = {
                        when (item) {
                            is UpcomingItem.EventItem -> onSelectDate(day.date)
                            is UpcomingItem.TodoRow -> onOpenTodo()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onClick: () -> Unit) {
    val timeText = when (item) {
        is UpcomingItem.EventItem -> item.event.timeText
        is UpcomingItem.TodoRow -> item.todo.dueAt?.format(TIME_FORMAT) ?: ""
    }
    val color = when (item) {
        is UpcomingItem.EventItem -> item.event.color
        is UpcomingItem.TodoRow -> null
    }?.let { androidx.compose.ui.graphics.Color(it) } ?: MaterialTheme.colorScheme.primary

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title.ifBlank { "（无标题）" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (item is UpcomingItem.TodoRow && item.overdue) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (item is UpcomingItem.TodoRow && item.todo.note.isNotBlank()) {
                Text(
                    text = item.todo.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (item is UpcomingItem.TodoRow && item.overdue) {
            Text(
                text = "已过期",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dayHeader(today: LocalDate, date: LocalDate): String {
    val base = when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "周${Occurrences.weekdayName(date).removePrefix("周")}"
    }
    return "$base · ${date.monthValue}月${date.dayOfMonth}日"
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
