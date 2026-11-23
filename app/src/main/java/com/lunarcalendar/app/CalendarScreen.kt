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
import kotlin.math.abs

@Composable
fun CalendarScreen(
    cursor: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    events: List<CalEvent>,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (Long) -> Unit,
    onJumpToMonth: (YearMonth) -> Unit,
    onYearChange: (Int) -> Unit,
    onEditEvent: (CalEvent) -> Unit,
    onDeleteEvent: (CalEvent) -> Unit
) {
    val enabledEvents = events.filter { it.enabled }
    val startMonth = remember { YearMonth.of(MIN_YEAR, 1) }
    val pageCount = (MAX_YEAR - MIN_YEAR + 1) * 12
    val pagerState = rememberPagerState(
        initialPage = ChronoUnit.MONTHS.between(startMonth, cursor).toInt()
            .coerceIn(0, pageCount - 1)
    ) { pageCount }

    // 外部改变年月（拨盘、左右按钮、选中其它月份日期）时，滚动到对应页；跨越多页时直接定位
    LaunchedEffect(cursor) {
        val target = ChronoUnit.MONTHS.between(startMonth, cursor).toInt()
            .coerceIn(0, pageCount - 1)
        val distance = abs(target - pagerState.currentPage)
        when {
            distance == 0 -> Unit
            distance > 12 -> pagerState.scrollToPage(target)
            else -> pagerState.animateScrollToPage(target)
        }
    }
    // 手势滑动停稳后，把当前页同步回外层状态
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            onJumpToMonth(startMonth.plusMonths(page.toLong()))
        }
    }

    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var viewMode by rememberSaveable { mutableIntStateOf(0) } // 0 = 月视图, 1 = 年视图
    var showAdjacent by rememberSaveable { mutableStateOf(false) }
    var viewMenu by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
    ) {
        ViewModeBar(
            yearMode = viewMode == 1,
            showAdjacent = showAdjacent,
            menuOpen = viewMenu,
            onMenuOpenChange = { viewMenu = it },
            onModeChange = { viewMode = it },
            onToggleAdjacent = { showAdjacent = !showAdjacent }
        )
        if (viewMode == 0) {
            MonthSwitcher(
                cursor = cursor,
                onMonthChange = onMonthChange,
                onTitleClick = { showMonthPicker = true }
            )
            WeekdayHeader()
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
                    onSelect = onSelect
                )
            }
        } else {
            MonthSwitcher(
                cursor = cursor,
                onMonthChange = onMonthChange,
                onTitleClick = { showMonthPicker = true },
                yearOnly = true
            )
            Spacer(Modifier.height(4.dp))
            WeekdayHeader()
            Spacer(Modifier.height(6.dp))
            YearPager(
                cursor = cursor,
                selected = selected,
                today = today,
                enabledEvents = enabledEvents,
                onSelect = onSelect,
                onYearChange = onYearChange
            )
        }
        Spacer(Modifier.height(10.dp))
        DayDetail(
            date = selected,
            events = events.filter { it.enabled },
            onEditEvent = onEditEvent,
            onDeleteEvent = onDeleteEvent
        )
        Spacer(Modifier.height(96.dp))
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
    yearMode: Boolean,
    showAdjacent: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onModeChange: (Int) -> Unit,
    onToggleAdjacent: () -> Unit
) {
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
                    text = if (yearMode) "年视图" else "月视图",
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
                DropdownMenuItem(
                    text = { Text("月视图") },
                    trailingIcon = {
                        if (!yearMode) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        onModeChange(0)
                        onMenuOpenChange(false)
                    }
                )
                DropdownMenuItem(
                    text = { Text("年视图") },
                    trailingIcon = {
                        if (yearMode) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                    onClick = {
                        onModeChange(1)
                        onMenuOpenChange(false)
                    }
                )
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
private fun WeekdayHeader() {
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (index >= 5) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
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
        modifier = Modifier
            .fillMaxWidth()
            .height(772.dp)
    ) { page ->
        YearGrid(
            year = MIN_YEAR + page,
            month = cursor.monthValue,
            selected = selected,
            today = today,
            enabledEvents = enabledEvents,
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
    onSelect: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0 until 6) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (column in 0 until 2) {
                    val index = row * 2 + column + 1
                    MiniMonth(
                        month = YearMonth.of(year, index),
                        highlightedMonth = month,
                        selected = selected,
                        today = today,
                        enabledEvents = enabledEvents,
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
    enabledEvents: List<CalEvent>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier
) {
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - 1
    val dates = remember(month) {
        val start = firstDay.minusDays(leading.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val eventDays = remember(enabledEvents, month) {
        dates.filter { date -> enabledEvents.any { Occurrences.occursOn(it, date) } }.toSet()
    }
    val isCurrent = month.monthValue == highlightedMonth

    Box(
        modifier = modifier.height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // 月份数字直接作为背景水印，不再单独占一行
        Text(
            text = "${month.monthValue}月",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
            }
        )
        Column(Modifier.fillMaxWidth()) {
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
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .height(20.dp)
            .clickable(enabled = inMonth && !isSelected, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!inMonth) return@Box
        val color = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isToday -> MaterialTheme.colorScheme.primary
            date.dayOfWeek.value >= 6 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }
        Box(
            modifier = Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = color
            )
        }
        if (hasEvent && !isSelected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
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
    onSelect: (LocalDate) -> Unit
) {
    val firstDay = cursor.atDay(1)
    // 一周从周一开始：周一 = 0 … 周日 = 6
    val leading = firstDay.dayOfWeek.value - 1
    val dates = remember(cursor) {
        val start = firstDay.minusDays(leading.toLong())
        (0 until 42).map { start.plusDays(it.toLong()) }
    }
    val eventDays = remember(enabledEvents, cursor, showAdjacent) {
        dates.filter { date -> enabledEvents.any { Occurrences.occursOn(it, date) } }.toSet()
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

            val festivals = (info.festivals + info.otherFestivals).distinct()
            if (festivals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = festivals.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
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
                            onEdit = { onEditEvent(event) },
                            onDelete = { onDeleteEvent(event) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: CalEvent, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.timeText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
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
