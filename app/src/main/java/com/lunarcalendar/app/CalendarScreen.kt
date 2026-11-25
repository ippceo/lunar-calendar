package com.lunarcalendar.app

import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import androidx.compose.ui.graphics.Color as ComposeColor

@Composable
fun CalendarScreen(
    cursor: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    todos: List<TodoItem>,
    weekStart: Int = 0,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    onJumpToMonth: (YearMonth) -> Unit,
    onYearChange: (Int) -> Unit,
    onEditEvent: (CalEvent) -> Unit,
    onDeleteEvent: (CalEvent) -> Unit,
    onOpenTodo: () -> Unit
) {
    val enabledEvents = remember(events) { events.filter { it.enabled } }
    val startMonth = remember { YearMonth.of(MIN_YEAR, 1) }
    val pageCount = (MAX_YEAR - MIN_YEAR + 1) * 12
    val pagerState = rememberPagerState(
        initialPage = ChronoUnit.MONTHS.between(startMonth, cursor).toInt()
            .coerceIn(0, pageCount - 1)
    ) { pageCount }

    var viewMode by rememberSaveable { mutableIntStateOf(0) }
    // 0 = 月视图, 1 = 年视图, 2 = 周视图, 3 = 日视图

    // 月视图才同步月分页；年/周/日视图选择日期时不能让隐藏的月分页把月份回写
    LaunchedEffect(cursor, viewMode) {
        if (viewMode != 0) return@LaunchedEffect
        val target = ChronoUnit.MONTHS.between(startMonth, cursor).toInt()
            .coerceIn(0, pageCount - 1)
        val distance = abs(target - pagerState.currentPage)
        when {
            distance == 0 -> Unit
            distance > 12 -> pagerState.scrollToPage(target)
            else -> pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState, viewMode) {
        if (viewMode != 0) return@LaunchedEffect
        var lastPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastPage) {
                lastPage = page
                onJumpToMonth(startMonth.plusMonths(page.toLong()))
            }
        }
    }

    // 周视图分页：默认以周一为起点，可在设置中改为周日
    val firstWeekday = if (weekStart == 0) java.time.DayOfWeek.MONDAY else java.time.DayOfWeek.SUNDAY
    val weekAnchor = startMonth.atDay(1)
        .with(TemporalAdjusters.previousOrSame(firstWeekday))
    val weekPageCount = (MAX_YEAR - MIN_YEAR + 1) * 54 + 2
    val weekPagerState = rememberPagerState(
        initialPage = ChronoUnit.WEEKS.between(
            weekAnchor,
            selected.with(TemporalAdjusters.previousOrSame(firstWeekday))
        ).toInt().coerceIn(0, weekPageCount - 1)
    ) { weekPageCount }
    LaunchedEffect(selected, viewMode) {
        if (viewMode != 2) return@LaunchedEffect
        val target = ChronoUnit.WEEKS.between(
            weekAnchor,
            selected.with(TemporalAdjusters.previousOrSame(firstWeekday))
        ).toInt().coerceIn(0, weekPageCount - 1)
        if (weekPagerState.currentPage != target) weekPagerState.scrollToPage(target)
    }

    // 日视图分页
    val dayPageCount = ChronoUnit.DAYS.between(
        startMonth.atDay(1).minusYears(10),
        YearMonth.of(MAX_YEAR, 12).atDay(31).plusYears(10)
    ).toInt() + 1
    val dayStartDate = startMonth.atDay(1).minusYears(10)
    val dayPagerState = rememberPagerState(
        initialPage = ChronoUnit.DAYS.between(dayStartDate, selected).toInt()
            .coerceIn(0, dayPageCount - 1)
    ) { dayPageCount }
    LaunchedEffect(selected, viewMode) {
        if (viewMode != 3) return@LaunchedEffect
        val target = ChronoUnit.DAYS.between(dayStartDate, selected).toInt()
            .coerceIn(0, dayPageCount - 1)
        if (dayPagerState.currentPage != target) dayPagerState.scrollToPage(target)
    }

    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var showAdjacent by rememberSaveable { mutableStateOf(false) }
    var viewMenu by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
    ) {
        ViewModeBar(
            viewMode = viewMode,
            showAdjacent = showAdjacent,
            menuOpen = viewMenu,
            onMenuOpenChange = { viewMenu = it },
            onModeChange = { viewMode = it },
            onToggleAdjacent = { showAdjacent = !showAdjacent }
        )
        when (viewMode) {
            0 -> { // 月视图
                MonthSwitcher(
                    cursor = cursor,
                    onMonthChange = onMonthChange,
                    onTitleClick = { showMonthPicker = true }
                )
                WeekdayHeader(weekStart)
                Spacer(Modifier.height(2.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp * 6)
                ) { page ->
                    val pageMonth = startMonth.plusMonths(page.toLong())
                    MonthPage(
                        cursor = pageMonth,
                        selected = selected,
                        today = today,
                        enabledEvents = enabledEvents,
                        showAdjacent = showAdjacent,
                        weekStart = weekStart,
                        onSelect = onSelect
                    )
                }
            }
            1 -> { // 年视图
                MonthSwitcher(
                    cursor = cursor,
                    onMonthChange = onMonthChange,
                    onTitleClick = { showMonthPicker = true },
                    yearOnly = true
                )
                Spacer(Modifier.height(6.dp))
                YearPager(
                    cursor = cursor,
                    selected = selected,
                    today = today,
                    enabledEvents = enabledEvents,
                    weekStart = weekStart,
                    onSelect = onSelect,
                    onYearChange = onYearChange
                )
            }
            2 -> { // 周视图
                val weekBegin = selected.with(TemporalAdjusters.previousOrSame(firstWeekday))
                val weekEnd = weekBegin.plusDays(6)
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${weekBegin.monthValue}月${weekBegin.dayOfMonth}日 – ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一周")
                    }
                }
                WeekPager(
                    pagerState = weekPagerState,
                    dayStart = weekAnchor,
                    selected = selected,
                    today = today,
                    enabledEvents = enabledEvents,
                    onSelect = onSelect
                )
            }
            3 -> { // 日视图
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onSelect(selected.minusDays(1)) }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一天")
                    }
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${selected.year}年${selected.monthValue}月${selected.dayOfMonth}日",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = { onSelect(selected.plusDays(1)) }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一天")
                    }
                }
                DayPager(
                    pagerState = dayPagerState,
                    dayStart = dayStartDate,
                    selected = selected,
                    enabledEvents = enabledEvents,
                    onSelect = onSelect
                )
            }
            4 -> { // 近期：未来 7/30 天的日程与待办
                UpcomingView(
                    today = today,
                    events = enabledEvents,
                    todos = todos,
                    onSelectDate = onSelect,
                    onOpenTodo = onOpenTodo
                )
            }
        }
        if (viewMode != 3) {
            Spacer(Modifier.height(10.dp))
            DayDetail(
                date = selected,
                events = events.filter { it.enabled },
                onEditEvent = onEditEvent,
                onDeleteEvent = onDeleteEvent
            )
            Spacer(Modifier.height(96.dp))
        } else {
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            current = cursor,
            onDismiss = { showMonthPicker = false },
            onPick = { ym ->
                onJumpToMonth(ym)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun ViewModeBar(
    viewMode: Int,
    showAdjacent: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onModeChange: (Int) -> Unit,
    onToggleAdjacent: () -> Unit
) {
    val modeLabel = when (viewMode) {
        1 -> "年视图"
        2 -> "周视图"
        3 -> "日视图"
        4 -> "近期"
        else -> "月视图"
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMenuOpenChange(true) }
                    .padding(start = 2.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "切换视图",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) }
            ) {
                listOf(0 to "月视图", 1 to "年视图", 2 to "周视图", 3 to "日视图", 4 to "近期").forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = {
                            if (viewMode == mode) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            onModeChange(mode)
                            onMenuOpenChange(false)
                        }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("显示相邻月份日期") },
                    trailingIcon = {
                        if (showAdjacent) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = onToggleAdjacent
                )
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    cursor: YearMonth,
    onMonthChange: (Long) -> Unit,
    onTitleClick: () -> Unit,
    yearOnly: Boolean = false
) {
    val step = if (yearOnly) 12L else 1L
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onMonthChange(-step) }) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = if (yearOnly) "上一年" else "上个月"
            )
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTitleClick)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = if (yearOnly) "${cursor.year}年" else "${cursor.year}年${cursor.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "选择年月",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { onMonthChange(step) }) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = if (yearOnly) "下一年" else "下个月"
            )
        }
    }
}

