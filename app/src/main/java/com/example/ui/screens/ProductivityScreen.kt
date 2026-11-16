package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.ui.NovaViewModel

@Composable
fun ProductivityScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val calendarEvents by viewModel.calendarEvents.collectAsStateWithLifecycle()
    val countdown by viewModel.countdownSeconds.collectAsStateWithLifecycle()
    val stopwatchMs by viewModel.stopwatchMillis.collectAsStateWithLifecycle()
    val stopwatchRunning by viewModel.stopwatchRunning.collectAsStateWithLifecycle()

    var noteTitle by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var taskTitle by remember { mutableStateOf("") }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderMins by remember { mutableStateOf("5") }
    var alarmHour by remember { mutableStateOf("7") }
    var alarmMin by remember { mutableStateOf("30") }
    var calcExpr by remember { mutableStateOf("250 ka 18%") }
    var calcResult by remember { mutableStateOf("") }

    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refreshCalendar() else viewModel.showBanner("READ_CALENDAR permission denied.")
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Daily Briefing + Calculator
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.WbSunny, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Daily Briefing & Voice Calculator", style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = { viewModel.executeCommand("daily briefing") },
                            modifier = Modifier.testTag("daily_briefing_button")
                        ) { Text("Brief Me") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = calcExpr,
                            onValueChange = { calcExpr = it },
                            label = { Text("Math / % expression") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val res = viewModel.intentDetector.evaluateMath(calcExpr)
                            calcResult = res?.let { "= $it" } ?: "Try e.g. 250 ka 18% or 45 * 12"
                        }) {
                            Icon(Icons.Filled.Calculate, contentDescription = "Evaluate math", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (calcResult.isNotBlank()) {
                        Text(calcResult, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Alarms, Countdown Timer, Stopwatch & WorkManager Reminders
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Alarms, Timers, Stopwatch & Reminders", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = alarmHour,
                            onValueChange = { alarmHour = it.filter(Char::isDigit).take(2) },
                            label = { Text("Hr") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = alarmMin,
                            onValueChange = { alarmMin = it.filter(Char::isDigit).take(2) },
                            label = { Text("Min") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val h = alarmHour.toIntOrNull() ?: 7
                            val m = alarmMin.toIntOrNull() ?: 0
                            viewModel.showBanner(viewModel.reminderManager.setAlarm(h, m, "NOVA Alarm"))
                        }) { Text("Set Alarm") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Countdown: ${countdown}s", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { viewModel.startCountdown(60) }) { Text("1m") }
                            OutlinedButton(onClick = { viewModel.startCountdown(300) }) { Text("5m") }
                            if (countdown > 0) OutlinedButton(onClick = { viewModel.stopCountdown() }) { Text("Stop") }
                        }
                    }

                    val sec = (stopwatchMs / 1000L)
                    val tenths = ((stopwatchMs % 1000L) / 100L)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Stopwatch: ${sec}.${tenths}s", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(onClick = { viewModel.toggleStopwatch() }) { Text(if (stopwatchRunning) "Pause" else "Start") }
                            OutlinedButton(onClick = { viewModel.resetStopwatch() }) { Text("Reset") }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = reminderTitle,
                            onValueChange = { reminderTitle = it },
                            label = { Text("Reminder title") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = reminderMins,
                            onValueChange = { reminderMins = it.filter(Char::isDigit).take(3) },
                            label = { Text("Mins") },
                            modifier = Modifier.weight(0.45f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val mins = reminderMins.toLongOrNull() ?: 5L
                            val msg = viewModel.reminderManager.scheduleWorkReminder(
                                reminderTitle.ifBlank { "NOVA Reminder" },
                                mins
                            )
                            viewModel.showBanner(msg)
                            reminderTitle = ""
                        }) { Text("Remind") }
                    }
                }
            }
        }

        // Calendar
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Calendar Schedule", style = MaterialTheme.typography.titleMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { viewModel.showBanner(viewModel.reminderManager.openCalendarInsert("NOVA Event")) }) { Text("New Event") }
                            Button(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                    viewModel.refreshCalendar()
                                } else {
                                    calendarPermLauncher.launch(Manifest.permission.READ_CALENDAR)
                                }
                            }) { Text("Load") }
                        }
                    }
                    if (calendarEvents.isEmpty()) {
                        Text("Tap Load to read upcoming 7-day device events.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        calendarEvents.forEach { ev ->
                            Text("• ${ev.title} (${ev.startTimeFormatted})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Notes Section
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.EventNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Cloud-Synced Notes", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("Note title") },
                        modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("Note content") },
                        modifier = Modifier.fillMaxWidth().testTag("note_content_input")
                    )
                    Button(
                        onClick = {
                            viewModel.createNote(noteTitle, noteContent)
                            noteTitle = ""
                            noteContent = ""
                        },
                        modifier = Modifier.fillMaxWidth().testTag("save_note_button")
                    ) { Text("Save Note") }
                }
            }
        }

        when (val st = notesState) {
            UiState.Loading -> item { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
            is UiState.Error -> item { Text(st.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> items(st.data, key = { it.id }) { note ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleSmall)
                            Text(note.content, style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            IconButton(onClick = { viewModel.toggleNotePin(note) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(
                                    Icons.Filled.PushPin,
                                    contentDescription = "Pin note",
                                    tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteNote(note.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete note", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            else -> Unit
        }

        // Tasks Section
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tasks & Todo Manager", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = taskTitle,
                            onValueChange = { taskTitle = it },
                            label = { Text("New task") },
                            modifier = Modifier.weight(1f).testTag("task_title_input"),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.createTask(taskTitle)
                                taskTitle = ""
                            },
                            modifier = Modifier.testTag("add_task_button")
                        ) { Text("Add") }
                    }
                }
            }
        }

        when (val st = tasksState) {
            UiState.Loading -> item { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
            is UiState.Error -> item { Text(st.message, color = MaterialTheme.colorScheme.error) }
            is UiState.Success -> items(st.data, key = { it.id }) { task ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(checked = task.completed, onCheckedChange = { viewModel.toggleTask(task) })
                            Icon(
                                if (task.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(task.title, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                        IconButton(onClick = { viewModel.deleteTask(task.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}
