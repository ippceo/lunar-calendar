package com.lunarcalendar.app

import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@Composable
fun SettingsScreen(
    events: List<CalEvent>,
    todos: List<TodoItem>,
    permissionTick: Int,
    accent: Int,
    onAccentChange: (Int) -> Unit,
    weekStart: Int,
    onWeekStartChange: (Int) -> Unit,
    onSaveAll: (List<CalEvent>, List<TodoItem>) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { AppearanceCard(accent, onAccentChange, weekStart, onWeekStartChange) }
        item { ReliabilityCard(permissionTick) }
        item { DataBackupCard(events, todos, onSaveAll) }
    }
}

@Composable
private fun AppearanceCard(
    accent: Int,
    onAccentChange: (Int) -> Unit,
    weekStart: Int,
    onWeekStartChange: (Int) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "外观与显示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "主题色",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AccentOptions.forEachIndexed { index, (name, color) ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onAccentChange(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (index == accent) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = name,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "每周起始日",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "周一", 1 to "周日").forEach { (value, label) ->
                    val selected = weekStart == value
                    FilterChip(
                        selected = selected,
                        onClick = { onWeekStartChange(value) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "影响月视图、年视图和周视图的排列，以及周视图的翻页",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReliabilityCard(permissionTick: Int) {
    val context = LocalContext.current
    val activity = context as? Activity
    var hideRecents by remember { mutableStateOf(AppSettings.excludeFromRecents(context)) }
    val notificationsOn = remember(permissionTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactAlarmOn = remember(permissionTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
    }
    val batteryFree = remember(permissionTick) {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "提醒可靠性",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "提醒走系统本地闹钟，不需要网络或推送服务，App 挂在后台即可。以下三项建议全部允许。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            PermissionRow("通知权限", notificationsOn) { openAppNotificationSettings(context) }
            PermissionRow("精确闹钟", exactAlarmOn) { openExactAlarmSettings(context) }
            PermissionRow("忽略电池优化", batteryFree) { openBatterySettings(context) }
            HorizontalDivider(
                Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "从最近任务隐藏",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "不在多任务界面显示，仍可通过桌面图标打开，提醒通知不受影响",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = hideRecents,
                    onCheckedChange = { v ->
                        hideRecents = v
                        AppSettings.setExcludeFromRecents(context, v)
                        activity?.let { AppSettings.applyExcludeFromRecents(it) }
                    }
                )
            }
        }
    }
}

@Composable
private fun DataBackupCard(
    events: List<CalEvent>,
    todos: List<TodoItem>,
    onSaveAll: (List<CalEvent>, List<TodoItem>) -> Unit
) {
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = DataBackup.writeToUri(context, uri, DataBackup.exportJson(events, todos))
        Toast.makeText(
            context,
            if (ok) "已导出 ${events.size} 条提醒、${todos.size} 条待办" else "导出失败",
            Toast.LENGTH_SHORT
        ).show()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = DataBackup.readFromUri(context, uri)
        val imported = text?.let { DataBackup.parseJson(it) }
        if (imported == null) {
            Toast.makeText(context, "导入失败：不是有效的备份文件", Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        onSaveAll(
            DataBackup.merge(events, imported.events),
            DataBackup.mergeTodos(todos, imported.todos)
        )
        Toast.makeText(
            context,
            "已导入 ${imported.events.size} 条提醒、${imported.todos.size} 条待办",
            Toast.LENGTH_SHORT
        ).show()
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "数据备份与迁移",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "把所有日程、生日、纪念日和待办导出成一个文件，换手机后在新设备上导入即可恢复。导入按 id 合并，同一条不会重复。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { exportLauncher.launch(DataBackup.backupFileName()) },
                    modifier = Modifier.weight(1f)
                ) { Text("导出") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入") }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "当前共 ${events.size} 条提醒、${todos.size} 条待办",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (granted) "已允许" else "去开启",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
        )
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
