package com.lunarcalendar.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.applyExcludeFromRecents(this)
        enableEdgeToEdge()
        setContent {
            LunarCalendarTheme {
                CalendarApp()
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775651),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    background = Color(0xFF201A19),
    surface = Color(0xFF201A19)
)

@Composable
fun LunarCalendarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

/** 旋转屏幕 / 进程重建后仍能保留当前查看的日期与月份。 */
private val LocalDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = { LocalDate.parse(it) }
)

private val YearMonthSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = { YearMonth.parse(it) }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var events by remember { mutableStateOf(EventRepository.load(context)) }
    var todos by remember { mutableStateOf(TodoRepository.load(context)) }
    var today by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(LocalDate.now())
    }
    var selected by rememberSaveable(stateSaver = LocalDateSaver) {
        mutableStateOf(today)
    }
    var cursor by rememberSaveable(stateSaver = YearMonthSaver) {
        mutableStateOf(YearMonth.from(today))
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var reminderSubTab by rememberSaveable { mutableIntStateOf(0) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var todoEditorTarget by remember { mutableStateOf<TodoEditorTarget?>(null) }
    var permissionTick by remember { mutableIntStateOf(0) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionTick++ }

    fun refreshFromStore() {
        today = LocalDate.now()
        events = EventRepository.load(context)
        todos = TodoRepository.load(context)
        ReminderScheduler.rescheduleAll(context, events)
        TodoScheduler.rescheduleAll(context, todos)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshFromStore()
                permissionTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        ReminderScheduler.rescheduleAll(context, events)
        TodoScheduler.rescheduleAll(context, todos)
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun persist(updated: List<CalEvent>) {
        events = updated
        EventRepository.save(context, updated)
        ReminderScheduler.rescheduleAll(context, updated)
        com.lunarcalendar.app.ui.widget.LunarWidgetProvider.forceRefreshAll(context)
    }

    fun persistTodos(updated: List<TodoItem>) {
        todos = updated
        TodoRepository.save(context, updated)
        TodoScheduler.rescheduleAll(context, updated)
        com.lunarcalendar.app.ui.widget.TodoWidgetProvider.forceRefreshAll(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("拾光", fontWeight = FontWeight.SemiBold)
                        Text(
                            "记住每一个值得的日子",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = {
                        today = LocalDate.now()
                        selected = today
                        cursor = YearMonth.from(today)
                    }) {
                        Text("回到今天")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("日历") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                    label = { Text("提醒") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editorTarget = EditorTarget(
                            event = CalEvent(
                                title = "",
                                type = EventType.ONCE,
                                year = selected.year,
                                month = selected.monthValue,
                                day = selected.dayOfMonth
                            ),
                            isNew = true
                        )
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新建") }
                )
            } else if (tab == 1 && reminderSubTab == 3) {
                ExtendedFloatingActionButton(
                    onClick = {
                        todoEditorTarget = TodoEditorTarget(TodoItem(title = ""), isNew = true)
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新建待办") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (tab == 0) {
                CalendarScreen(
                    cursor = cursor,
                    selected = selected,
                    today = today,
                    events = events,
                    onSelect = { date ->
                        selected = date
                        cursor = YearMonth.from(date)
                    },
                    onMonthChange = { delta ->
                        val next = cursor.plusMonths(delta)
                        cursor = when {
                            next.year < 1900 -> YearMonth.of(1900, 1)
                            next.year > 2100 -> YearMonth.of(2100, 12)
                            else -> next
                        }
                    },
                    onJumpToMonth = { ym ->
                        cursor = ym
                    },
                    onYearChange = { year ->
                        cursor = cursor.withYear(year.coerceIn(1900, 2100))
                    },
                    onEditEvent = { event ->
                        editorTarget = EditorTarget(event, isNew = false)
                    },
                    onDeleteEvent = { event ->
                        persist(events.filterNot { it.id == event.id })
                    }
                )
            } else if (tab == 1) {
                ReminderScreen(
                    events = events,
                    todos = todos,
                    subTab = reminderSubTab,
                    onSubTabChange = { reminderSubTab = it },
                    onToggle = { event, enabled ->
                        persist(events.map { if (it.id == event.id) it.copy(enabled = enabled) else it })
                    },
                    onEdit = { event ->
                        editorTarget = EditorTarget(event, isNew = false)
                    },
                    onToggleTodo = { todo, done ->
                        persistTodos(todos.map { if (it.id == todo.id) it.copy(done = done) else it })
                    },
                    onEditTodo = { todo ->
                        todoEditorTarget = TodoEditorTarget(todo, isNew = false)
                    }
                )
            } else {
                SettingsScreen(
                    events = events,
                    todos = todos,
                    permissionTick = permissionTick,
                    onSaveAll = { newEvents, newTodos -> persist(newEvents); persistTodos(newTodos) }
                )
            }
        }
    }

    val target = editorTarget
    if (target != null) {
        EventEditorDialog(
            target = target,
            onDismiss = { editorTarget = null },
            onSave = { saved ->
                editorTarget = null
                val updated = if (target.isNew) {
                    events + saved
                } else {
                    events.map { if (it.id == saved.id) saved else it }
                }
                persist(updated)
            },
            onDelete = { event ->
                editorTarget = null
                persist(events.filterNot { it.id == event.id })
            }
        )
    }

    val todoTarget = todoEditorTarget
    if (todoTarget != null) {
        TodoEditorDialog(
            target = todoTarget,
            onDismiss = { todoEditorTarget = null },
            onSave = { saved ->
                todoEditorTarget = null
                val updated = if (todoTarget.isNew) {
                    todos + saved
                } else {
                    todos.map { if (it.id == saved.id) saved else it }
                }
                persistTodos(updated)
            },
            onDelete = { todo ->
                todoEditorTarget = null
                persistTodos(todos.filterNot { it.id == todo.id })
            }
        )
    }
}
