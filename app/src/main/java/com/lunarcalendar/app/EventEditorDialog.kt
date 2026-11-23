package com.lunarcalendar.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalDate

data class EditorTarget(val event: CalEvent, val isNew: Boolean)

@Composable
fun EventEditorDialog(
    target: EditorTarget,
    onDismiss: () -> Unit,
    onSave: (CalEvent) -> Unit,
    onDelete: (CalEvent) -> Unit
) {
    val context = LocalContext.current
    val original = target.event

    val initialLunar = LunarUtils.info(original.anchorDate() ?: LocalDate.now())
    var title by remember(original.id) { mutableStateOf(original.title) }
    var type by remember(original.id) { mutableStateOf(original.type) }
    var date by remember(original.id) {
        mutableStateOf(original.anchorDate() ?: LocalDate.now())
    }
    var lunarMonth by remember(original.id) {
        mutableIntStateOf(if (original.type == EventType.YEARLY_LUNAR) original.month else initialLunar.lunarMonth)
    }
    var lunarDay by remember(original.id) {
        mutableIntStateOf(if (original.type == EventType.YEARLY_LUNAR) original.day else initialLunar.lunarDay)
    }
    var lunarYear by remember(original.id) {
        mutableIntStateOf(
            if (original.type == EventType.YEARLY_LUNAR && original.year > 0) {
                original.year
            } else {
                initialLunar.lunarYear
            }
        )
    }
    var showLunarPicker by remember(original.id) { mutableStateOf(false) }
    var advances by remember(original.id) { mutableStateOf(original.advanceReminders) }
    var showAdvanceAdder by remember(original.id) { mutableStateOf(false) }
    var timeMinutes by remember(original.id) { mutableIntStateOf(original.timeMinutes) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var enabled by remember(original.id) { mutableStateOf(original.enabled) }

    val pending = if (type == EventType.YEARLY_LUNAR) {
        original.copy(
            title = title.trim(),
            type = type,
            year = lunarYear,
            month = lunarMonth,
            day = lunarDay,
            timeMinutes = timeMinutes,
            note = note.trim(),
            enabled = enabled,
            advanceReminders = advances
        )
    } else {
        original.copy(
            title = title.trim(),
            type = type,
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth,
            timeMinutes = timeMinutes,
            note = note.trim(),
            enabled = enabled,
            advanceReminders = advances
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.isNew) "新建提醒" else "编辑提醒") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称，例如 妈妈生日") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == EventType.ONCE,
                        onClick = { type = EventType.ONCE },
                        label = { Text("日程", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == EventType.YEARLY_SOLAR,
                        onClick = { type = EventType.YEARLY_SOLAR },
                        label = { Text("阳历生日", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == EventType.YEARLY_LUNAR,
                        onClick = {
                            if (type != EventType.YEARLY_LUNAR) {
                                val li = LunarUtils.info(date)
                                lunarYear = li.lunarYear
                                lunarMonth = li.lunarMonth
                                lunarDay = li.lunarDay
                            }
                            type = EventType.YEARLY_LUNAR
                        },
                        label = { Text("农历生日", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == EventType.ANNIVERSARY,
                        onClick = { type = EventType.ANNIVERSARY },
                        label = { Text("纪念日", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = when (type) {
                        EventType.ONCE -> "只在这个日期提醒一次"
                        EventType.YEARLY_SOLAR -> "每年阳历的这一天提醒"
                        EventType.YEARLY_LUNAR -> "每年农历这一天提醒；闰月不存在时按平月计算"
                        EventType.ANNIVERSARY -> "每年同一天提醒；桌面组件显示“已经 X 天”"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // 固定两行高度，切换类型时下方内容不会上下跳动
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (type == EventType.YEARLY_LUNAR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(52.dp)
                        )
                        OutlinedButton(onClick = { showLunarPicker = true }) {
                            Text(
                                "农历 $lunarYear 年" +
                                    "${LunarUtils.monthLabelOf(lunarMonth)}${LunarUtils.dayNameOf(lunarDay)}"
                            )
                        }
                    }
                    if (showLunarPicker) {
                        LunarDatePickerDialog(
                            initialYear = lunarYear,
                            initialMonth = lunarMonth,
                            initialDay = lunarDay,
                            onDismiss = { showLunarPicker = false },
                            onConfirm = { y, m, d ->
                                lunarYear = y
                                lunarMonth = m
                                lunarDay = d
                                showLunarPicker = false
                            }
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(52.dp)
                        )
                        OutlinedButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    date = LocalDate.of(year, month + 1, day)
                                },
                                date.year,
                                date.monthValue - 1,
                                date.dayOfMonth
                            ).show()
                        }) {
                            Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日")
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "时间",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(52.dp)
                    )
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                timeMinutes = hour * 60 + minute
                            },
                            timeMinutes / 60,
                            timeMinutes % 60,
                            true
                        ).show()
                    }) {
                        Text("%02d:%02d".format(timeMinutes / 60, timeMinutes % 60))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "提前提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showAdvanceAdder = true },
                            enabled = advances.size < 7
                        ) {
                            Text("＋ 添加")
                        }
                    }
                    if (advances.isEmpty()) {
                        Text(
                            text = "可添加事件前 1~7 天（最多一周）的指定时间提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    advances.sortedBy { it.daysBefore }.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${item.daysBefore} 天前",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        advances = advances.map {
                                            if (it.daysBefore == item.daysBefore) {
                                                it.copy(timeMinutes = hour * 60 + minute)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    item.timeMinutes / 60,
                                    item.timeMinutes % 60,
                                    true
                                ).show()
                            }) {
                                Text(item.timeText)
                            }
                            IconButton(onClick = {
                                advances = advances.filterNot { it.daysBefore == item.daysBefore }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除提前提醒",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (showAdvanceAdder) {
                    AddAdvanceDialog(
                        existingDays = advances.map { it.daysBefore }.toSet(),
                        defaultTime = timeMinutes,
                        onDismiss = { showAdvanceAdder = false },
                        onConfirm = { days, minutes ->
                            advances = (advances.filterNot { it.daysBefore == days } +
                                AdvanceReminder(days, minutes)).sortedBy { it.daysBefore }
                            showAdvanceAdder = false
                        }
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "启用提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pending) },
                enabled = title.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (!target.isNew) {
                    TextButton(onClick = { onDelete(original) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

/**
 * 添加提前提醒：选择提前 1~7 天，以及当天的提醒时刻。
 */
@Composable
private fun AddAdvanceDialog(
    existingDays: Set<Int>,
    defaultTime: Int,
    onDismiss: () -> Unit,
    onConfirm: (days: Int, minutes: Int) -> Unit
) {
    val context = LocalContext.current
    val firstFree = (1..7).firstOrNull { it !in existingDays } ?: 1
    val dayLabels = remember { (1..7).map { "$it 天前" }.toTypedArray() }
    var days by remember { mutableIntStateOf(firstFree) }
    var minutes by remember { mutableIntStateOf(defaultTime) }
    val duplicate = days in existingDays

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加提前提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "提前天数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = dayLabels.size - 1
                            displayedValues = dayLabels
                            value = firstFree - 1
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal -> days = newVal + 1 }
                        }
                    },
                    update = { it.value = days - 1 },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "提醒时间",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> minutes = hour * 60 + minute },
                            minutes / 60,
                            minutes % 60,
                            true
                        ).show()
                    }) {
                        Text("%02d:%02d".format(minutes / 60, minutes % 60))
                    }
                }
                if (duplicate) {
                    Text(
                        text = "该天数已有提醒，添加后将替换",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(days, minutes) }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 农历日期选择对话框：依次选择年、月份（正月~腊月，含闰正月~闰腊月）、日期（初一~三十）。
 * 月份值为负数表示闰月，与 [CalEvent] 存储约定一致。
 */
@Composable
private fun LunarDatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit
) {
    // 前 12 项为平月（1..12），后 12 项为闰月（-1..-12）
    val monthEntries = remember { (1..12).toList() + (1..12).map { -it } }
    val monthLabels = remember { monthEntries.map { LunarUtils.monthLabelOf(it) }.toTypedArray() }
    val dayLabels = remember { (1..30).map { LunarUtils.dayNameOf(it) }.toTypedArray() }

    val initialMonthIndex = monthEntries.indexOf(initialMonth).let { if (it < 0) 0 else it }
    var year by remember { mutableIntStateOf(initialYear.coerceIn(1900, 2100)) }
    var monthIndex by remember { mutableIntStateOf(initialMonthIndex) }
    var dayIndex by remember { mutableIntStateOf((initialDay - 1).coerceIn(0, 29)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择农历日期") },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 1900
                            maxValue = 2100
                            value = initialYear.coerceIn(1900, 2100)
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal -> year = newVal }
                        }
                    },
                    update = { it.value = year },
                    modifier = Modifier.weight(1.3f)
                )
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = monthLabels.size - 1
                            displayedValues = monthLabels
                            value = initialMonthIndex
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal -> monthIndex = newVal }
                        }
                    },
                    update = { it.value = monthIndex },
                    modifier = Modifier.weight(1f)
                )
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = dayLabels.size - 1
                            displayedValues = dayLabels
                            value = (initialDay - 1).coerceIn(0, 29)
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setOnValueChangedListener { _, _, newVal -> dayIndex = newVal }
                        }
                    },
                    update = { it.value = dayIndex },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(year, monthEntries[monthIndex], dayIndex + 1) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
