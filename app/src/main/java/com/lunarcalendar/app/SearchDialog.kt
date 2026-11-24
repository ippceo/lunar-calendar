package com.lunarcalendar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate

private val EventTypeLabels = mapOf(
    EventType.ONCE to "日程",
    EventType.YEARLY_SOLAR to "每年（阳历）",
    EventType.YEARLY_LUNAR to "每年（农历）",
    EventType.ANNIVERSARY to "纪念日"
)

/** 全局搜索：在提醒与待办中按标题和备注查找，点击结果跳转到对应位置。 */
@Composable
fun SearchDialog(
    events: List<CalEvent>,
    todos: List<TodoItem>,
    onDismiss: () -> Unit,
    onPickEvent: (CalEvent) -> Unit,
    onPickTodo: (TodoItem) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyword = query.trim()

    val matchedEvents = remember(keyword, events) {
        if (keyword.isEmpty()) emptyList()
        else events.filter {
            it.title.contains(keyword, ignoreCase = true) ||
                it.note.contains(keyword, ignoreCase = true)
        }
    }
    val matchedTodos = remember(keyword, todos) {
        if (keyword.isEmpty()) emptyList()
        else todos.filter {
            it.title.contains(keyword, ignoreCase = true) ||
                it.note.contains(keyword, ignoreCase = true)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "搜索",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(MAX_TEXT_LENGTH) },
                    singleLine = true,
                    placeholder = { Text("搜索标题或备注") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                when {
                    keyword.isEmpty() -> Text(
                        text = "输入关键词，查找提醒、生日、纪念日和待办",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    matchedEvents.isEmpty() && matchedTodos.isEmpty() -> Text(
                        text = "没有找到「$keyword」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(Modifier.height(360.dp)) {
                        items(matchedEvents, key = { it.id }) { event ->
                            SearchRow(
                                title = event.title.ifBlank { "（无标题）" },
                                subtitle = buildString {
                                    append(EventTypeLabels[event.type] ?: "提醒")
                                    append(" · ")
                                    append(eventDateText(event))
                                    if (event.note.isNotBlank()) {
                                        append(" · ")
                                        append(event.note)
                                    }
                                },
                                enabled = event.enabled,
                                onClick = { onPickEvent(event) }
                            )
                        }
                        if (matchedEvents.isNotEmpty() && matchedTodos.isNotEmpty()) {
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                        }
                        items(matchedTodos, key = { it.id }) { todo ->
                            SearchRow(
                                title = todo.title.ifBlank { "（无内容）" },
                                subtitle = buildString {
                                    append("待办")
                                    if (todo.done) append(" · 已完成")
                                    if (todo.note.isNotBlank()) {
                                        append(" · ")
                                        append(todo.note)
                                    }
                                },
                                enabled = true,
                                onClick = { onPickTodo(todo) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun eventDateText(event: CalEvent): String {
    val date = runCatching { LocalDate.of(event.year, event.month, event.day) }.getOrNull()
    return when (event.type) {
        EventType.YEARLY_LUNAR -> "农历${event.month}月${event.day}日"
        else -> date?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: ""
    }
}

@Composable
private fun SearchRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
