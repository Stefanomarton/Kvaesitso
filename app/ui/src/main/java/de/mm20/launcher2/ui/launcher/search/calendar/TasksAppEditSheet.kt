package de.mm20.launcher2.ui.launcher.search.calendar

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.calendar.providers.TasksAppCalendarEvent
import de.mm20.launcher2.tasksapp.TaskEdit
import de.mm20.launcher2.tasksapp.TasksAppRepository
import de.mm20.launcher2.tasksapp.localDateIn
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.DismissableBottomSheet
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val priorities = listOf(0 to R.string.tasksapp_priority_none, 1 to R.string.tasksapp_priority_low,
    2 to R.string.tasksapp_priority_medium, 3 to R.string.tasksapp_priority_high)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksAppEditSheet(event: TasksAppCalendarEvent, onDismiss: () -> Unit) {
    val repository: TasksAppRepository = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val projects by repository.projects().collectAsState(emptyList())

    var loaded by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(event.label) }
    var content by remember { mutableStateOf(event.description ?: "") }
    var projectId by remember { mutableStateOf(event.projectId) }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(event.priority) }

    LaunchedEffect(event.id) {
        val task = repository.getTask(event.id) ?: return@LaunchedEffect
        title = task.title
        content = task.content ?: ""
        projectId = task.projectId
        priority = task.priority
        task.dueDate?.let { due ->
            if (task.isAllDay) {
                date = localDateIn(due, task.timeZone)
            } else {
                val dt = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
                date = dt.toLocalDate()
                time = dt.toLocalTime()
            }
        }
        loaded = true
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showProjects by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun close(result: Result<Unit>) {
        if (result.isFailure) {
            Toast.makeText(context, R.string.tasksapp_write_failed, Toast.LENGTH_SHORT).show()
        } else onDismiss()
    }

    DismissableBottomSheet(expanded = true, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.tasksapp_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.tasksapp_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showProjects = true }) {
                    Text(projects.find { it.id == projectId }?.name ?: event.calendarName ?: projectId)
                }
                DropdownMenu(expanded = showProjects, onDismissRequest = { showProjects = false }) {
                    for (p in projects) {
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { projectId = p.id; showProjects = false },
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                InputChip(
                    selected = date != null,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            ?: stringResource(R.string.tasksapp_no_date))
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.event_24px), null) },
                )
                if (date != null) {
                    InputChip(
                        selected = time != null,
                        onClick = { showTimePicker = true },
                        label = {
                            Text(time?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                                ?: stringResource(R.string.tasksapp_all_day))
                        },
                    )
                }
                if (date != null) {
                    TextButton(onClick = { date = null; time = null }) {
                        Text(stringResource(R.string.tasksapp_clear_date))
                    }
                }
            }

            Text(stringResource(R.string.tasksapp_priority), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                priorities.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = priority == value,
                        onClick = { priority = value },
                        shape = SegmentedButtonDefaults.itemShape(i, priorities.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { confirmDelete = true }) {
                    Icon(painterResource(R.drawable.delete_24px), stringResource(R.string.tasksapp_delete))
                }
                OutlinedButton(onClick = { event.launch(context, null); onDismiss() }) {
                    Icon(painterResource(R.drawable.open_in_new_24px), stringResource(R.string.menu_calendar_open_externally))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = loaded && title.isNotBlank(),
                    onClick = {
                        val zone = ZoneId.systemDefault()
                        val due = date?.let { d ->
                            (time?.let { d.atTime(it) } ?: d.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
                        }
                        scope.launch {
                            close(
                                repository.updateTask(
                                    event.id,
                                    TaskEdit(
                                        title = title.trim(),
                                        content = content.takeIf { it.isNotBlank() },
                                        projectId = projectId,
                                        dueDate = due,
                                        isAllDay = time == null,
                                        priority = priority,
                                    )
                                )
                            )
                        }
                    },
                ) { Text(stringResource(R.string.tasksapp_save)) }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        ) { DatePicker(state) }
    }

    if (showTimePicker) {
        val initial = time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    showTimePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { time = null; showTimePicker = false }) {
                    Text(stringResource(R.string.tasksapp_all_day))
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.tasksapp_delete_confirm, title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { close(repository.deleteTask(event.id)) }
                }) { Text(stringResource(R.string.tasksapp_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
