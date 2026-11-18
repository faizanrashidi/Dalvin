
package com.example.voice

import android.content.Context
import com.example.ai.AIEngine
import com.example.core.CommandExecutionResult
import com.example.core.ConfirmationRequest
import com.example.core.NovaIntent
import com.example.core.NovaLanguage
import com.example.core.NovaMemoryItem
import com.example.data.NovaRepository
import com.example.data.NovaSettingsState
import com.example.managers.AppLaunchOutcome
import com.example.managers.AppLauncher
import com.example.managers.CameraManager
import com.example.managers.ContactManager
import com.example.managers.DeviceInfoManager
import com.example.managers.DiagnosticsEngine
import com.example.managers.FileManager
import com.example.managers.InternetManager
import com.example.managers.MediaManager
import com.example.managers.PhoneControlManager
import com.example.managers.ReminderManager
import com.example.managers.SMSManager
import com.example.managers.SkillPluginRegistry
import com.example.managers.VolumeController
import com.example.services.AccessibilityAutomationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import java.util.UUID

class CommandProcessor(
    private val context: Context,
    private val intentDetector: IntentDetector,
    private val aiEngine: AIEngine,
    private val repository: NovaRepository,
    private val appLauncher: AppLauncher,
    private val phoneControlManager: PhoneControlManager,
    private val volumeController: VolumeController,
    private val deviceInfoManager: DeviceInfoManager,
    private val contactManager: ContactManager,
    private val smsManager: SMSManager,
    private val cameraManager: CameraManager,
    private val internetManager: InternetManager,
    private val fileManager: FileManager,
    private val mediaManager: MediaManager,
    private val reminderManager: ReminderManager,
    private val skillRegistry: SkillPluginRegistry,
    private val diagnosticsEngine: DiagnosticsEngine
) {

    fun processCommand(
        rawUtterance: String,
        userId: String,
        settings: NovaSettingsState,
        memories: List<NovaMemoryItem>,
        ttsReady: Boolean,
        onNavigateToCamera: (Boolean, Boolean) -> Unit,
        onNavigateToGalleryPicker: () -> Unit,
        onRequestConfirmation: (ConfirmationRequest) -> Unit,
        onStreamAiUpdate: (String) -> Unit
    ): Flow<CommandExecutionResult> = flow {

        val utterance = rawUtterance.trim()

        if (utterance.isBlank()) {
            emit(
                CommandExecutionResult(
                    utterance = utterance,
                    response = "Please say or type a command.",
                    intentLabel = "EmptyCommand",
                    language = settings.language,
                    success = false
                )
            )
            return@flow
        }

        val detectedLang = if (settings.language == NovaLanguage.ENGLISH) {
            intentDetector.detectLanguage(utterance)
        } else {
            settings.language
        }

        val steps = splitChainedSteps(utterance)

        if (steps.size > 1) {
            val stepReplies = mutableListOf<String>()

            for (step in steps) {
                val result = executeSingleStep(
                    step = step,
                    userId = userId,
                    lang = detectedLang,
                    settings = settings,
                    memories = memories,
                    ttsReady = ttsReady,
                    onNavigateToCamera = onNavigateToCamera,
                    onNavigateToGalleryPicker = onNavigateToGalleryPicker,
                    onRequestConfirmation = onRequestConfirmation,
                    onStreamAiUpdate = onStreamAiUpdate
                )

                stepReplies.add("• $step → ${result.response}")
            }

            emit(
                CommandExecutionResult(
                    utterance = utterance,
                    response = stepReplies.joinToString("\n"),
                    intentLabel = "MultiStep(${steps.size})",
                    language = detectedLang,
                    success = true
                )
            )
            return@flow
        }

        emit(
            executeSingleStep(
                step = utterance,
                userId = userId,
                lang = detectedLang,
                settings = settings,
                memories = memories,
                ttsReady = ttsReady,
                onNavigateToCamera = onNavigateToCamera,
                onNavigateToGalleryPicker = onNavigateToGalleryPicker,
                onRequestConfirmation = onRequestConfirmation,
                onStreamAiUpdate = onStreamAiUpdate
            )
        )
    }

    private suspend fun executeSingleStep(
        step: String,
        userId: String,
        lang: NovaLanguage,
        settings: NovaSettingsState,
        memories: List<NovaMemoryItem>,
        ttsReady: Boolean,
        onNavigateToCamera: (Boolean, Boolean) -> Unit,
        onNavigateToGalleryPicker: () -> Unit,
        onRequestConfirmation: (ConfirmationRequest) -> Unit,
        onStreamAiUpdate: (String) -> Unit
    ): CommandExecutionResult {

        skillRegistry.tryHandle(step)?.let { pluginReply ->
            return CommandExecutionResult(
                step,
                pluginReply,
                "SkillPlugin",
                lang,
                true
            )
        }

        val intent = intentDetector.detect(step)

        return when (intent) {

            is NovaIntent.OpenApp -> {
                when (val outcome = appLauncher.launchApp(intent.appName, intent.inAppQuery)) {

                    is AppLaunchOutcome.Launched -> {
                        CommandExecutionResult(
                            step,
                            outcome.message,
                            "OpenApp(${outcome.appLabel})",
                            lang,
                            true
                        )
                    }

                    is AppLaunchOutcome.NotInstalled -> {
                        val promptText = when (lang) {
                            NovaLanguage.HINDI ->
                                "${outcome.requestedName} फोन में इंस्टॉल नहीं है। क्या मैं इसे Play Store में खोजकर खोल दूँ?"

                            NovaLanguage.HINGLISH ->
                                "${outcome.requestedName} aapke phone me install nahi hai. Kya main Play Store par search karke install page khol doon?"

                            NovaLanguage.ENGLISH ->
                                "${outcome.requestedName} is not installed. Should I open Play Store to search and install it?"
                        }

                        val confirm = ConfirmationRequest(
                            id = UUID.randomUUID().toString(),
                            title = "Install ${outcome.requestedName}?",
                            description = promptText,
                            actionType = "PLAY_STORE_INSTALL",
                            onConfirm = {
                                appLauncher.openPlayStoreSearch(outcome.requestedName)
                            }
                        )

                        onRequestConfirmation(confirm)

                        CommandExecutionResult(
                            step,
                            promptText,
                            "AppNotInstalled",
                            lang,
                            true,
                            pendingConfirmation = confirm,
                            openPlayStorePromptApp = outcome.requestedName
                        )
                    }

                    is AppLaunchOutcome.Failed -> {
                        CommandExecutionResult(
                            step,
                            outcome.message,
                            "OpenAppFailed",
                            lang,
                            false
                        )
                    }
                }
            }

            is NovaIntent.InstallAppPlayStore -> {
                val msg = appLauncher.openPlayStoreSearch(intent.appName)
                CommandExecutionResult(step, msg, "PlayStoreSearch", lang, true)
            }

            is NovaIntent.ShareToApp -> {
                val msg = appLauncher.shareTextToApp(intent.appName, intent.text)
                CommandExecutionResult(step, msg, "ShareToApp", lang, true)
            }

            is NovaIntent.ControlVolume -> {
                val msg = volumeController.adjustVolume(intent.direction, intent.stream)
                CommandExecutionResult(step, msg, "VolumeControl", lang, true)
            }

            is NovaIntent.SetRingerMode -> {
                val msg = volumeController.setRingerMode(intent.mode)
                CommandExecutionResult(step, msg, "RingerMode", lang, true)
            }

            is NovaIntent.ToggleFlashlight -> {
                val (ok, msg) = phoneControlManager.toggleFlashlight(intent.enable)
                CommandExecutionResult(step, msg, "Flashlight", lang, ok)
            }

            is NovaIntent.OpenSystemPanel -> {
                val msg = phoneControlManager.openSystemPanel(intent.panel)
                CommandExecutionResult(step, msg, "SystemPanel", lang, true)
            }

            is NovaIntent.GlobalNavigation -> {
                val msg = AccessibilityAutomationManager.performGlobal(intent.action)
                CommandExecutionResult(step, msg, "GlobalNav", lang, true)
            }

            is NovaIntent.AutomationStep -> {
                val msg = when (intent.action) {
                    "scroll_down" -> AccessibilityAutomationManager.scrollScreen(down = true)
                    "scroll_up" -> AccessibilityAutomationManager.scrollScreen(down = false)
                    else -> "Automation action executed."
                }

                CommandExecutionResult(step, msg, "Automation", lang, true)
            }

            is NovaIntent.DeviceInfoQuery -> {
                val msg = deviceInfoManager.formatTopicReport(intent.topic)
                CommandExecutionResult(step, msg, "DeviceInfo", lang, true)
            }

            is NovaIntent.SearchContact -> {
                val contacts = contactManager.searchContacts(intent.name)

                val msg = if (!contactManager.hasReadContactsPermission()) {
                    contactManager.openContactsSearchUi(intent.name)
                    "READ_CONTACTS not granted; opened Contacts app for '${intent.name}'."
                } else if (contacts.isEmpty()) {
                    "No contacts matching '${intent.name}'."
                } else {
                    contacts.take(4).joinToString("\n") {
                        "${it.name}: ${it.phone}"
                    }
                }

                CommandExecutionResult(step, msg, "SearchContact", lang, true)
            }

            is NovaIntent.InitiateCall -> {
                val raw = intent.target

                val isDigits = raw
                    .replace(Regex("[+\\-\\s()]"), "")
                    .all { it.isDigit() }

                val resolvedNumber = if (isDigits) {
                    raw
                } else {
                    contactManager.searchContacts(raw).firstOrNull()?.phone
                }

                if (resolvedNumber.isNullOrBlank()) {
                    CommandExecutionResult(
                        step,
                        "Could not find number for '$raw'. Grant Contacts permission or speak the phone number.",
                        "CallLookup",
                        lang,
                        false
                    )
                } else {
                    val prompt = "Confirm call to $raw ($resolvedNumber)?"

                    val confirm = ConfirmationRequest(
                        id = UUID.randomUUID().toString(),
                        title = "Confirm Phone Call",
                        description = prompt,
                        actionType = "CALL_PHONE",
                        onConfirm = {
                            contactManager.placeDirectCall(resolvedNumber)
                        }
                    )

                    onRequestConfirmation(confirm)

                    CommandExecutionResult(
                        step,
                        prompt,
                        "ConfirmCall",
                        lang,
                        true,
                        pendingConfirmation = confirm
                    )
                }
            }

            is NovaIntent.ComposeSms -> {
                val resolved = contactManager.searchContacts(intent.recipient)
                    .firstOrNull()?.phone ?: intent.recipient

                val prompt = "Send SMS to $resolved: \"${intent.body}\"?"

                val confirm = ConfirmationRequest(
                    id = UUID.randomUUID().toString(),
                    title = "Confirm SMS",
                    description = prompt,
                    actionType = "SEND_SMS",
                    onConfirm = {
                        smsManager.sendConfirmedSms(resolved, intent.body)
                    }
                )

                onRequestConfirmation(confirm)

                CommandExecutionResult(
                    step,
                    prompt,
                    "ConfirmSMS",
                    lang,
                    true,
                    pendingConfirmation = confirm
                )
            }

            is NovaIntent.OpenCamera -> {
                onNavigateToCamera(intent.capturePhoto, intent.frontLens)

                val msg = if (intent.capturePhoto) {
                    "Opening NOVA Camera for photo capture."
                } else if (intent.video) {
                    cameraManager.openSystemCamera(videoMode = true)
                } else {
                    "Opening NOVA Camera viewfinder."
                }

                CommandExecutionResult(step, msg, "Camera", lang, true)
            }

            is NovaIntent.OpenGallery -> {
                onNavigateToGalleryPicker()

                CommandExecutionResult(
                    step,
                    "Opening Android Photo Picker.",
                    "GalleryPicker",
                    lang,
                    true
                )
            }

            is NovaIntent.WebSearch -> {
                val msg = internetManager.performSearch(intent.query, intent.target)
                CommandExecutionResult(step, msg, "WebSearch", lang, true)
            }

            is NovaIntent.OpenUrl -> {
                val msg = internetManager.openUrl(intent.url)
                CommandExecutionResult(step, msg, "OpenUrl", lang, true)
            }

            is NovaIntent.WeatherLookup -> {
                val result = internetManager.fetchWeather(intent.city)

                result.fold(
                    onSuccess = {
                        CommandExecutionResult(step, it.summary, "Weather", lang, true)
                    },
                    onFailure = {
                        CommandExecutionResult(
                            step,
                            "Weather check failed: ${it.localizedMessage}",
                            "Weather",
                            lang,
                            false
                        )
                    }
                )
            }

            is NovaIntent.WikiLookup -> {
                val result = internetManager.fetchWikipediaSummary(intent.topic)

                result.fold(
                    onSuccess = {
                        CommandExecutionResult(step, it, "Wikipedia", lang, true)
                    },
                    onFailure = {
                        CommandExecutionResult(
                            step,
                            "Wikipedia lookup failed: ${it.localizedMessage}",
                            "Wikipedia",
                            lang,
                            false
                        )
                    }
                )
            }

            is NovaIntent.MediaControl -> {
                val msg = mediaManager.handleMediaAction(intent.action, intent.query)
                CommandExecutionResult(step, msg, "MediaControl", lang, true)
            }

            is NovaIntent.SetAlarm -> {
                val msg = reminderManager.setAlarm(intent.hour, intent.minute, intent.label)
                CommandExecutionResult(step, msg, "SetAlarm", lang, true)
            }

            is NovaIntent.SetTimer -> {
                val msg = reminderManager.setTimer(intent.seconds, intent.label)
                CommandExecutionResult(step, msg, "SetTimer", lang, true)
            }

            is NovaIntent.CreateNote -> {
                repository.addNote(userId, intent.title, intent.content, lang.code)

                CommandExecutionResult(
                    step,
                    "Saved note: '${intent.title}'.",
                    "CreateNote",
                    lang,
                    true
                )
            }

            is NovaIntent.CreateTask -> {
                repository.addTask(userId, intent.title, intent.priority)

                CommandExecutionResult(
                    step,
                    "Added task: '${intent.title}'.",
                    "CreateTask",
                    lang,
                    true
                )
            }

            is NovaIntent.CreateReminder -> {
                val msg = reminderManager.scheduleWorkReminder(
                    intent.title,
                    intent.delayMinutes
                )

                CommandExecutionResult(step, msg, "Reminder", lang, true)
            }

            is NovaIntent.CalculateExpression -> {
                val result = intentDetector.evaluateMath(intent.expression)
                    ?: "Could not parse math expression. Example: '250 ka 18%' or '45 * 12'."

                CommandExecutionResult(step, "Result: $result", "Calculator", lang, true)
            }

            is NovaIntent.FileSearch -> {
                val files = fileManager.searchFiles(intent.query, settings.safTreeUri)

                val msg = if (files.isEmpty()) {
                    "No files matching '${intent.query}' in NOVA workspace or selected folder."
                } else {
                    "Found ${files.size} file(s): ${
                        files.take(5).joinToString(", ") { it.name }
                    }"
                }

                CommandExecutionResult(step, msg, "FileSearch", lang, true)
            }

            is NovaIntent.TranslateText -> {
                val reply = runCatching {
                    aiEngine.generateOneShot(
                        "Translate to ${intent.targetLanguage}: ${intent.text}",
                        lang
                    )
                }.getOrElse {
                    "Translation failed: ${it.localizedMessage}"
                }

                CommandExecutionResult(
                    step,
                    reply,
                    "Translate",
                    lang,
                    !reply.startsWith("Translation failed")
                )
            }

            is NovaIntent.SummarizeText -> {
                val reply = runCatching {
                    aiEngine.generateOneShot(
                        "Summarize clearly: ${intent.text}",
                        lang
                    )
                }.getOrElse {
                    "Summarization failed: ${it.localizedMessage}"
                }

                CommandExecutionResult(
                    step,
                    reply,
                    "Summarize",
                    lang,
                    !reply.startsWith("Summarization failed")
                )
            }

            is NovaIntent.RunRoutine -> {
                CommandExecutionResult(
                    step,
                    "Run routines from the Automation tab.",
                    "Routine",
                    lang,
                    true
                )
            }

            is NovaIntent.DailyBriefing -> {
                val device = deviceInfoManager.formatTopicReport(
                    com.example.core.DeviceInfoTopic.ALL
                )

                val events = reminderManager.readUpcomingEvents()

                val weather = internetManager.fetchWeather("New Delhi")
                    .getOrNull()?.summary ?: "Weather offline"

                val briefing = buildString {
                    appendLine("Daily NOVA Briefing:")
                    appendLine("• Device: $device")
                    appendLine("• Weather: $weather")
                    appendLine(
                        "• Calendar: ${
                            if (events.isEmpty()) {
                                "No upcoming events readable"
                            } else {
                                events.take(3).joinToString("; ") { it.title }
                            }
                        }"
                    )
                }

                CommandExecutionResult(
                    step,
                    briefing.trim(),
                    "DailyBriefing",
                    lang,
                    true
                )
            }

            is NovaIntent.RunDiagnostics -> {
                val checks = diagnosticsEngine.runAll(
                    ttsReady,
                    settings,
                    deviceInfoManager,
                    internetManager
                )

                val summary = checks.joinToString("\n") {
                    "${if (it.passed) "[PASS]" else "[WARN]"} ${it.component}: ${it.detail}"
                }

                CommandExecutionResult(
                    step,
                    summary,
                    "Diagnostics",
                    lang,
                    true
                )
            }

            is NovaIntent.AskAi -> {
                val activeMemories = if (settings.memoryEnabled) {
                    memories
                } else {
                    emptyList()
                }

                val responseBuilder = StringBuilder()

                try {
                    aiEngine.streamChat(
                        intent.prompt,
                        lang,
                        settings.profile,
                        activeMemories
                    ).collect { partial ->
                        responseBuilder.clear()
                        responseBuilder.append(partial)
                        onStreamAiUpdate(partial)
                    }

                    CommandExecutionResult(
                        step,
                        responseBuilder.toString(),
                        "GeminiAI",
                        lang,
                        true
                    )
                } catch (e: Exception) {
                    CommandExecutionResult(
                        step,
                        "Gemini AI Error: ${
                            e.localizedMessage ?: "Check internet or App Check config"
                        }",
                        "GeminiAIError",
                        lang,
                        false
                    )
                }
            }
        }
    }

    private fun splitChainedSteps(utterance: String): List<String> {
        val lower = utterance.lowercase()

        val hasActionVerb = listOf(
            "open",
            "kholo",
            "volume",
            "flashlight",
            "torch",
            "alarm",
            "timer",
            "battery",
            "wifi",
            "bluetooth"
        ).any { lower.contains(it) }

        if (!hasActionVerb) {
            return listOf(utterance)
        }

        return utterance.split(
            Regex(
                "\\s+(?:and then|aur fir|fir|then|aur)\\s+",
                RegexOption.IGNORE_CASE
            )
        )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
