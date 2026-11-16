package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.GlobalNavAction
import com.example.core.SystemPanelType
import com.example.core.UiState
import com.example.services.AccessibilityAutomationManager
import com.example.services.NovaNotificationListenerService
import com.example.ui.NovaViewModel

@Composable
fun AutomationAndAiScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val a11yConnected by AccessibilityAutomationManager.isConnected.collectAsStateWithLifecycle()
    val foregroundPkg by AccessibilityAutomationManager.foregroundPackage.collectAsStateWithLifecycle()
    val visibleNodes by AccessibilityAutomationManager.visibleNodes.collectAsStateWithLifecycle()
    val notifConnected by NovaNotificationListenerService.isListenerConnected.collectAsStateWithLifecycle()
    val recentNotifs by NovaNotificationListenerService.recentNotifications.collectAsStateWithLifecycle()
    val routinesState by viewModel.routinesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val workflowPlanState by viewModel.workflowPlan.collectAsStateWithLifecycle()
    val pythonOutput by viewModel.pythonOutput.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    var tapText by remember { mutableStateOf("") }
    var routineName by remember { mutableStateOf("") }
    var routineSteps by remember { mutableStateOf("battery status, weather in Delhi") }
    var goalInput by remember { mutableStateOf("") }
    var memoryKey by remember { mutableStateOf("") }
    var memoryValue by remember { mutableStateOf("") }
    var customApiKeyInput by remember { mutableStateOf("") }
    var pythonScript by remember { mutableStateOf("x = 21 * 2\nprint(x)") }
    var pythonEndpoint by remember(settings.pythonBridgeEndpoint) { mutableStateOf(settings.pythonBridgeEndpoint) }
    var replyText by remember { mutableStateOf("On my way!") }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Accessibility Automation & Emergency Stop
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AccessibilityNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Accessibility Automation (Opt-In)", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = if (a11yConnected) "ACTIVE" else "INACTIVE",
                            color = if (a11yConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Text(
                        "Foreground App: ${foregroundPkg.ifBlank { "Waiting for Accessibility Service..." }} (${visibleNodes.size} nodes inspected)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.showBanner(viewModel.phoneControlManager.openSystemPanel(SystemPanelType.ACCESSIBILITY)) },
                            modifier = Modifier.weight(1f).testTag("open_accessibility_settings_button")
                        ) { Text("Accessibility Settings") }
                        Button(
                            onClick = { viewModel.showBanner(AccessibilityAutomationManager.emergencyStop()) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("emergency_stop_button")
                        ) {
                            Icon(Icons.Filled.StopCircle, contentDescription = "Emergency stop", modifier = Modifier.size(16.dp))
                            Text(" Emergency Stop", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.showBanner(AccessibilityAutomationManager.performGlobal(GlobalNavAction.BACK)) }, modifier = Modifier.weight(1f)) { Text("Back") }
                        OutlinedButton(onClick = { viewModel.showBanner(AccessibilityAutomationManager.performGlobal(GlobalNavAction.HOME)) }, modifier = Modifier.weight(1f)) { Text("Home") }
                        OutlinedButton(onClick = { viewModel.showBanner(AccessibilityAutomationManager.scrollScreen(true)) }, modifier = Modifier.weight(1f)) { Text("Scroll Down") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = tapText,
                            onValueChange = { tapText = it },
                            label = { Text("Tap screen label") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.showBanner(AccessibilityAutomationManager.tapElementByText(tapText)) }) { Text("Tap") }
                    }
                }
            }
        }

        // Notification Listener & Quick Reply
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Notification Reader & Quick Reply", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = { viewModel.showBanner(viewModel.phoneControlManager.openSystemPanel(SystemPanelType.NOTIFICATIONS)) }) {
                            Text(if (notifConnected) "Connected" else "Enable")
                        }
                    }
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Quick reply text") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (recentNotifs.isEmpty()) {
                        Text("No notifications captured yet.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        recentNotifs.take(4).forEach { n ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(n.title, style = MaterialTheme.typography.titleSmall)
                                    Text(n.text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                                if (n.hasQuickReply) {
                                    Button(onClick = {
                                        viewModel.showBanner(NovaNotificationListenerService.sendQuickReply(n.key, replyText))
                                    }) { Text("Reply") }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini Agentic Workflow Planner
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Gemini Agentic Workflow Planner", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { goalInput = it },
                            label = { Text("Describe goal (e.g. Morning routine)") },
                            modifier = Modifier.weight(1f).testTag("workflow_goal_input"),
                            singleLine = true
                        )
                        Button(onClick = { viewModel.planAiWorkflow(goalInput) }) { Text("Plan") }
                    }
                    when (val st = workflowPlanState) {
                        UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        is UiState.Success -> {
                            Text(st.data.summary, style = MaterialTheme.typography.bodyMedium)
                            st.data.steps.forEachIndexed { idx, s -> Text("${idx + 1}. $s", style = MaterialTheme.typography.bodySmall) }
                            Button(onClick = { viewModel.executeCommand(st.data.steps.joinToString(" then ")) }) {
                                Text("Execute Plan")
                            }
                        }
                        is UiState.Error -> Text(st.message, color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }
        }

        // Automation Routines
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("User-Approved Multi-Step Routines", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = routineName, onValueChange = { routineName = it }, label = { Text("Routine name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = routineSteps, onValueChange = { routineSteps = it }, label = { Text("Steps (comma separated)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        viewModel.addRoutine(routineName.ifBlank { "Morning Flow" }, routineName, routineSteps)
                        routineName = ""
                    }, modifier = Modifier.fillMaxWidth().testTag("save_routine_button")) { Text("Save Routine") }
                }
            }
        }

        when (val st = routinesState) {
            is UiState.Success -> items(st.data, key = { it.id }) { routine ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleSmall)
                            Text(routine.steps.joinToString(" -> "), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = routine.enabled, onCheckedChange = { viewModel.toggleRoutine(routine) })
                            IconButton(onClick = { viewModel.runRoutine(routine) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Run ${routine.name}")
                            }
                            IconButton(onClick = { viewModel.deleteRoutine(routine.id) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${routine.name}", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            else -> Unit
        }

        // AI Memory + Custom Key Config + Python Bridge + Diagnostics
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("AI Memory & Provider Config", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = memoryKey, onValueChange = { memoryKey = it }, label = { Text("Fact key") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = memoryValue, onValueChange = { memoryValue = it }, label = { Text("Fact value") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Button(onClick = {
                        viewModel.addMemory(memoryKey, memoryValue)
                        memoryKey = ""
                        memoryValue = ""
                    }) { Text("Save AI Memory") }

                    (memoriesState as? UiState.Success)?.data?.forEach { mem ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("• ${mem.key}: ${mem.value}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { viewModel.deleteMemory(mem.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete memory", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Text("Gemini AI Logic Active (App Check). Optional Encrypted Custom Key:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customApiKeyInput,
                            onValueChange = { customApiKeyInput = it },
                            label = { Text(if (settings.hasCustomApiKey) settings.customApiKeyPreview else "Custom API key (Keystore AES-GCM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            viewModel.saveCustomApiKey(customApiKeyInput)
                            customApiKeyInput = ""
                        }) { Text("Encrypt") }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Python Bridge & Skill Plugins", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Registered Skills: ${viewModel.skillRegistry.getPlugins().joinToString { it.name }}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = pythonEndpoint,
                        onValueChange = { pythonEndpoint = it },
                        label = { Text("Local bridge URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pythonScript,
                        onValueChange = { pythonScript = it },
                        label = { Text("Sandboxed Python-syntax script") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.runPythonScript(pythonScript, false) }, modifier = Modifier.weight(1f)) { Text("Run Local") }
                        OutlinedButton(onClick = {
                            viewModel.updatePythonEndpoint(pythonEndpoint)
                            viewModel.runPythonScript(pythonScript, true)
                        }, modifier = Modifier.weight(1f)) { Text("Send Bridge") }
                    }
                    if (pythonOutput.isNotBlank()) Text("Output: $pythonOutput", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("System Diagnostics", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = { viewModel.runDiagnostics() }) { Text("Re-run") }
                    }
                    diagnostics.forEach { d ->
                        Text("${if (d.passed) "✅" else "⚠️"} ${d.component}: ${d.detail}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
