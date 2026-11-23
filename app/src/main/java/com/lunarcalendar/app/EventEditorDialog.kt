package com.lunarcalendar.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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

    var title by remember(original.id) { mutableStateOf(original.title) }
    var type by remember(original.id) { mutableStateOf(original.type) }
    var date by remember(original.id) {
        mutableStateOf(original.anchorDate() ?: LocalDate.now())
    }
    var timeMinutes by remember(original.id) { mutableIntStateOf(original.timeMinutes) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var enabled by remember(original.id) { mutableStateOf(original.enabled) }

    val lunarInfo = LunarUtils.info(date)

    val pending = if (type == EventType.YEARLY_LUNAR) {
        original.copy(
            title = title.trim(),
            type = type,
            year = lunarInfo.lunarYear,
            month = lunarInfo.lunarMonth,
            day = lunarInfo.lunarDay,
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
                        onClick = { type = EventType.YEARLY_LUNAR },
                        label = { Text("农历生日") }
                    )
                }

                Text(
                    text = when (type) {
                        EventType.ONCE -> "只在这个日期提醒一次"
                        EventType.YEARLY_SOLAR -> "每年阳历的这一天提醒"
                        EventType.YEARLY_LUNAR ->
                            "每年农历${lunarInfo.monthLabel}${lunarInfo.dayName}提醒；闰月不存在时按平月计算"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
