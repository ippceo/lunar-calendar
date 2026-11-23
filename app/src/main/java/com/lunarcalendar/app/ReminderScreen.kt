package com.lunarcalendar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime

@Composable
fun ReminderScreen(
    events: List<CalEvent>,
    onToggle: (CalEvent, Boolean) -> Unit,
    onEdit: (CalEvent) -> Unit
) {
    var subTab by remember { mutableIntStateOf(0) } // 0=日程, 1=生日

    val schedule = remember(events) {
        events.filter { it.type == EventType.ONCE }.sortedBy { sortKey(it) }
    }
    val birthdays = remember(events) {
        events.filter { it.type != EventType.ONCE }.sortedBy { sortKey(it) }
    }
    val list = if (subTab == 0) schedule else birthdays
    val title = if (subTab == 0) "日程" else "生日"
    val emptyHint = if (subTab == 0) {
        "在“日历”页点“新建”，添加一次性日程。"
    } else {
        "在“日历”页点“新建”，选择阳历生日或农历生日。"
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            Tab(
                selected = subTab == 0,
                onClick = { subTab = 0 },
                text = { Text("日程") }
            )
            Tab(
                selected = subTab == 1,
                onClick = { subTab = 1 },
                text = { Text("生日") }
            )
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
                        text = if (subTab == 0) "全部日程" else "全部生日",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${list.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (list.isEmpty()) {
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

            items(list, key = { it.id }) { event ->
                ReminderCard(
                    event = event,
                    onToggle = { onToggle(event, it) },
                    onEdit = { onEdit(event) }
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(event: CalEvent, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.title.ifBlank { "未命名提醒" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (event.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${Occurrences.ruleText(event)} · ${event.timeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Spacer(Modifier.width(8.dp))
            Switch(checked = event.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun sortKey(event: CalEvent): Long {
    if (!event.enabled) return Long.MAX_VALUE
    val next = Occurrences.nextTrigger(event) ?: return Long.MAX_VALUE - 1
    if (event.type == EventType.ONCE && !next.isAfter(ZonedDateTime.now())) {
        return Long.MAX_VALUE - 1
    }
    return next.toInstant().toEpochMilli()
}
