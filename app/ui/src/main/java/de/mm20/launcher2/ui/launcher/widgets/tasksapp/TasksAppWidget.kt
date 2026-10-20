package de.mm20.launcher2.ui.launcher.widgets.tasksapp

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.tasksapp.TasksAppRepository
import de.mm20.launcher2.tasksapp.TasksAppTask
import de.mm20.launcher2.tasksapp.day
import de.mm20.launcher2.tasksapp.inside
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.widgets.calendar.Info
import de.mm20.launcher2.widgets.TasksAppWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val GUTTER = 52

private fun nowMin() = LocalTime.now().let { it.hour * 60 + it.minute }
private fun hm(m: Int) = "%02d:%02d".format(m / 60 % 24, m % 60)
private fun hours(m: Int) = if (m % 60 == 0) "${m / 60} h" else if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
private fun TasksAppTask.start() = dueDate?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }?.let { it.hour * 60 + it.minute } ?: 0

/** The web's priority colours; none = outline. */
@Composable
private fun prio(p: Int) = when (p) {
    1 -> Color(0xFF3E6AE1)
    2 -> Color(0xFFE6A23C)
    3 -> Color(0xFFC4314B)
    else -> MaterialTheme.colorScheme.outline
}

/**
 * Oggi as the Tasks app shows it: what is late, what is for today at any time, then the day in order – each thing at
 * its time, a block as a panel with its tasks inside, the free time between, a line where now is. A tap opens the task
 * in the app, the circle completes it.
 */
