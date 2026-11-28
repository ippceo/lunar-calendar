package com.lunarcalendar.app

import android.Manifest
import android.content.Intent
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** 待办小组件点击标题：直接打开「提醒 → 待办」页，而不是停在日历页。 */
const val EXTRA_OPEN_TODO_TAB = "open_todo_tab"

class MainActivity : ComponentActivity() {
    private val deepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink.value = readDeepLink(intent)
        AppSettings.applyExcludeFromRecents(this)
        enableEdgeToEdge()
        setContent {
            var accent by remember { mutableIntStateOf(AppSettings.accent(this@MainActivity)) }
            LunarCalendarTheme(accent) {
                CalendarApp(
                    deepLink = deepLink.value,
                    onDeepLinkHandled = { deepLink.value = null },
                    accent = accent,
                    onAccentChange = { value ->
                        accent = value
                        AppSettings.setAccent(this@MainActivity, value)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLink.value = readDeepLink(intent)
    }

    private fun readDeepLink(intent: Intent): DeepLink? {
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        val todoId = intent.getStringExtra(EXTRA_TODO_ID)
        return when {
            eventId != null -> DeepLink(eventId = eventId)
            todoId != null -> DeepLink(todoId = todoId)
            intent.getBooleanExtra(EXTRA_OPEN_TODO_TAB, false) -> DeepLink(openTodoTab = true)
            else -> null
        }
    }
}

private data class DeepLink(
    val eventId: String? = null,
    val todoId: String? = null,
    val openTodoTab: Boolean = false
)

/** 主题色预设：名称 + 主色，供设置页展示。 */
internal val AccentOptions: List<Pair<String, Color>> = listOf(
    "拾光红" to Color(0xFFB3261E),
    "黛蓝" to Color(0xFF1D4ED8),
    "松绿" to Color(0xFF047857),
    "紫罗兰" to Color(0xFF7C3AED),
    "琥珀" to Color(0xFFB45309)
)

private data class AccentPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color
)

private val Accents = listOf(
    AccentPalette(
        Color(0xFFB3261E), Color(0xFFFFDAD6), Color(0xFF410002),
        Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A)
    ),
    AccentPalette(
        Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF172554),
        Color(0xFF93C5FD), Color(0xFF0B2E6F), Color(0xFF1E40AF)
    ),
    AccentPalette(
        Color(0xFF047857), Color(0xFFD1FAE5), Color(0xFF064E3B),
        Color(0xFF6EE7B7), Color(0xFF064E3B), Color(0xFF065F46)
    ),
    AccentPalette(
        Color(0xFF7C3AED), Color(0xFFEDE9FE), Color(0xFF4C1D95),
        Color(0xFFC4B5FD), Color(0xFF2E1065), Color(0xFF5B21B6)
    ),
    AccentPalette(
        Color(0xFFB45309), Color(0xFFFEF3C7), Color(0xFF78350F),
        Color(0xFFFCD34D), Color(0xFF451A03), Color(0xFF92400E)
    )
)

@Composable
fun LunarCalendarTheme(accent: Int = 0, content: @Composable () -> Unit) {
    val palette = Accents[accent.coerceIn(0, Accents.lastIndex)]
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.darkPrimary,
            onPrimary = palette.darkOnPrimary,
            primaryContainer = palette.darkContainer,
            onPrimaryContainer = Color(0xFFFFDAD6),
            background = Color(0xFF201A19),
            surface = Color(0xFF201A19)
        )
    } else {
        lightColorScheme(
            primary = palette.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.lightContainer,
            onPrimaryContainer = palette.lightOnContainer,
            secondary = Color(0xFF775651),
            background = Color(0xFFFFFBF8),
            surface = Color(0xFFFFFBF8),
            surfaceVariant = Color(0xFFF5DDDA),
            onSurfaceVariant = Color(0xFF534341)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
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
private fun CalendarApp(
    deepLink: DeepLink?,
    onDeepLinkHandled: () -> Unit,
    accent: Int,
    onAccentChange: (Int) -> Unit
) {
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
    var weekStart by remember { mutableIntStateOf(AppSettings.weekStart(context)) }
    var searchOpen by remember { mutableStateOf(false) }

    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        if (link.eventId != null) {
            tab = 1
            events.firstOrNull { it.id == link.eventId }?.let { event ->
                reminderSubTab = when (event.type) {
                    EventType.ONCE -> 0
                    EventType.YEARLY_SOLAR, EventType.YEARLY_LUNAR -> 1
                    EventType.ANNIVERSARY -> 2
                }
                editorTarget = EditorTarget(event, isNew = false)
            }
        } else if (link.todoId != null) {
            tab = 1
            reminderSubTab = 3
            todos.firstOrNull { it.id == link.todoId }?.let { todo ->
                todoEditorTarget = TodoEditorTarget(todo, isNew = false)
            }
        } else if (link.openTodoTab) {
            tab = 1
            reminderSubTab = 3
        }
        onDeepLinkHandled()
    }

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
        val updatedIds = updated.mapTo(HashSet()) { it.id }
        // 删除或编辑过的日程：收起已经投递的提醒，避免留下点进去找不到的僵尸通知
        events.forEach { previous ->
            if (updated.firstOrNull { it.id == previous.id } != previous) {
                Notifications.cancelEvent(context, previous.id)
            }
            // 被删除的日程要同时撤销系统闹钟（含未触发的「稍后提醒」）
            if (previous.id !in updatedIds) {
                ReminderScheduler.cancel(context, previous.id)
            }
        }
        events = updated
        EventRepository.save(context, updated)
        ReminderScheduler.rescheduleAll(context, updated)
        com.lunarcalendar.app.ui.widget.LunarWidgetProvider.forceRefreshAll(context)
        DataBackup.autoBackup(context, updated, todos)
    }

    fun persistTodos(updated: List<TodoItem>) {
        val updatedIds = updated.mapTo(HashSet()) { it.id }
        todos.forEach { previous ->
            if (updated.firstOrNull { it.id == previous.id } != previous) {
                Notifications.cancelTodo(context, previous.id)
            }
            if (previous.id !in updatedIds) {
                TodoScheduler.cancel(context, previous.id)
            }
        }
        todos = updated
        TodoRepository.save(context, updated)
        TodoScheduler.rescheduleAll(context, updated)
        com.lunarcalendar.app.ui.widget.TodoWidgetProvider.forceRefreshAll(context)
        DataBackup.autoBackup(context, events, updated)
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
                    IconButton(onClick = { searchOpen = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
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
                    todos = todos,
                    weekStart = weekStart,
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
                    },
                    onOpenTodo = {
                        tab = 1
                        reminderSubTab = 3
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
                    accent = accent,
                    onAccentChange = onAccentChange,
                    weekStart = weekStart,
                    onWeekStartChange = { value ->
                        weekStart = value
                        AppSettings.setWeekStart(context, value)
                    },
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

    if (searchOpen) {
        SearchDialog(
            events = events,
            todos = todos,
            onDismiss = { searchOpen = false },
            onPickEvent = { event ->
                searchOpen = false
                val date = Occurrences.nextTrigger(event)?.toLocalDate()
                    ?: runCatching { LocalDate.of(event.year, event.month, event.day) }.getOrNull()
                    ?: today
                selected = date
                cursor = YearMonth.from(date)
                tab = 0
            },
            onPickTodo = {
                searchOpen = false
                tab = 1
                reminderSubTab = 3
            }
        )
    }
}