/**
 * 年月跳转：上下拨盘选择年份与月份，可快速滑动到任意年份。
 */
@Composable
private fun MonthYearPickerDialog(
    current: YearMonth,
    onDismiss: () -> Unit,
    onPick: (YearMonth) -> Unit
) {
    var year by remember { mutableIntStateOf(current.year.coerceIn(MIN_YEAR, MAX_YEAR)) }
    var month by remember { mutableIntStateOf(current.monthValue) }
    val monthLabels = remember { (1..12).map { "${it}月" }.toTypedArray() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年月") },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WheelColumn("年", Modifier.weight(1.4f)) {
                    AndroidView(
                        factory = { ctx ->
                            NumberPicker(ctx).apply {
                                minValue = MIN_YEAR
                                maxValue = MAX_YEAR
                                value = year
                                wrapSelectorWheel = false
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setOnValueChangedListener { _, _, v -> year = v }
                            }
                        },
                        update = { if (it.value != year) it.value = year },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                WheelColumn("月", Modifier.weight(1f)) {
                    AndroidView(
                        factory = { ctx ->
                            NumberPicker(ctx).apply {
                                minValue = 0
                                maxValue = 11
                                displayedValues = monthLabels
                                value = month - 1
                                wrapSelectorWheel = true
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                setOnValueChangedListener { _, _, v -> month = v + 1 }
                            }
                        },
                        update = { if (it.value != month - 1) it.value = month - 1 },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(YearMonth.of(year, month)) }) { Text("确定") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    year = YearMonth.now().year
                    month = YearMonth.now().monthValue
                }) { Text("今年") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun WheelColumn(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

private const val MIN_YEAR = 1900
private const val MAX_YEAR = 2100

@Composable
private fun WeekdayHeader(weekStart: Int = 0) {
    val labels = if (weekStart == 0) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("日", "一", "二", "三", "四", "五", "六")
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (label == "六" || label == "日") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun MiniWeekdayHeader(weekStart: Int = 0) {
    val labels = if (weekStart == 0) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("日", "一", "二", "三", "四", "五", "六")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(14.dp)
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                maxLines = 1,
                color = if (label == "六" || label == "日") {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                }
            )
        }
    }
}

@Composable
private fun YearPager(
    cursor: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    enabledEvents: List<CalEvent>,
    weekStart: Int,
    onSelect: (LocalDate) -> Unit,
    onYearChange: (Int) -> Unit
) {
    val yearCount = MAX_YEAR - MIN_YEAR + 1
    val pager = rememberPagerState(
        initialPage = (cursor.year - MIN_YEAR).coerceIn(0, yearCount - 1)
    ) { yearCount }
    LaunchedEffect(cursor.year) {
        val target = (cursor.year - MIN_YEAR).coerceIn(0, yearCount - 1)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val year = MIN_YEAR + page
            if (year != cursor.year) onYearChange(year)
        }
    }
    HorizontalPager(
        state = pager,
        // 提前组合相邻年份，滑动时不必在拖拽中同步组合 12 个月的内容
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(580.dp)
    ) { page ->
        YearGrid(
            year = MIN_YEAR + page,
            month = cursor.monthValue,
            selected = selected,
            today = today,
            enabledEvents = enabledEvents,
            weekStart = weekStart,
            onSelect = onSelect
        )
    }
}

@Composable
private fun YearGrid(
    year: Int,
    month: Int,
    selected: LocalDate,
    today: LocalDate,
    enabledEvents: List<CalEvent>,
    weekStart: Int,
    onSelect: (LocalDate) -> Unit
) {
    // 整年一次性展开，避免每个 MiniMonth 各自扫描 42 天
    val eventDays = remember(enabledEvents, year) {
        Occurrences.datesIn(enabledEvents, year..year)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until 4) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (column in 0 until 3) {
                    val index = row * 3 + column + 1
                    MiniMonth(
                        month = YearMonth.of(year, index),
                        highlightedMonth = month,
                        selected = selected,
                        today = today,
                        eventDays = eventDays,
                        weekStart = weekStart,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    highlightedMonth: Int,
    selected: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    weekStart: Int,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier
) {
    val firstDay = month.atDay(1)
    val leading = (firstDay.dayOfWeek.value - 1 - weekStart + 7) % 7
    val dates = remember(month, weekStart) {
        val start = firstDay.minusDays(leading.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val isCurrent = month.monthValue == highlightedMonth
    // 颜色在月份层级取一次，避免 42 个格子各自读 CompositionLocal
    val scheme = MaterialTheme.colorScheme
    val primary = scheme.primary
    val onPrimary = scheme.onPrimary
    val onSurface = scheme.onSurface

    Box(
        modifier = modifier.height(136.dp),
        contentAlignment = Alignment.Center
    ) {
        // 月份作为背景水印，不额外占用标题行
        Text(
            text = "${month.monthValue}月",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCurrent) {
                primary.copy(alpha = 0.22f)
            } else {
                onSurface.copy(alpha = 0.13f)
            }
        )
        Column(Modifier.fillMaxWidth()) {
            MiniWeekdayHeader(weekStart)
            for (row in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val date = dates[row * 7 + column]
                        MiniDayCell(
                            date = date,
                            inMonth = date.year == month.year &&
                                date.monthValue == month.monthValue,
                            isSelected = date == selected,
                            isToday = date == today,
                            hasEvent = eventDays.contains(date),
                            primary = primary,
                            onPrimary = onPrimary,
                            onSurface = onSurface,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDayCell(
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    primary: Color,
    onPrimary: Color,
    onSurface: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) primary else Color.Transparent
            )
            .clickable(enabled = inMonth && !isSelected, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!inMonth) return@Box
        val color = when {
            isSelected -> onPrimary
            isToday -> primary
            date.dayOfWeek.value >= 6 -> primary
            else -> onSurface
        }
        Text(
            text = date.dayOfMonth.toString(),
            fontSize = 9.sp,
            maxLines = 1,
            softWrap = false,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
        if (hasEvent && !isSelected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(primary)
            )
        }
    }
}

@Composable
private fun MonthPage(
    cursor: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    enabledEvents: List<CalEvent>,
    showAdjacent: Boolean,
    weekStart: Int = 0,
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = cursor.atDay(1)
    // 一周从周一或周日开始：leading 为当月 1 号之前的空格数
    val leading = (firstDay.dayOfWeek.value - 1 - weekStart + 7) % 7
    val dates = remember(cursor, weekStart) {
        val start = firstDay.minusDays(leading.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val eventDays = remember(enabledEvents, cursor) {
        Occurrences.datesIn(enabledEvents, dates.first().year..dates.last().year)
    }
    MonthGrid(
        cursor = cursor,
        dates = dates,
        selected = selected,
        today = today,
        eventDays = eventDays,
        showAdjacent = showAdjacent,
        onSelect = onSelect
    )
}

@Composable
private fun MonthGrid(
    cursor: YearMonth,
    dates: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    showAdjacent: Boolean,
    onSelect: (LocalDate) -> Unit
) {
    Column {
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = dates[row * 7 + column]
                    DayCell(
                        date = date,
                        inMonth = date.year == cursor.year && date.monthValue == cursor.monthValue,
                        showAdjacent = showAdjacent,
                        isSelected = date == selected,
                        isToday = date == today,
                        hasEvent = eventDays.contains(date),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    showAdjacent: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    if (!inMonth && !showAdjacent) {
        // 不显示相邻月份时用空白占位，保持网格对齐
        Box(
            modifier
                .padding(1.dp)
                .height(56.dp)
        )
        return
    }
    val info = LunarUtils.info(date)
    val shape = RoundedCornerShape(10.dp)
    val base = Modifier
        .padding(1.dp)
        .height(56.dp)
        .clip(shape)
        .then(
            when {
                isSelected -> Modifier.background(MaterialTheme.colorScheme.primary)
                isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                else -> Modifier
            }
        )
        .clickable(onClick = onClick)

    val isWeekend = date.dayOfWeek.value == 6 || date.dayOfWeek.value == 7
    val dayColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        isWeekend -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val lunarColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        info.isHighlightDay -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.then(base),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 15.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = dayColor
            )
            Text(
                text = LunarUtils.gridLabel(info),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = lunarColor
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (hasEvent) dotColor else Color.Transparent)
            )
        }
    }
}

/**
 * 周视图：7 列每天一列，顶部是日期格子，下面是当天事件列表。
 */
@Composable
private fun WeekPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    dayStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    enabledEvents: List<CalEvent>,
    onSelect: (LocalDate) -> Unit
) {
    // 预展开整年的打点集合，避免每个 Pager 页面重复算
    val yearRange = remember(selected.year) { selected.year - 1..selected.year + 1 }
    val eventDays = remember(enabledEvents, yearRange) {
        Occurrences.datesIn(enabledEvents, yearRange)
    }
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    ) { page ->
        val weekStart = dayStart.plusDays(page * 7L)
        val dates = remember(weekStart) { (0 until 7).map { weekStart.plusDays(it.toLong()) } }
        Row(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            dates.forEach { date ->
                DayColumn(
                    date = date,
                    isSelected = date == selected,
                    isToday = date == today,
                    hasEvent = eventDays.contains(date),
                    events = enabledEvents.filter { Occurrences.occursOn(it, date) },
                    onClick = { onSelect(date) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 日视图：24 小时时间轴 + 事件块。
 */
@Composable
private fun DayPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    dayStart: LocalDate,
    selected: LocalDate,
    enabledEvents: List<CalEvent>,
    onSelect: (LocalDate) -> Unit
) {
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = 1,
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
    ) { page ->
        val date = dayStart.plusDays(page.toLong())
        DayTimeline(
            date = date,
            events = enabledEvents.filter { Occurrences.occursOn(it, date) }.sortedBy { it.timeMinutes },
            onSelect = onSelect
        )
    }
}

/** 周视图里的一列：日期号 + 事件数点 + 事件列表前 2 条。 */
@Composable
private fun DayColumn(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvent: Boolean,
    events: List<CalEvent>,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    val scheme = MaterialTheme.colorScheme
    val bgColor = when {
        isSelected -> scheme.primary
        isToday -> scheme.primary.copy(alpha = 0.15f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        date.dayOfWeek.value >= 6 -> scheme.primary
        else -> scheme.onSurface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = weekdays[date.dayOfWeek.value - 1],
            fontSize = 8.sp,
            color = textColor.copy(alpha = 0.7f)
        )
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) scheme.onPrimary.copy(alpha = 0.2f) else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 11.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        if (hasEvent) {
            Box(
                Modifier
                    .padding(top = 1.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) scheme.onPrimary else scheme.primary)
            )
        }
        // 事件列表前 2 条（紧凑版）
        Spacer(Modifier.height(2.dp))
        events.take(2).forEach { event ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 1.dp, vertical = 0.5.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = event.title.take(4),
                    fontSize = 7.sp,
                    maxLines = 1,
                    color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant
                )
            }
        }
        if (events.size > 2) {
            Text(
                text = "+${events.size - 2}",
                fontSize = 7.sp,
                color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant
            )
        }
    }
}

/** 日视图时间轴：24 小时行，事件按 timeMinutes 定位。 */
@Composable
private fun DayTimeline(
    date: LocalDate,
    events: List<CalEvent>,
    onSelect: (LocalDate) -> Unit
) {
    val info = LunarUtils.info(date)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        // 顶部：日期头
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 · ${Occurrences.weekdayName(date)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = info.fullLunarText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "这天还没有日程",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                events.forEach { event ->
                    val eventColor = ComposeColor(
                        (event.color ?: MaterialTheme.colorScheme.primary.value.toLong() and 0xFFFFFFFFL).toInt()
                    )
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = eventColor.copy(alpha = 0.12f))
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp, 36.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(eventColor)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = event.timeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = eventColor
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = Occurrences.ruleText(event),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                                if (event.note.isNotEmpty()) {
                                    Text(
                                        text = event.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    events: List<CalEvent>,
    onEditEvent: (CalEvent) -> Unit,
    onDeleteEvent: (CalEvent) -> Unit
) {
    val info = LunarUtils.info(date)
    val dayEvents = remember(events, date) {
        events.filter { Occurrences.occursOn(it, date) }.sortedBy { it.timeMinutes }
    }
    var detailTarget by remember { mutableStateOf<CalEvent?>(null) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${date.monthValue}月${date.dayOfMonth}日 · ${Occurrences.weekdayName(date)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = info.fullLunarText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (info.jieQi.isNotEmpty()) {
                    Text(
                        text = info.jieQi,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 节气倒计时：15 天内才显示，避免「距冬至还有 200 天」这种噪音
            LunarUtils.nextSolarTerm(date)?.let { (termName, daysLeft) ->
                if (daysLeft in 0..15) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (daysLeft == 0) "今天是$termName" else "距$termName 还有 $daysLeft 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val festivals = (info.festivals + info.otherFestivals).distinct()
            if (festivals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = festivals.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 闰月 / 三伏 / 数九等传统时段标注，没有时不占位
            val traditionalTags = buildList {
                if (info.isLeapMonth) add("闰月")
                info.specialNote?.let { add(it) }
            }
            if (traditionalTags.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = traditionalTags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            )

            if (dayEvents.isEmpty()) {
                Text(
                    text = "这天还没有日程，点右下角「新建」添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    dayEvents.forEach { event ->
                        EventRow(
                            event = event,
                            onTap = { detailTarget = event },
                            onDelete = { onDeleteEvent(event) }
                        )
                    }
                }
            }
        }
    }

    detailTarget?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { detailTarget = null },
            onEdit = {
                detailTarget = null
                onEditEvent(event)
            },
            onDelete = {
                detailTarget = null
                onDeleteEvent(event)
            }
        )
    }
}

@Composable
private fun EventDetailDialog(
    event: CalEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val argb = EventColors.resolve(event.color, themeColor.value.toLong())
    val dotColor = ComposeColor(argb and 0xFFFFFFFFL)
    val anchor = event.anchorDate()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(event.title, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("类型：${event.typeLabel()}", style = MaterialTheme.typography.bodyMedium)
                if (anchor != null) {
                    val next = Occurrences.nextTrigger(event)?.toLocalDate()
                    Text(
                        buildString {
                            append("日期：${anchor.year}年${anchor.monthValue}月${anchor.dayOfMonth}日")
                            if (event.type == EventType.YEARLY_LUNAR) {
                                val li = LunarUtils.info(anchor)
                                append(" · ${li.fullLunarText}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (next != null) {
                        val age = event.birthdayAge(next)
                        if (age != null) {
                            Text("下次 ${next.monthValue}月${next.dayOfMonth}日 · $age 岁", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("下次 ${next.monthValue}月${next.dayOfMonth}日", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("时间：${event.timeText}", style = MaterialTheme.typography.bodyMedium)
                if (event.advanceReminders.isNotEmpty()) {
                    Text(
                        "提前：${event.advanceReminders.sortedBy { it.daysBefore }.joinToString("、") { "${it.daysBefore}天前${it.timeText}" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.note.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                    Text("备注：${event.note}", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = if (event.enabled) "已启用提醒" else "已关闭提醒",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (event.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun EventRow(event: CalEvent, onTap: () -> Unit, onDelete: () -> Unit) {
    val themeColor = MaterialTheme.colorScheme.primary
    val argb = EventColors.resolve(event.color, themeColor.value.toLong())
    val dotColor = ComposeColor(argb and 0xFFFFFFFFL)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = event.timeText,
            style = MaterialTheme.typography.labelLarge,
            color = dotColor,
            modifier = Modifier.width(50.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(text = event.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = Occurrences.ruleText(event),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
