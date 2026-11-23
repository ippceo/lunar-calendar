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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
    var showLunarPicker by remember(original.id) { mutableStateOf(false) }
    var timeMinutes by remember(original.id) { mutableIntStateOf(original.timeMinutes) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var enabled by remember(original.id) { mutableStateOf(original.enabled) }

    val pending = if (type == EventType.YEARLY_LUNAR) {
        original.copy(
            title = title.trim(),
            type = type,
            year = LunarUtils.info(LocalDate.now()).lunarYear,
            month = lunarMonth,
            day = lunarDay,
            timeMinutes = timeMinutes,
            note = note.trim(),
            enabled = enabled
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
            enabled = enabled
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
                        label = { Text("日程") }
                    )
                    FilterChip(
                        selected = type == EventType.YEARLY_SOLAR,
                        onClick = { type = EventType.YEARLY_SOLAR },
                        label = { Text("阳历生日") }
                    )
                    FilterChip(
                        selected = type == EventType.YEARLY_LUNAR,
                        onClick = {
                            if (type != EventType.YEARLY_LUNAR) {
                                val li = LunarUtils.info(date)
                                lunarMonth = li.lunarMonth
                                lunarDay = li.lunarDay
                            }
                            type = EventType.YEARLY_LUNAR
                        },
                        label = { Text("农历生日") }
                    )
                }

                Text(
                    text = when (type) {
                        EventType.ONCE -> "只在这个日期提醒一次"
                        EventType.YEARLY_SOLAR -> "每年阳历的这一天提醒"
                        EventType.YEARLY_LUNAR -> {
                            val thisYear = LunarUtils.info(LocalDate.now()).lunarYear
                            val solar = LunarUtils.solarFromLunar(thisYear, lunarMonth, lunarDay)
                            val solarTxt = solar?.let { "（今年对应${it.monthValue}月${it.dayOfMonth}日）" } ?: ""
                            "每年农历${LunarUtils.monthLabelOf(lunarMonth)}${LunarUtils.dayNameOf(lunarDay)}提醒$solarTxt；闰月不存在时按平月计算"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (type == EventType.YEARLY_LUNAR) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "日期",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(52.dp)
                        )
                        OutlinedButton(onClick = { showLunarPicker = true }) {
                            Text("农历 ${LunarUtils.monthLabelOf(lunarMonth)}${LunarUtils.dayNameOf(lunarDay)}")
                        }
                    }
                    if (showLunarPicker) {
                        LunarDatePickerDialog(
                            initialMonth = lunarMonth,
                            initialDay = lunarDay,
                            onDismiss = { showLunarPicker = false },
                            onConfirm = { m, d ->
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
 * 农历日期选择对话框：左侧选月份（正月~腊月，含闰正月~闰腊月），右侧选日期（初一~三十）。
 * 月份值为负数表示闰月，与 [CalEvent] 存储约定一致。
 */
@Composable
private fun LunarDatePickerDialog(
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (month: Int, day: Int) -> Unit
) {
    // 前 12 项为平月（1..12），后 12 项为闰月（-1..-12）
    val monthEntries = remember { (1..12).toList() + (1..12).map { -it } }
    val monthLabels = remember { monthEntries.map { LunarUtils.monthLabelOf(it) }.toTypedArray() }
    val dayLabels = remember { (1..30).map { LunarUtils.dayNameOf(it) }.toTypedArray() }

    val initialMonthIndex = monthEntries.indexOf(initialMonth).let { if (it < 0) 0 else it }
    var monthIndex by remember { mutableIntStateOf(initialMonthIndex) }
    var dayIndex by remember { mutableIntStateOf((initialDay - 1).coerceIn(0, 29)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择农历日期") },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            TextButton(onClick = { onConfirm(monthEntries[monthIndex], dayIndex + 1) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
