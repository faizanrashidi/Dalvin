package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.CommandHistoryItem
import com.example.core.NovaLanguage
import com.example.core.UiState
import com.example.ui.NovaViewModel
import com.example.voice.VoiceRecognitionState

@Composable
fun NovaHubScreen(
    viewModel: NovaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val rmsDb by viewModel.rmsDb.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val outputState by viewModel.commandOutputState.collectAsStateWithLifecycle()
    val historyState by viewModel.historyState.collectAsStateWithLifecycle()
    val pendingConfirm by viewModel.pendingConfirmation.collectAsStateWithLifecycle()

    var commandText by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            viewModel.showBanner("Microphone permission is needed for voice input. You can still type commands.")
        }
    }

    fun onMicToggle() {
        if (voiceState is VoiceRecognitionState.Listening || voiceState is VoiceRecognitionState.Partial) {
            viewModel.stopVoiceListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    pendingConfirm?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { Text(req.title) },
            text = { Text(req.description) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    modifier = Modifier.testTag("confirm_sensitive_action_button")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingAction() }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nova_banner_1794852520758),
                    contentDescription = "NOVA holographic banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NOVA VOICE AI", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Profile: ${settings.profile.displayName} · 155 Features Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isSpeaking) {
                            OutlinedButton(
                                onClick = { viewModel.speechEngine.stop() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = "Stop speaking", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Mute TTS")
                            }
                        }
                    }

                    NovaVoiceOrb(
                        isListening = voiceState is VoiceRecognitionState.Listening || voiceState is VoiceRecognitionState.Partial,
                        isSpeaking = isSpeaking,
                        rmsDb = rmsDb,
                        micKilled = settings.micKillSwitch,
                        onClick = { onMicToggle() }
                    )

                    val statusLine = when (val st = voiceState) {
                        is VoiceRecognitionState.Listening -> "Sun raha hoon... Speak Hindi, Hinglish or English"
                        is VoiceRecognitionState.Partial -> "Hearing: \"${st.text}\""
                        is VoiceRecognitionState.Error -> st.message
                        VoiceRecognitionState.Idle -> if (settings.micKillSwitch) "Mic Kill-Switch Active" else "Tap Orb to Speak"
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (voiceState is VoiceRecognitionState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Language selector
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NovaLanguage.entries.forEach { lang ->
                    FilterChip(
                        selected = settings.language == lang,
                        onClick = { viewModel.updateLanguage(lang) },
                        label = { Text(lang.label) },
                        modifier = Modifier.testTag("lang_chip_${lang.code}")
                    )
                }
            }
        }

        // Command input bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_input_field"),
                    label = { Text("Command (Hindi / Hinglish / English)") },
                    placeholder = { Text("e.g. YouTube kholo, battery status, 250 ka 18%") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                IconButton(
                    onClick = {
                        if (commandText.isNotBlank()) {
                            viewModel.executeCommand(commandText)
                            commandText = ""
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Execute command",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Quick Voice Command Chips
        item {
            val samples = listOf(
                "Battery status batao",
                "Flashlight on karo",
                "YouTube kholo",
                "Weather in Delhi",
                "250 ka 18%",
                "Daily briefing",
                "System check"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                samples.forEach { sample ->
                    SuggestionChip(
                        onClick = { viewModel.executeCommand(sample) },
                        label = { Text(sample) }
                    )
                }
            }
        }

        // Active Response Card
        item {
            when (val st = outputState) {
                UiState.Idle -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                "Ask NOVA anything or speak a phone command like 'WhatsApp kholo', 'Photo click karo', or 'Alarm lagao 7 am'.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                UiState.Loading -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Processing with NOVA Engine...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is UiState.Streaming -> {
                    ResponseCard(
                        intentLabel = "Gemini AI (Streaming)",
                        utterance = "Thinking...",
                        response = st.partial,
                        onSpeak = null
                    )
                }
                is UiState.Success -> {
                    ResponseCard(
                        intentLabel = st.data.intentLabel,
                        utterance = st.data.utterance,
                        response = st.data.response,
                        onSpeak = {
                            viewModel.speechEngine.speak(
                                st.data.response,
                                st.data.language,
                                settings.ttsRate,
                                settings.ttsPitch
                            )
                        }
                    )
                }
                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = st.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Command History Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Recent Command History", style = MaterialTheme.typography.titleMedium)
            }
        }

        when (val hist = historyState) {
            UiState.Loading -> item {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            is UiState.Error -> item {
                Text(hist.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            is UiState.Success -> {
                if (hist.data.isEmpty()) {
                    item {
                        Text(
                            "No commands recorded yet. Tap the orb or a quick chip above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(hist.data.take(12), key = { it.id }) { item ->
                        HistoryRowCard(item = item, onReplay = { viewModel.executeCommand(item.utterance) })
                    }
                }
            }
            UiState.Idle, is UiState.Streaming -> Unit
        }
    }
}

@Composable
private fun NovaVoiceOrb(
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsDb: Float,
    micKilled: Boolean,
    onClick: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening || isSpeaking) 1.18f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .size(116.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("nova_mic_orb_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 2.6f
            val dynamicRadius = baseRadius * pulse + (rmsDb * 2.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(primary.copy(alpha = 0.45f), secondary.copy(alpha = 0.1f))
                ),
                radius = dynamicRadius.coerceAtMost(size.minDimension / 2f)
            )
            drawCircle(
                brush = Brush.sweepGradient(listOf(primary, secondary, primary)),
                radius = baseRadius + 6f,
                style = Stroke(width = 4.dp.toPx())
            )
        }
        Surface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            color = if (micKilled) MaterialTheme.colorScheme.error else primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (micKilled) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isListening) "Stop listening" else "Start voice assistant",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponseCard(
    intentLabel: String,
    utterance: String,
    response: String,
    onSpeak: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = intentLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = "Speak response aloud")
                    }
                }
            }
            Text(
                text = utterance,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = response,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun HistoryRowCard(item: CommandHistoryItem, onReplay: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onReplay),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.utterance, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(item.intent, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(item.response, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
    }
}
