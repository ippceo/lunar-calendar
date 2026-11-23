package com.lunarcalendar.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime

data class TodoEditorTarget(val todo: TodoItem, val isNew: Boolean)

@Composable
fun TodoEditorDialog(
    target: TodoEditorTarget,
    onDismiss: () -> Unit,
    onSave: (TodoItem) -> Unit,
    onDelete: (TodoItem) -> Unit
) {
    val context = LocalContext.current
    val original = target.todo

    var title by remember(original.id) { mutableStateOf(original.title) }
    var note by remember(original.id) { mutableStateOf(original.note) }
    var dueAt by remember(original.id) { mutableStateOf(original.dueAt) }

    val pending = original.copy(
        title = title.trim(),
        note = note.trim(),
        dueAt = dueAt
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (target.isNew) "新建待办" else "编辑待办",
                    modifier = Modifier.weight(1f)
                )
                if (!target.isNew) {
                    IconButton(onClick = { onDelete(original) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除待办")
                    }
                }
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("待办内容，例如 交房租") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("提醒时间", modifier = Modifier.weight(1f))
                    Switch(
                        checked = dueAt != null,
                        onCheckedChange = { on ->
                            dueAt = if (on) {
                                LocalDateTime.now()
                                    .withMinute(0)
                                    .withSecond(0)
                                    .withNano(0)
                                    .plusHours(1)
                            } else {
                                null
                            }
                        }
                    )
                }
                Text(
                    text = "开启后到点提醒；也可以不设时间，只当清单使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val current = dueAt
                if (current != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        dueAt = LocalDateTime.of(
                                            java.time.LocalDate.of(y, m + 1, d),
                                            current.toLocalTime()
                                        )
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth
                                ).show()
                            }
                        ) {
                            Text("${current.monthValue}月${current.dayOfMonth}日")
                        }
                        Spacer(Modifier.width(2.dp))
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        dueAt = LocalDateTime.of(
                                            current.toLocalDate(),
                                            LocalTime.of(h, min)
                                        )
                                    },
                                    current.hour,
                                    current.minute,
                                    true
                                ).show()
                            }
                        ) {
                            Text("%02d:%02d".format(current.hour, current.minute))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(pending) },
                enabled = title.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
