package com.lunarcalendar.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(
    cursor: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    onAddEvent: () -> Unit,
    onEditEvent: (CalEvent) -> Unit,
    onDeleteEvent: (CalEvent) -> Unit
) {
    val firstDay = cursor.atDay(1)
    val leading = firstDay.dayOfWeek.value % 7
    val dates = remember(cursor) {
        val start = firstDay.minusDays(leading.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val enabledEvents = events.filter { it.enabled }
    val eventDays = remember(enabledEvents, cursor) {
        dates.filter { date -> enabledEvents.any { Occurrences.occursOn(it, date) } }.toSet()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
    ) {
        MonthSwitcher(cursor = cursor, onMonthChange = onMonthChange)
        WeekdayHeader()
        Spacer(Modifier.height(2.dp))
        MonthGrid(
            cursor = cursor,
            dates = dates,
            selected = selected,
            today = today,
            eventDays = eventDays,
            onSelect = onSelect
        )
        Spacer(Modifier.height(10.dp))
        DayDetail(
            date = selected,
            events = events.filter { it.enabled },
            onAddEvent = onAddEvent,
            onEditEvent = onEditEvent,
            onDeleteEvent = onDeleteEvent
        )
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun MonthSwitcher(cursor: YearMonth, onMonthChange: (Long) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(-1) }) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = "${cursor.year}年${cursor.monthValue}月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onMonthChange(1) }) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (index == 0 || index == 6) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun MonthGrid(
    cursor: YearMonth,
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit
) {
    Column {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = dates[row * 7 + column]
                    DayCell(
                        date = date,
                        inMonth = date.year == cursor.year && date.monthValue == cursor.monthValue,
                        isSelected = date == selected,
                        isToday = date == today,
                        hasEvent = eventDays.contains(date),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val info = LunarUtils.info(date)
    val shape = RoundedCornerShape(10.dp)
    val base = Modifier
        .padding(1.dp)
        .height(56.dp)
        .clip(shape)
        .then(
            when {
                isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                else -> Modifier
            }
        )
        .clickable(onClick = onClick)

    val isWeekend = date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isWeekend -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val lunarColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        info.isHighlightDay -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.then(base),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 15.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = dayColor
            )
            Text(
                text = LunarUtils.gridLabel(info),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = lunarColor
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (hasEvent) dotColor else Color.Transparent)
            )
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    events: List<CalEvent>,
    onAddEvent: () -> Unit,
    onEditEvent: (CalEvent) -> Unit,
    onDeleteEvent: (CalEvent) -> Unit
) {
    val info = LunarUtils.info(date)
    val dayEvents = remember(events, date) {
        events.filter { Occurrences.occursOn(it, date) }.sortedBy { it.timeMinutes }
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${date.monthValue}月${date.dayOfMonth}日 · ${Occurrences.weekdayName(date)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = info.fullLunarText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (info.jieQi.isNotEmpty()) {
                    Text(
                        text = info.jieQi,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val festivals = (info.festivals + info.otherFestivals).distinct()
            if (festivals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = festivals.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            if (dayEvents.isEmpty()) {
                Text(
                    text = "这天还没有日程，点下方按钮添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    dayEvents.forEach { event ->
                        EventRow(
                            event = event,
                            onEdit = { onEditEvent(event) },
                            onDelete = { onDeleteEvent(event) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAddEvent, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加日程 / 生日")
            }
        }
    }
}

@Composable
private fun EventRow(event: CalEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.timeText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(50.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(text = event.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = Occurrences.ruleText(event),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
