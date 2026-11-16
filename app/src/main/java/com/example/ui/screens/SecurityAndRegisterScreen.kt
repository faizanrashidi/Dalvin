package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.FeatureRegister
import com.example.core.NovaProfile
import com.example.core.UiState
import com.example.services.NovaWakeService
import com.example.ui.NovaViewModel
import com.example.ui.auth.signOutUser
import com.example.ui.theme.NovaThemeMode

@Composable
fun SecurityAndRegisterScreen(
    viewModel: NovaViewModel,
    userEmail: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val runtimePerms by viewModel.runtimePermissions.collectAsStateWithLifecycle()
    val specialPerms by viewModel.specialPermissions.collectAsStateWithLifecycle()
    val appAudit by viewModel.appAudit.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val wakeRunning by NovaWakeService.isRunning.collectAsStateWithLifecycle()

    var selectedCategory by remember { mutableStateOf("ALL") }
    var featureSearch by remember { mutableStateOf("") }
    var vaultUnlocked by remember { mutableStateOf(!settings.biometricLockEnabled) }
    var confirmWipeDialog by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermissions()
    }

    if (confirmWipeDialog) {
        AlertDialog(
            onDismissRequest = { confirmWipeDialog = false },
            title = { Text("Wipe Cloud History & Memory?") },
            text = { Text("This deletes all your NOVA command history and AI memory items from Firestore.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.wipeHistoryAndMemories()
                        confirmWipeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe All") }
            },
            dismissButton = { TextButton(onClick = { confirmWipeDialog = false }) { Text("Cancel") } }
        )
    }

    val filteredFeatures = remember(selectedCategory, featureSearch) {
        FeatureRegister.allFeatures.filter { entry ->
            val catMatch = selectedCategory == "ALL" || entry.categoryCode == selectedCategory
            val qMatch = featureSearch.isBlank() ||
                entry.name.contains(featureSearch, ignoreCase = true) ||
                entry.id.contains(featureSearch, ignoreCase = true) ||
                entry.description.contains(featureSearch, ignoreCase = true)
            catMatch && qMatch
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Account, Theme, Profile & Voice Settings
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Account & Voice Preferences", style = MaterialTheme.typography.titleMedium)
                            Text("Signed in: $userEmail", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = { signOutUser(credentialManager, scope) },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out", modifier = Modifier.size(16.dp))
                            Text(" Sign Out", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.updateTheme(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }

                    Text("Assistant Profile", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NovaProfile.entries.forEach { prof ->
                            FilterChip(
                                selected = settings.profile == prof,
                                onClick = { viewModel.updateProfile(prof) },
                                label = { Text(prof.displayName) }
                            )
                        }
                    }

                    Text("TTS Speed: ${"%.2f".format(settings.ttsRate)}x · Pitch: ${"%.2f".format(settings.ttsPitch)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(value = settings.ttsRate, onValueChange = { viewModel.updateTtsRate(it) }, valueRange = 0.6f..1.8f)
                    Slider(value = settings.ttsPitch, onValueChange = { viewModel.updateTtsPitch(it) }, valueRange = 0.6f..1.6f)

                    SwitchRow("Auto-Speak Replies", settings.autoSpeak) { viewModel.updateAutoSpeak(it) }
                    SwitchRow("Continuous Conversation Loop", settings.continuousListening) { viewModel.updateContinuousListening(it) }
                    SwitchRow("Wake Service Notification", wakeRunning) { enable ->
                        if (enable) NovaWakeService.start(context) else NovaWakeService.stop(context)
                    }
                }
            }
        }

        // Privacy Controls & Data Handling Notice
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Privacy, Data Notice & Kill-Switch", style = MaterialTheme.typography.titleMedium)
                    }
                    SwitchRow("Mic Privacy Kill-Switch", settings.micKillSwitch) { viewModel.updateMicKillSwitch(it) }
                    SwitchRow("Persistent AI Memory", settings.memoryEnabled) { viewModel.updateMemoryEnabled(it) }
                    SwitchRow("Biometric Vault Lock", settings.biometricLockEnabled) {
                        viewModel.updateBiometricLock(it)
                        vaultUnlocked = !it
                    }
                    Text(
                        "Data Disclosure: Voice audio uses Android SpeechRecognizer. AI queries are sent via Firebase App Check to Gemini AI Logic. Notes, tasks, routines & audit logs sync strictly under your Google account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { confirmWipeDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("wipe_history_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" One-Tap Wipe History & AI Memory", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }

        // Runtime & Special Permission Dashboard + App Audit
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Permission Dashboard", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = {
                            viewModel.refreshPermissions()
                            viewModel.runAppPermissionAudit()
                        }) { Text("Audit Apps") }
                    }
                    runtimePerms.forEach { p ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.label, style = MaterialTheme.typography.titleSmall)
                                Text(p.rationale, style = MaterialTheme.typography.bodySmall)
                            }
                            if (p.granted) {
                                Text("Granted", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                            } else {
                                OutlinedButton(onClick = { permLauncher.launch(p.permission) }) { Text("Grant") }
                            }
                        }
                    }
                    specialPerms.forEach { sp ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sp.label, style = MaterialTheme.typography.titleSmall)
                                Text(sp.rationale, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                if (sp.granted) "Enabled" else "Off",
                                color = if (sp.granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    OutlinedButton(onClick = { openAppInfoSettings(context) }) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Open Android App Permissions", modifier = Modifier.padding(start = 6.dp))
                    }
                    if (appAudit.isNotEmpty()) {
                        Text("Installed Apps Sensitive Permissions:", style = MaterialTheme.typography.labelLarge)
                        appAudit.take(8).forEach { item ->
                            Text(
                                "• ${item.appName}: ${if (item.dangerousGranted.isEmpty()) "None granted" else item.dangerousGranted.joinToString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // Biometric Vault & Security Audit Trail
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Security Audit Vault", style = MaterialTheme.typography.titleMedium)
                    }
                    if (settings.biometricLockEnabled && !vaultUnlocked) {
                        Button(onClick = {
                            val act = context as? FragmentActivity
                            if (act != null) {
                                viewModel.securityManager.authenticateVault(
                                    act,
                                    onSuccess = { vaultUnlocked = true },
                                    onError = { viewModel.showBanner(it) }
                                )
                            } else {
                                vaultUnlocked = true
                            }
                        }) { Text("Unlock Security Audit Log") }
                    } else {
                        val logs = (auditLogsState as? UiState.Success)?.data.orEmpty()
                        if (logs.isEmpty()) {
                            Text("No sensitive action audits logged yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            logs.take(10).forEach { log ->
                                Text(
                                    "${if (log.approved) "✔" else "✖"} [${log.action}] ${log.detail}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // 155-Feature Master Register
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "155-Feature Register (${FeatureRegister.allFeatures.size}/155)",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedTextField(
                        value = featureSearch,
                        onValueChange = { featureSearch = it },
                        label = { Text("Search feature ID, name or description") },
                        modifier = Modifier.fillMaxWidth().testTag("feature_register_search"),
                        singleLine = true
                    )
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedCategory == "ALL", onClick = { selectedCategory = "ALL" }, label = { Text("ALL (155)") })
                        FeatureRegister.categories.forEach { (code, name) ->
                            FilterChip(selected = selectedCategory == code, onClick = { selectedCategory = code }, label = { Text("$code: $name") })
                        }
                    }
                }
            }
        }

        items(filteredFeatures, key = { it.id }) { feat ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${feat.id} · ${feat.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                            Text(
                                "Phase ${feat.phase} · ${feat.status.label}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(feat.description, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Module: ${feat.moduleClass} · Perms: ${feat.permissions} · ${feat.connectivity} · ${feat.android16Note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun openAppInfoSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
