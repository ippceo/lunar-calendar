package com.lunarcalendar.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.content.edit

/**
 * 桌面小组件配置页：用户在桌面长按拖小组件到主屏时系统自动弹出。
 * 让用户选"显示日程 / 显示生日 / 显示纪念日"，保存后立即渲染一次。
 */
class LunarWidgetConfigActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val options = listOf(
        "schedule" to "显示日程",
        "birthday" to "显示生日",
        "anniversary" to "显示纪念日"
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

        val current = getSharedPreferences(LunarWidgetProvider.PREFS, MODE_PRIVATE)
            .getString(LunarWidgetProvider.PREF_KEY_VIEW, "birthday") ?: "birthday"

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
                            .selectableGroup()
                    ) {
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
                                RadioButton(
                                    selected = selected == value,
                                    onClick = null
                                )
                                Column {
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        when (value) {
                                            "schedule" -> "显示最近一条日程，倒计时到该日"
                                            "birthday" -> "显示最近一条生日，倒计时到生日"
                                            else -> "显示最新一个纪念日，累计已经多少天"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        androidx.compose.material3.Button(
                            onClick = { saveAndFinish(selected) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) { Text("确定") }
                    }
                }
            }
        }
    }

    private fun saveAndFinish(viewType: String) {
        getSharedPreferences(LunarWidgetProvider.PREFS, MODE_PRIVATE).edit {
            putString(LunarWidgetProvider.PREF_KEY_VIEW, viewType)
        }
        // 立即渲染一次
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
