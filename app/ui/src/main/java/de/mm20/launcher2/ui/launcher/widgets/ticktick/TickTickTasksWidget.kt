package de.mm20.launcher2.ui.launcher.widgets.ticktick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.mm20.launcher2.calendar.providers.toCalendarEvent
import de.mm20.launcher2.ticktick.TickTickRepository
import de.mm20.launcher2.ticktick.dueOn
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.launcher.search.common.list.SearchResultList
import de.mm20.launcher2.ui.launcher.widgets.calendar.Info
import de.mm20.launcher2.widgets.TickTickTasksWidget
import org.koin.compose.koinInject
import java.time.LocalDate

@Composable
fun TickTickTasksWidget(widget: TickTickTasksWidget) {
    val repository: TickTickRepository = koinInject()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configured by repository.isConfigured.collectAsState(true)
    val tasks by remember { repository.tasks() }.collectAsState(emptyList())
    // ponytail: "today" is read on recomposition; a widget left on screen past midnight updates on the next sync
    val events = remember(tasks) { tasks.dueOn(LocalDate.now()).mapNotNull { it.toCalendarEvent() } }

    LaunchedEffect(null) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.requestSyncIfStale()
        }
    }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.date_today),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                repository.openApp(context)
            }) {
                Icon(
                    painter = painterResource(R.drawable.open_in_new_24px),
                    contentDescription = stringResource(R.string.ticktick),
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp)
        ) {
            when {
                !configured -> Info(stringResource(R.string.ticktick_not_configured))
                events.isEmpty() -> Info(stringResource(R.string.ticktick_tasks_empty))
                else -> SearchResultList(events, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
