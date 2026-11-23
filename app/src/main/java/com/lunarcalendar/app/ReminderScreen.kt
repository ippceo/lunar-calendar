package com.lunarcalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime

@Composable
fun ReminderScreen(
    events: List<CalEvent>,
    todos: List<TodoItem>,
    subTab: Int,
    onSubTabChange: (Int) -> Unit,
    onToggle: (CalEvent, Boolean) -> Unit,
    onEdit: (CalEvent) -> Unit,
    onToggleTodo: (TodoItem, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = subTab) { 4 }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { onSubTabChange(it) }
    }
    LaunchedEffect(subTab) {
        if (pagerState.currentPage != subTab) pagerState.animateScrollToPage(subTab)
    }

    val scheduleActive = remember(events) {
        events.filter { it.type == EventType.ONCE && !isExpired(it) }
            .sortedBy { sortKey(it) }
    }
    val scheduleExpired = remember(events) {
        events.filter { it.type == EventType.ONCE && isExpired(it) }
            .sortedBy { sortKey(it) }
    }
    val birthdays = remember(events) {
        events.filter { it.type == EventType.YEARLY_SOLAR || it.type == EventType.YEARLY_LUNAR }
            .sortedBy { sortKey(it) }
    }
    val anniversaries = remember(events) {
        events.filter { it.type == EventType.ANNIVERSARY }.sortedBy { sortKey(it) }
    }
    val todoItems = remember(todos) { todos.sortedForDisplay() }

    Column(Modifier.fillMaxSize()) {
        // 自绘 Tab：下划线位置跟随 pager 的实时拖动偏移，滑动时与手指同步
        val fraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
            .coerceIn(0f, 3f)
        val labels = listOf("日程", "生日", "纪念日", "待办")
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSubTabChange(index) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (subTab == index) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (subTab == index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
        ) {
            val tabWidth = maxWidth / labels.size
            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .offset(x = tabWidth * fraction)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 3) {
                TodoList(
                    todos = todoItems,
                    onToggleTodo = onToggleTodo,
                    onEditTodo = onEditTodo
                )
            } else {
                ReminderList(
                    page = page,
                    scheduleActive = scheduleActive,
                    scheduleExpired = scheduleExpired,
                    birthdays = birthdays,
                    anniversaries = anniversaries,
                    onToggle = onToggle,
                    onEdit = onEdit
                )
            }
        }
    }
}

@Composable
private fun ReminderList(
    page: Int,
    scheduleActive: List<CalEvent>,
    scheduleExpired: List<CalEvent>,
    birthdays: List<CalEvent>,
    anniversaries: List<CalEvent>,
    onToggle: (CalEvent, Boolean) -> Unit,
    onEdit: (CalEvent) -> Unit
) {
    val active = when (page) {
        0 -> scheduleActive
        1 -> birthdays
        else -> anniversaries
    }
    val expired = if (page == 0) scheduleExpired else emptyList()
    val total = active.size + expired.size
    val title = when (page) { 0 -> "日程"; 1 -> "生日"; else -> "纪念日" }
    val emptyHint = when (page) {
        0 -> "在“日历”页点右下角“新建”，添加一次性日程。"
        1 -> "在“日历”页点右下角“新建”，选择阳历生日或农历生日。"
        else -> "在“日历”页点右下角“新建”，选择“纪念日”类型，记录某个值得纪念的日子。"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部$title",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$total 条",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (total == 0) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有$title", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            emptyHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(active, key = { it.id }) { event ->
            ReminderCard(
                event = event,
                expired = false,
                onToggle = { onToggle(event, it) },
                onEdit = { onEdit(event) }
            )
        }

        if (expired.isNotEmpty()) {
            item(key = "expired-divider") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
            }
        }

        items(expired, key = { it.id }) { event ->
            ReminderCard(
                event = event,
                expired = true,
                onToggle = { onToggle(event, it) },
                onEdit = { onEdit(event) }
            )
        }
    }
}

@Composable
private fun ReminderCard(
    event: CalEvent,
    expired: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val dimmed = expired || !event.enabled
    val textColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // 过期日程仍可点开，便于修改日期或删除
    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onEdit)
        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)

    Card(Modifier.fillMaxWidth()) {
        Row(
            rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title.ifBlank { "未命名提醒" },
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (expired) TextDecoration.LineThrough else null,
                    color = textColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${Occurrences.ruleText(event)} · ${event.timeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (expired) {
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = Occurrences.nextLabel(event),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (event.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = event.enabled,
                onCheckedChange = onToggle,
                enabled = !expired
            )
        }
    }
}

@Composable
private fun TodoList(
    todos: List<TodoItem>,
    onToggleTodo: (TodoItem, Boolean) -> Unit,
    onEditTodo: (TodoItem) -> Unit
) {
    val remaining = todos.count { !it.done }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "全部待办",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$remaining 项未完成",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (todos.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没有待办", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "在“提醒”页点右下角“新建”，添加一条待办；也可以设置到点提醒。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(todos, key = { it.id }) { todo ->
            TodoCard(
                todo = todo,
                onToggle = { onToggleTodo(todo, it) },
                onEdit = { onEditTodo(todo) }
            )
        }
    }
}

@Composable
private fun TodoCard(todo: TodoItem, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val dimmed = todo.done
    val textColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = todo.done, onCheckedChange = onToggle)
            Column(Modifier.weight(1f)) {
                Text(
                    text = todo.title.ifBlank { "未命名待办" },
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    color = textColor
                )
                val subtitle = todoSubtitle(todo)
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun todoSubtitle(todo: TodoItem): String {
    val due = todo.dueAt
    val dueText = if (due != null) {
        buildString {
            append("${due.monthValue}月${due.dayOfMonth}日 ")
            append("%02d:%02d".format(due.hour, due.minute))
            if (!todo.done && due.isBefore(java.time.LocalDateTime.now())) append(" · 已过期")
        }
    } else {
        ""
    }
    return listOf(dueText, todo.note).filter { it.isNotBlank() }.joinToString(" · ")
}

/** 一次性日程且触发时间已过（停用不影响过期判断）即为过期。 */
private fun isExpired(event: CalEvent): Boolean {
    if (event.type != EventType.ONCE) return false
    val date = event.anchorDate() ?: return true
    val now = ZonedDateTime.now()
    val trigger = date
        .atTime(event.timeMinutes / 60, event.timeMinutes % 60)
        .atZone(now.zone)
    return !trigger.isAfter(now)
}

/**
 * 排序键：始终按事件本身的日期计算，忽略启用开关，
 * 避免用户开关提醒时列表顺序发生跳动。
 */
private fun sortKey(event: CalEvent): Long {
    val next = Occurrences.nextTrigger(event.copy(enabled = true)) ?: return Long.MAX_VALUE
    return next.toInstant().toEpochMilli()
}
