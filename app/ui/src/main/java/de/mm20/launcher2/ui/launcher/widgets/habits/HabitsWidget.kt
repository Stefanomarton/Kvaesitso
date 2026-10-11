package de.mm20.launcher2.ui.launcher.widgets.habits

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.ticktick.HabitToday
import de.mm20.launcher2.ticktick.TickTickRepository
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.widgets.HabitsWidget
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun HabitsWidget(widget: HabitsWidget) {
    val repository: TickTickRepository = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current
    val configured by repository.isConfigured.collectAsState(true)
    val habits by remember { repository.habitsToday() }.collectAsState(emptyList())

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.requestSyncIfStale()
        }
    }

    val visible = habits.filter {
        (widget.config.showNotDue || it.dueToday) && !(widget.config.hideCompleted && it.doneToday)
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        when {
            !configured -> Message(stringResource(R.string.ticktick_not_configured))
            visible.isEmpty() -> Message(stringResource(R.string.ticktick_habits_empty))
            else -> visible.forEach { HabitRow(it, repository) }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(habit: HabitToday, repository: TickTickRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val color = habit.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    var showValueDialog by remember { mutableStateOf(false) }
    val progressText = if (habit.isBoolean) null else "${habit.valueToday.fmt()}/${habit.goal.fmt()}"

    fun write(block: suspend () -> Result<Unit>) {
        scope.launch {
            if (block().isFailure) {
                Toast.makeText(context, R.string.ticktick_write_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = if (habit.isBoolean) Role.Checkbox else Role.Button,
                onClickLabel = habit.name,
                onLongClick = if (habit.isBoolean) null else ({ showValueDialog = true }),
                onClick = { write { repository.checkIn(habit) } },
            )
            .semantics {
                stateDescription = progressText ?: if (habit.doneToday) "done" else "not done"
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (habit.isBoolean || habit.doneToday) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .then(
                            if (habit.doneToday) Modifier.background(color, CircleShape)
                            else Modifier.border(2.dp, color, CircleShape)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (habit.doneToday) {
                        Icon(
                            painterResource(R.drawable.check_24px),
                            null,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            } else {
                CircularProgressIndicator(
                    progress = { (habit.valueToday / habit.goal).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.size(28.dp),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                habit.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    progressText?.let { "$it ${habit.unit.orEmpty()}".trim() },
                    habit.streak.takeIf { it > 0 }?.let { stringResource(R.string.ticktick_streak, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            habit.lastWeek.forEach { day ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(
                            when {
                                day.done -> Modifier.background(color, CircleShape)
                                day.due -> Modifier.border(1.dp, color, CircleShape)
                                else -> Modifier.background(color.copy(alpha = 0.15f), CircleShape)
                            }
                        )
                )
            }
        }
    }

    if (showValueDialog) {
        var text by remember { mutableStateOf(habit.valueToday.fmt()) }
        AlertDialog(
            onDismissRequest = { showValueDialog = false },
            title = { Text(habit.name) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.ticktick_set_value)) },
                    suffix = habit.unit?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.toDoubleOrNull() != null,
                    onClick = {
                        showValueDialog = false
                        write { repository.setHabitValue(habit.id, text.toDouble(), habit.goal) }
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showValueDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private fun Double.fmt(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
