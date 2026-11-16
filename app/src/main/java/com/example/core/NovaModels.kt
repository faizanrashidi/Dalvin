package com.example.core

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Streaming(val partial: String) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class NovaLanguage(
    val code: String,
    val label: String,
    val speechLocale: String
) {
    ENGLISH("en", "English", "en-IN"),
    HINDI("hi", "हिन्दी (Hindi)", "hi-IN"),
    HINGLISH("hinglish", "Hinglish", "en-IN")
}

enum class NovaProfile(val id: String, val displayName: String, val description: String) {
    GENERAL("general", "General", "Balanced Hindi, Hinglish & English assistant"),
    WORK("work", "Work", "Concise professional responses & calendar/notes focus"),
    STUDY("study", "Study", "Detailed explanations, summaries & translation"),
    DRIVING("driving", "Driving", "Hands-free spoken confirmations & calling/media focus")
}

sealed interface NovaIntent {
    data class OpenApp(val appName: String, val inAppQuery: String? = null) : NovaIntent
    data class InstallAppPlayStore(val appName: String) : NovaIntent
    data class ShareToApp(val appName: String, val text: String, val mediaType: String? = null) : NovaIntent
    data class ControlVolume(val direction: VolumeDirection, val stream: VolumeStream = VolumeStream.MUSIC) : NovaIntent
    data class SetRingerMode(val mode: RingerTarget) : NovaIntent
    data class ToggleFlashlight(val enable: Boolean?) : NovaIntent
    data class OpenSystemPanel(val panel: SystemPanelType) : NovaIntent
    data class GlobalNavigation(val action: GlobalNavAction) : NovaIntent
    data class DeviceInfoQuery(val topic: DeviceInfoTopic) : NovaIntent
    data class SearchContact(val name: String, val openUi: Boolean = false) : NovaIntent
    data class InitiateCall(val target: String) : NovaIntent
    data class ComposeSms(val recipient: String, val body: String) : NovaIntent
    data class OpenCamera(val capturePhoto: Boolean, val frontLens: Boolean = false, val video: Boolean = false) : NovaIntent
    data object OpenGallery : NovaIntent
    data class WebSearch(val query: String, val target: WebTarget = WebTarget.GOOGLE) : NovaIntent
    data class OpenUrl(val url: String) : NovaIntent
    data class WeatherLookup(val city: String) : NovaIntent
    data class WikiLookup(val topic: String) : NovaIntent
    data class MediaControl(val action: MediaAction, val query: String? = null) : NovaIntent
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : NovaIntent
    data class SetTimer(val seconds: Int, val label: String) : NovaIntent
    data class CreateNote(val title: String, val content: String) : NovaIntent
    data class CreateTask(val title: String, val priority: String = "normal") : NovaIntent
    data class CreateReminder(val title: String, val delayMinutes: Long) : NovaIntent
    data class CalculateExpression(val expression: String) : NovaIntent
    data class FileSearch(val query: String) : NovaIntent
    data class AutomationStep(val action: String, val arg: String) : NovaIntent
    data class TranslateText(val text: String, val targetLanguage: String) : NovaIntent
    data class SummarizeText(val text: String) : NovaIntent
    data class RunRoutine(val routineName: String) : NovaIntent
    data object DailyBriefing : NovaIntent
    data object RunDiagnostics : NovaIntent
    data class AskAi(val prompt: String) : NovaIntent
}

enum class VolumeDirection { UP, DOWN, MAX, MUTE }
enum class VolumeStream { MUSIC, RING, ALARM, CALL }
enum class RingerTarget { NORMAL, VIBRATE, SILENT }
enum class SystemPanelType { WIFI, BLUETOOTH, DISPLAY, SETTINGS, ACCESSIBILITY, NOTIFICATIONS, DND, BATTERY, LOCATION }
enum class GlobalNavAction { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }
enum class DeviceInfoTopic { ALL, BATTERY, MODEL, OS, RAM, STORAGE, NETWORK, SENSORS, APPS }
enum class WebTarget { GOOGLE, YOUTUBE, MAPS, PLAY_STORE }
enum class MediaAction { PLAY_QUERY, PAUSE_RESUME, NEXT, PREVIOUS, STOP, LAUNCH_PLAYER, NOW_PLAYING }

data class ConfirmationRequest(
    val id: String,
    val title: String,
    val description: String,
    val actionType: String,
    val onConfirm: () -> Unit
)

data class CommandExecutionResult(
    val utterance: String,
    val response: String,
    val intentLabel: String,
    val language: NovaLanguage,
    val success: Boolean,
    val pendingConfirmation: ConfirmationRequest? = null,
    val openPlayStorePromptApp: String? = null
)

@IgnoreExtraProperties
data class NovaNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val language: String = "hinglish",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NovaTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val completed: Boolean = false,
    val dueAtMillis: Long = 0L,
    val priority: String = "normal",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CommandHistoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val utterance: String = "",
    val response: String = "",
    val intent: String = "",
    val language: String = "hinglish",
    val success: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NovaMemoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val key: String = "",
    val value: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AutomationRoutine(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val triggerPhrase: String = "",
    val steps: List<String> = emptyList(),
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SecurityAuditEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val action: String = "",
    val detail: String = "",
    val approved: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)
