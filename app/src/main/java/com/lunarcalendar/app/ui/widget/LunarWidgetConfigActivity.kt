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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

/**
 * 小组件内容选择页。
 * 入口有两个：桌面上「＋」占位小组件点击进入；已配置的小组件内容被清空后也可再次进入。
 * 选择结果按 widgetId 单独保存，支持同屏放多个小组件显示不同类目。
 */
class LunarWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val options = listOf(
        LunarWidgetProvider.VIEW_SCHEDULE to "显示日程",
        LunarWidgetProvider.VIEW_BIRTHDAY to "显示生日",
        LunarWidgetProvider.VIEW_ANNIVERSARY to "显示纪念日"
    )

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

        val current = LunarWidgetProvider.getViewType(this, widgetId)
            ?: LunarWidgetProvider.VIEW_BIRTHDAY

        setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("选择显示内容") }) }
                ) { pad ->
                    var selected by remember { mutableStateOf(current) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(pad)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup()
                    ) {
                        Text(
                            text = "先在「拾光历」中添加日程、生日或纪念日，小组件才会显示具体内容。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                        options.forEach { (value, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected == value,
                                        role = Role.RadioButton,
                                        onClick = { selected = value }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(selected = selected == value, onClick = null)
                                Column {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        when (value) {
                                            LunarWidgetProvider.VIEW_SCHEDULE ->
                                                "显示最近一条日程，倒计时到该日"
                                            LunarWidgetProvider.VIEW_BIRTHDAY ->
                                                "显示最近一条生日，倒计时到生日"
                                            else ->
                                                "显示最新一个纪念日，累计已经多少天"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { saveAndFinish(selected) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) { Text("确定") }
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
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    private fun saveAndFinish(viewType: String) {
        LunarWidgetProvider.setViewType(this, widgetId, viewType)
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
