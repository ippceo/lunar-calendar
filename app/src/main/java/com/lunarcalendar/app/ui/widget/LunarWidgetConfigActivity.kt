package com.lunarcalendar.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lunarcalendar.app.CalEvent
import com.lunarcalendar.app.EventRepository
import com.lunarcalendar.app.EventType
import com.lunarcalendar.app.Occurrences
import java.time.ZonedDateTime

/**
 * 小组件设置页。
 * 入口：点击桌面上的小组件（占位或已配置）进入。
 *
 * 支持：
 *  - 选择显示内容：日程 / 生日 / 纪念日
 *  - 日程、生日支持「自动（最近一条）」或「手动选择」
 *  - 纪念日可选主显示为「已过天数」或「距下次天数」
 *
 * 配置按 widgetId 单独保存，同屏可放多个小组件显示不同内容。
 */
class LunarWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    @androidx.compose.material3.ExperimentalMaterial3Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val initialView = LunarWidgetProvider.getViewType(this, widgetId)
            ?: LunarWidgetProvider.VIEW_SCHEDULE
        val initialMode = LunarWidgetProvider.getMode(this, widgetId)
        val initialEventId = LunarWidgetProvider.getEventId(this, widgetId)
        val initialAnniv = LunarWidgetProvider.getAnnivPrimary(this, widgetId)
        val initialLayout = LunarWidgetProvider.getLayoutMode(this, widgetId)
        val initialCustomText = LunarWidgetProvider.getCustomText(this, widgetId)
        val events = EventRepository.load(this).filter { it.enabled }
        val now = ZonedDateTime.now()

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("小组件设置") }) }
                ) { pad ->
                    var view by remember { mutableStateOf(initialView) }
                    var mode by remember { mutableStateOf(initialMode) }
                    var eventId by remember { mutableStateOf(initialEventId) }
                    var annivPrimary by remember { mutableStateOf(initialAnniv) }
                    var layoutMode by remember { mutableStateOf(initialLayout) }
                    var customText by remember { mutableStateOf(initialCustomText) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(pad)
                            .selectableGroup()
                    ) {
                        item {
                            Text(
                                text = "先在「拾光历」中添加日程、生日或纪念日，小组件才会显示具体内容。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }

                        item { SectionTitle("显示内容") }
                        item {
                            RadioRow(
                                selected = view == LunarWidgetProvider.VIEW_SCHEDULE,
                                title = "日程",
                                subtitle = "最近一条未过期日程；不足 24 小时按小时倒计时",
                                onClick = { view = LunarWidgetProvider.VIEW_SCHEDULE }
                            )
                        }
                        item {
                            RadioRow(
                                selected = view == LunarWidgetProvider.VIEW_BIRTHDAY,
                                title = "生日",
                                subtitle = "接下来最近的一个生日，卡片同时显示年龄",
                                onClick = { view = LunarWidgetProvider.VIEW_BIRTHDAY }
                            )
                        }
                        item {
                            RadioRow(
                                selected = view == LunarWidgetProvider.VIEW_ANNIVERSARY,
                                title = "纪念日",
                                subtitle = "累计天数，可切换主显示为「还有多少天」",
                                onClick = { view = LunarWidgetProvider.VIEW_ANNIVERSARY }
                            )
                        }

                        if (view == LunarWidgetProvider.VIEW_SCHEDULE) {
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                            item { SectionTitle("日程显示") }
                            item {
                                RadioRow(
                                    selected = layoutMode == LunarWidgetProvider.LAYOUT_SINGLE,
                                    title = "单条",
                                    subtitle = "大号倒计时，只显示最近一条日程",
                                    onClick = { layoutMode = LunarWidgetProvider.LAYOUT_SINGLE }
                                )
                            }
                            item {
                                RadioRow(
                                    selected = layoutMode == LunarWidgetProvider.LAYOUT_LIST,
                                    title = "列表",
                                    subtitle = "按时间列出接下来的多条日程，点一条可直接打开",
                                    onClick = { layoutMode = LunarWidgetProvider.LAYOUT_LIST }
                                )
                            }
                        }

                        val singleMode = view != LunarWidgetProvider.VIEW_SCHEDULE ||
                            layoutMode == LunarWidgetProvider.LAYOUT_SINGLE
                        if (view != LunarWidgetProvider.VIEW_ANNIVERSARY && singleMode) {
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                            item { SectionTitle("显示方式") }
                            item {
                                RadioRow(
                                    selected = mode == LunarWidgetProvider.MODE_AUTO,
                                    title = "自动",
                                    subtitle = if (view == LunarWidgetProvider.VIEW_SCHEDULE) {
                                        "自动选择距离现在最近的一条日程"
                                    } else {
                                        "自动选择接下来最近的一个生日"
                                    },
                                    onClick = { mode = LunarWidgetProvider.MODE_AUTO }
                                )
                            }
                            item {
                                RadioRow(
                                    selected = mode == LunarWidgetProvider.MODE_MANUAL,
                                    title = "手动选择",
                                    subtitle = "在下方列表里指定要显示的一条",
                                    onClick = { mode = LunarWidgetProvider.MODE_MANUAL }
                                )
                            }
                        }

                        val showList = singleMode && (
                            view == LunarWidgetProvider.VIEW_ANNIVERSARY ||
                                mode == LunarWidgetProvider.MODE_MANUAL
                            )
                        if (showList) {
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                            item {
                                SectionTitle(
                                    if (view == LunarWidgetProvider.VIEW_ANNIVERSARY) "选择纪念日" else "选择要显示的一条"
                                )
                            }
                            val listItems = buildListItems(view, events, now)
                            if (listItems.isEmpty()) {
                                item {
                                    Text(
                                        text = "还没有可选择的${viewLabel(view)}，先去 App 里添加吧。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(listItems, key = { it.event.id }) { item ->
                                RadioRow(
                                    selected = eventId == item.event.id,
                                    title = item.event.title.ifBlank { viewLabel(view) },
                                    subtitle = item.subtitle,
                                    enabled = !item.expired,
                                    strikethrough = item.expired,
                                    onClick = { eventId = item.event.id }
                                )
                            }
                        }

                        if (view == LunarWidgetProvider.VIEW_ANNIVERSARY) {
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                            item { SectionTitle("卡片主显示") }
                            item {
                                RadioRow(
                                    selected = annivPrimary == LunarWidgetProvider.ANNIV_PASSED,
                                    title = "已经过的天数",
                                    subtitle = "大号数字显示「已经 X 天」，下方显示还有多少天",
                                    onClick = { annivPrimary = LunarWidgetProvider.ANNIV_PASSED }
                                )
                            }
                            item {
                                RadioRow(
                                    selected = annivPrimary == LunarWidgetProvider.ANNIV_REMAINING,
                                    title = "距离下次的天数",
                                    subtitle = "大号数字显示「还有 X 天」，下方显示已经多少天",
                                    onClick = { annivPrimary = LunarWidgetProvider.ANNIV_REMAINING }
                                )
                            }
                            item { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                            item { SectionTitle("卡片文案（可选）") }
                            item {
                                OutlinedTextField(
                                    value = customText,
                                    onValueChange = { customText = it.take(20) },
                                    singleLine = true,
                                    label = { Text("自定义说明") },
                                    placeholder = { Text("例如：距下次还有 69 天") },
                                    supportingText = {
                                        Text("留空则自动显示「距下次还有 X 天」或「已经走过 X 天」")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }

                        item {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    saveAndFinish(view, mode, eventId, annivPrimary, layoutMode, customText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) { Text("保存") }
                        }
                        item {
                            TextButton(
                                onClick = {
                                    startActivity(
                                        Intent(
                                            this@LunarWidgetConfigActivity,
                                            com.lunarcalendar.app.MainActivity::class.java
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("打开拾光历，去添加") }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    private data class ListItem(
        val event: CalEvent,
        val subtitle: String,
        val expired: Boolean = false
    )

    private fun buildListItems(
        view: String,
        events: List<CalEvent>,
        now: ZonedDateTime
    ): List<ListItem> = when (view) {
        LunarWidgetProvider.VIEW_SCHEDULE -> {
            val schedules = events.filter { it.type == EventType.ONCE }
            val (upcoming, expired) = schedules.partition { event ->
                Occurrences.nextTrigger(event)?.isAfter(now) == true
            }
            upcoming.sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() }
                .map { ListItem(it, Occurrences.nextLabel(it, now)) } +
                expired.sortedByDescending { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() }
                    .map { ListItem(it, "已过期 · ${Occurrences.ruleText(it)}", expired = true) }
        }

        LunarWidgetProvider.VIEW_BIRTHDAY -> events.filter {
            it.type == EventType.YEARLY_SOLAR || it.type == EventType.YEARLY_LUNAR
        }.sortedBy { Occurrences.nextTrigger(it)?.toInstant()?.toEpochMilli() ?: Long.MAX_VALUE }
            .map { ListItem(it, Occurrences.nextLabel(it, now)) }

        else -> events.filter { it.type == EventType.ANNIVERSARY }
            .sortedByDescending { Occurrences.anniversaryDaysPassed(it) }
            .map {
                ListItem(
                    it,
                    "已经 ${Occurrences.anniversaryDaysPassed(it)} 天 · ${Occurrences.ruleText(it)}"
                )
            }
    }

    private fun viewLabel(view: String): String = when (view) {
        LunarWidgetProvider.VIEW_SCHEDULE -> "日程"
        LunarWidgetProvider.VIEW_BIRTHDAY -> "生日"
        else -> "纪念日"
    }

    private fun saveAndFinish(
        view: String,
        mode: String,
        eventId: String?,
        annivPrimary: String,
        layoutMode: String,
        customText: String
    ) {
        LunarWidgetProvider.setViewType(this, widgetId, view)
        LunarWidgetProvider.setAnnivPrimary(this, widgetId, annivPrimary)
        LunarWidgetProvider.setLayoutMode(this, widgetId, layoutMode)
        LunarWidgetProvider.setCustomText(this, widgetId, customText.trim())
        if (view == LunarWidgetProvider.VIEW_ANNIVERSARY) {
            // 纪念日以上方列表中选择的一条为准
            val fallback = EventRepository.load(this)
                .filter { it.enabled && it.type == EventType.ANNIVERSARY }
                .maxByOrNull { Occurrences.anniversaryDaysPassed(it) }
                ?.id
            LunarWidgetProvider.setMode(this, widgetId, LunarWidgetProvider.MODE_MANUAL)
            LunarWidgetProvider.setEventId(this, widgetId, eventId ?: fallback)
        } else {
            LunarWidgetProvider.setMode(this, widgetId, mode)
            LunarWidgetProvider.setEventId(
                this,
                widgetId,
                if (mode == LunarWidgetProvider.MODE_MANUAL) eventId else null
            )
        }
        LunarWidgetProvider.update(
            this,
            AppWidgetManager.getInstance(this),
            widgetId
        )
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun RadioRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    strikethrough: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