@Composable
fun TasksAppWidget(widget: TasksAppWidget) {
    val repository: TasksAppRepository = koinInject()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configured by repository.isConfigured.collectAsState(true)
    val tasks by remember { repository.tasks() }.collectAsState(emptyList())
    val now by produceState(nowMin()) { while (true) { delay(30_000); value = nowMin() } }
    // "today" changes with the minute that is read: a widget left on screen past midnight moves on by itself
    val today = remember(now / 60) { LocalDate.now() }
    val d = remember(tasks, today) { tasks.day(today) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val open = { t: TasksAppTask -> repository.openTask(context, t.id); Unit }
    val done = { t: TasksAppTask ->
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        scope.launch { repository.setCompleted(t.id, true) }; Unit
    }

    // The list scrolls inside (at most under half the screen, so clock and music below stay in sight) and rests on now:
    // now is a well – a drag first meets resistance, then breaks free with a thump; each thing passing the now mark
    // ticks; coming back across now catches it again; back on the home screen it is on now again.
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val offset = with(density) { 72.dp.toPx() }          // where the now line rests, from the top
    val breakAt = with(density) { 56.dp.toPx() }
    var nowY by remember { mutableIntStateOf(-1) }
    var curY by remember { mutableIntStateOf(-1) }        // top of the thing running now, -1 when none
    val curKey = d.timed.lastOrNull { val a = it.start(); now in a until maxOf(a + it.durationMin, a + 1) }?.id
    if (curKey == null) curY = -1
    // the thing running now sits at the top, its title in sight; with none, the now line a little below the top
    val anchor = { if (curY >= 0) maxOf(0, curY - with(density) { 4.dp.roundToPx() }) else maxOf(0, (nowY - offset).toInt()) }
    var touched by remember { mutableLongStateOf(0L) }
    val marks = remember { HashMap<String, Int>() }      // top of each thing in the list
    val well = remember {
        object : NestedScrollConnection {
            var free = false
            var pull = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) touched = System.currentTimeMillis()
                if (free || nowY < 0 || source != NestedScrollSource.UserInput) return Offset.Zero
                pull += available.y
                if (abs(pull) > breakAt) { free = true; haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate); return Offset.Zero }
                return Offset(0f, available.y * 0.75f)    // only a quarter of the drag moves it
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                pull = 0f
                touched = System.currentTimeMillis()
                if (free || nowY < 0) return Velocity.Zero
                scroll.animateScrollTo(anchor(), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium))
                return available
            }
        }
    }
    LaunchedEffect(nowY >= 0, curY) { if (nowY >= 0 && touched == 0L) scroll.scrollTo(anchor()) }
    // let go, it springs back to now by itself after a moment: a stiff spring, well under a second
    LaunchedEffect(Unit) {
        while (true) {
            delay(150)
            if (nowY >= 0 && touched > 0 && System.currentTimeMillis() - touched > 1500 && !scroll.isScrollInProgress && abs(scroll.value - anchor()) > 4) {
                well.free = false; well.pull = 0f; touched = 0L
                scroll.animateScrollTo(anchor(), spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    LaunchedEffect(Unit) {
        var last = -1
        var side = 0
        snapshotFlow { scroll.value }.collect { v ->
            val i = marks.values.count { it <= v + offset }
            if (last >= 0 && i != last) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            last = i
            val s = (v - anchor()).sign
            if (well.free && side != 0 && s != side && nowY >= 0) { well.free = false; well.pull = 0f; haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
            side = s
        }
    }

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (nowY >= 0) { well.free = false; well.pull = 0f; touched = 0L; scroll.scrollTo(anchor()) }
            repository.requestSyncIfStale()
        }
    }
    fun Modifier.mark(k: String) = onGloballyPositioned { marks[k] = it.positionInParent().y.toInt() }

    val c = MaterialTheme.colorScheme
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.date_today),
                style = MaterialTheme.typography.titleMedium,
                color = c.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { repository.openApp(context) }) {
                Icon(painter = painterResource(R.drawable.open_in_new_24px), contentDescription = stringResource(R.string.tasksapp))
            }
        }
        Column(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.45f).dp).nestedScroll(well).verticalScroll(scroll).padding(bottom = 12.dp)) {
            when {
                !configured -> Box(Modifier.padding(horizontal = 12.dp)) { Info(stringResource(R.string.tasksapp_not_configured)) }
                d.late.isEmpty() && d.anytime.isEmpty() && d.timed.isEmpty() ->
                    Box(Modifier.padding(horizontal = 12.dp)) { Info(stringResource(R.string.tasksapp_tasks_empty)) }
                else -> {
                    if (d.late.isNotEmpty()) {
                        Header("In ritardo")
                        for (t in d.late) Box(Modifier.mark("l/${t.id}")) { TaskLine(t, open, done, meta = "late") }
                    }
                    if (d.anytime.isNotEmpty()) {
                        Header("In giornata")
                        for (t in d.anytime) Column(Modifier.mark("a/${t.id}")) { TaskLine(t, open, done); for (k in tasks.inside(t.id)) TaskLine(k, open, done, depth = 1) }
                    }
                    if (d.timed.isNotEmpty()) {
                        Header("Programma")
                        var end: Int? = null
                        var nowShown = false
                        for (t in d.timed) {
                            val a = t.start()
                            val b = a + t.durationMin
                            end?.let { e -> if (a - e >= 30) Gap(a - e) }
                            if (!nowShown && now < a) { nowShown = true; NowLine(now) { nowY = it } }
                            Box(Modifier.mark("t/${t.id}").then(if (t.id == curKey) Modifier.onGloballyPositioned { curY = it.positionInParent().y.toInt() } else Modifier)) { Slot(t, a, b, now, tasks, open, done) }
                            end = maxOf(end ?: 0, b)
                        }
                        if (!nowShown) NowLine(now) { nowY = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(s: String) =
    Text(s, Modifier.padding(start = 20.dp, top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

/** ○ title, and under it its list (and how late it is). */
@Composable
private fun TaskLine(t: TasksAppTask, open: (TasksAppTask) -> Unit, done: (TasksAppTask) -> Unit, depth: Int = 0, meta: String = "") {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { open(t) }.padding(start = (12 + 24 * depth).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).clickable { done(t) }, contentAlignment = Alignment.Center) {
            Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, prio(t.priority), CircleShape))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge, color = c.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val list = t.projectName?.takeIf { it.isNotEmpty() && it != "Inbox" }
            if (list != null || meta.isNotEmpty() || t.isRecurring) Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta == "late") t.dueDate?.let {
                    val day = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    Text("%d/%d  ".format(day.dayOfMonth, day.monthValue), style = MaterialTheme.typography.labelMedium, color = c.error)
                }
                if (t.isRecurring) Text("↻  ", style = MaterialTheme.typography.labelMedium, color = c.outline)
                if (list != null) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(t.projectColor?.let { Color(it) } ?: c.outline))
                    Text(" $list", style = MaterialTheme.typography.labelMedium, color = c.outline)
                }
            }
        }
    }
}

/** One timed thing: its start (and end) on the left; a block as a tinted panel with its tasks, ringed while it runs. */
@Composable
private fun Slot(t: TasksAppTask, a: Int, b: Int, now: Int, all: List<TasksAppTask>, open: (TasksAppTask) -> Unit, done: (TasksAppTask) -> Unit) {
    val c = MaterialTheme.colorScheme
    val running = now in a until maxOf(b, a + 1)
    val past = now >= maxOf(b, a + 1)
    Row(Modifier.fillMaxWidth().alpha(if (past) 0.55f else 1f).padding(vertical = 2.dp)) {
        Column(Modifier.width(GUTTER.dp).padding(start = 14.dp, top = 8.dp)) {
            Text(hm(a), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (running) c.primary else c.onSurface)
            if (b > a) Text(hm(minOf(1440, b)), style = MaterialTheme.typography.labelSmall, color = c.outline)
        }
        if (!t.isBlock) Column(
            Modifier.weight(1f).then(if (running) Modifier.padding(end = 12.dp).clip(RoundedCornerShape(14.dp))
                .background(c.primary.copy(alpha = 0.14f)).border(2.dp, c.primary, RoundedCornerShape(14.dp)) else Modifier),
        ) {
            if (running) Text("ORA", Modifier.padding(start = 14.dp, top = 6.dp), style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
            TaskLine(t, open, done)
            for (k in all.inside(t.id)) TaskLine(k, open, done, depth = 1)
        } else {
            val tint = t.projectColor?.let { Color(it) } ?: c.primary
            val bg = tint.copy(alpha = 0.16f).compositeOver(c.surfaceContainer)
            Column(
                Modifier.weight(1f).padding(end = 12.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                    .then(if (running) Modifier.border(2.dp, tint, RoundedCornerShape(14.dp)) else Modifier),
            ) {
                Row(Modifier.fillMaxWidth().clickable { open(t) }.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(tint))
                    Spacer(Modifier.width(10.dp))
                    Text(t.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (running) "in corso" else hours(b - a), style = MaterialTheme.typography.labelMedium, color = if (running) tint else c.outline)
                }
                val inside = all.inside(t.id)
                if (inside.isEmpty()) Text("Vuoto", Modifier.padding(start = 28.dp, bottom = 10.dp), style = MaterialTheme.typography.bodySmall, color = c.outline)
                for (k in inside) {
                    TaskLine(k, open, done)
                    for (g in all.inside(k.id)) TaskLine(g, open, done, depth = 1)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Free time between two things. */
@Composable
private fun Gap(min: Int) = Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Spacer(Modifier.width(GUTTER.dp))
    Text("libero ${hours(min)}", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
}

/** Where now is: the time, a dot and a line. */
@Composable
private fun NowLine(now: Int, at: (Int) -> Unit) = Row(
    Modifier.fillMaxWidth().onGloballyPositioned { at(it.positionInParent().y.toInt()) }.padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val red = MaterialTheme.colorScheme.error
    Text(hm(now), Modifier.width(GUTTER.dp).padding(start = 14.dp), style = MaterialTheme.typography.labelSmall, color = red, fontWeight = FontWeight.SemiBold)
    Box(Modifier.size(8.dp).clip(CircleShape).background(red))
    Box(Modifier.weight(1f).padding(end = 12.dp).height(1.5.dp).background(red))
}
