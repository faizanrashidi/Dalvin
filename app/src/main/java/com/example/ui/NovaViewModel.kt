package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AIEngine
import com.example.ai.AiWorkflowPlan
import com.example.core.AutomationRoutine
import com.example.core.CommandExecutionResult
import com.example.core.CommandHistoryItem
import com.example.core.ConfirmationRequest
import com.example.core.DeviceInfoTopic
import com.example.core.NovaLanguage
import com.example.core.NovaMemoryItem
import com.example.core.NovaNote
import com.example.core.NovaProfile
import com.example.core.NovaTask
import com.example.core.SecurityAuditEntry
import com.example.core.UiState
import com.example.data.NovaRepository
import com.example.data.NovaSettingsState
import com.example.data.SettingsManager
import com.example.managers.AppLauncher
import com.example.managers.AppPermissionAuditItem
import com.example.managers.CalendarEventSummary
import com.example.managers.CameraManager
import com.example.managers.ContactEntry
import com.example.managers.ContactManager
import com.example.managers.DeviceInfoManager
import com.example.managers.DeviceSnapshot
import com.example.managers.DiagnosticCheckResult
import com.example.managers.DiagnosticsEngine
import com.example.managers.FileManager
import com.example.managers.InstalledAppInfo
import com.example.managers.InternetManager
import com.example.managers.MediaManager
import com.example.managers.NovaFileEntry
import com.example.managers.PermissionManager
import com.example.managers.PermissionStatusItem
import com.example.managers.PhoneControlManager
import com.example.managers.PythonBridge
import com.example.managers.ReminderManager
import com.example.managers.SMSManager
import com.example.managers.SecurityManager
import com.example.managers.SkillPluginRegistry
import com.example.managers.VolumeController
import com.example.managers.WeatherResult
import com.example.services.AccessibilityAutomationManager
import com.example.ui.theme.NovaThemeMode
import com.example.voice.CommandProcessor
import com.example.voice.IntentDetector
import com.example.voice.SpeechEngine
import com.example.voice.VoiceRecognitionManager
import com.example.voice.VoiceRecognitionState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NovaViewModel(
    appContext: Context,
    private val currentUserId: String,
    private val repository: NovaRepository,
    val settingsManager: SettingsManager
) : ViewModel() {

    private val context = appContext.applicationContext

    val voiceManager = VoiceRecognitionManager(context)
    val speechEngine = SpeechEngine(context)
    val aiEngine = AIEngine()
    val intentDetector = IntentDetector()
    val appLauncher = AppLauncher(context)
    val phoneControlManager = PhoneControlManager(context)
    val volumeController = VolumeController(context)
    val deviceInfoManager = DeviceInfoManager(context)
    val contactManager = ContactManager(context)
    val smsManager = SMSManager(context)
    val cameraManager = CameraManager(context)
    val internetManager = InternetManager(context)
    val fileManager = FileManager(context)
    val mediaManager = MediaManager(context)
    val reminderManager = ReminderManager(context)
    val permissionManager = PermissionManager(context)
    val securityManager = SecurityManager(context)
    val pythonBridge = PythonBridge()
    val skillRegistry = SkillPluginRegistry()
    val diagnosticsEngine = DiagnosticsEngine(context)

    private val commandProcessor = CommandProcessor(
        context = context,
        intentDetector = intentDetector,
        aiEngine = aiEngine,
        repository = repository,
        appLauncher = appLauncher,
        phoneControlManager = phoneControlManager,
        volumeController = volumeController,
        deviceInfoManager = deviceInfoManager,
        contactManager = contactManager,
        smsManager = smsManager,
        cameraManager = cameraManager,
        internetManager = internetManager,
        fileManager = fileManager,
        mediaManager = mediaManager,
        reminderManager = reminderManager,
        skillRegistry = skillRegistry,
        diagnosticsEngine = diagnosticsEngine
    )

    val settings: StateFlow<NovaSettingsState> = settingsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NovaSettingsState())

    val voiceState: StateFlow<VoiceRecognitionState> = voiceManager.state
    val rmsDb: StateFlow<Float> = voiceManager.rmsDb
    val ttsReady: StateFlow<Boolean> = speechEngine.isReady
    val isSpeaking: StateFlow<Boolean> = speechEngine.isSpeaking

    val notesState: StateFlow<UiState<List<NovaNote>>> = observeSafe(repository.observeNotes(currentUserId))
    val tasksState: StateFlow<UiState<List<NovaTask>>> = observeSafe(repository.observeTasks(currentUserId))
    val historyState: StateFlow<UiState<List<CommandHistoryItem>>> = observeSafe(repository.observeHistory(currentUserId))
    val memoriesState: StateFlow<UiState<List<NovaMemoryItem>>> = observeSafe(repository.observeMemories(currentUserId))
    val routinesState: StateFlow<UiState<List<AutomationRoutine>>> = observeSafe(repository.observeRoutines(currentUserId))
    val auditLogsState: StateFlow<UiState<List<SecurityAuditEntry>>> = observeSafe(repository.observeAuditLogs(currentUserId))

    private val _commandOutputState = MutableStateFlow<UiState<CommandExecutionResult>>(UiState.Idle)
    val commandOutputState: StateFlow<UiState<CommandExecutionResult>> = _commandOutputState.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val pendingConfirmation: StateFlow<ConfirmationRequest?> = _pendingConfirmation.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _deviceSnapshot = MutableStateFlow(deviceInfoManager.collectSnapshot())
    val deviceSnapshot: StateFlow<DeviceSnapshot> = _deviceSnapshot.asStateFlow()

    private val _files = MutableStateFlow<List<NovaFileEntry>>(emptyList())
    val files: StateFlow<List<NovaFileEntry>> = _files.asStateFlow()

    private val _weatherResult = MutableStateFlow<UiState<WeatherResult>>(UiState.Idle)
    val weatherResult: StateFlow<UiState<WeatherResult>> = _weatherResult.asStateFlow()

    private val _wikiResult = MutableStateFlow<UiState<String>>(UiState.Idle)
    val wikiResult: StateFlow<UiState<String>> = _wikiResult.asStateFlow()

    private val _headlines = MutableStateFlow<UiState<List<String>>>(UiState.Idle)
    val headlines: StateFlow<UiState<List<String>>> = _headlines.asStateFlow()

    private val _visionResult = MutableStateFlow<UiState<String>>(UiState.Idle)
    val visionResult: StateFlow<UiState<String>> = _visionResult.asStateFlow()

    private val _workflowPlan = MutableStateFlow<UiState<AiWorkflowPlan>>(UiState.Idle)
    val workflowPlan: StateFlow<UiState<AiWorkflowPlan>> = _workflowPlan.asStateFlow()

    private val _pythonOutput = MutableStateFlow<String>("")
    val pythonOutput: StateFlow<String> = _pythonOutput.asStateFlow()

    private val _diagnostics = MutableStateFlow<List<DiagnosticCheckResult>>(emptyList())
    val diagnostics: StateFlow<List<DiagnosticCheckResult>> = _diagnostics.asStateFlow()

    private val _runtimePermissions = MutableStateFlow<List<PermissionStatusItem>>(emptyList())
    val runtimePermissions: StateFlow<List<PermissionStatusItem>> = _runtimePermissions.asStateFlow()

    private val _specialPermissions = MutableStateFlow<List<PermissionStatusItem>>(emptyList())
    val specialPermissions: StateFlow<List<PermissionStatusItem>> = _specialPermissions.asStateFlow()

    private val _appAudit = MutableStateFlow<List<AppPermissionAuditItem>>(emptyList())
    val appAudit: StateFlow<List<AppPermissionAuditItem>> = _appAudit.asStateFlow()

    private val _calendarEvents = MutableStateFlow<List<CalendarEventSummary>>(emptyList())
    val calendarEvents: StateFlow<List<CalendarEventSummary>> = _calendarEvents.asStateFlow()

    private val _contactSearchResults = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contactSearchResults: StateFlow<List<ContactEntry>> = _contactSearchResults.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _openCameraEvent = MutableStateFlow<Pair<Boolean, Boolean>?>(null)
    val openCameraEvent: StateFlow<Pair<Boolean, Boolean>?> = _openCameraEvent.asStateFlow()

    private val _openGalleryPickerEvent = MutableStateFlow(false)
    val openGalleryPickerEvent: StateFlow<Boolean> = _openGalleryPickerEvent.asStateFlow()

    // In-app Timer & Stopwatch
    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds.asStateFlow()
    private var timerJob: Job? = null

    private val _stopwatchMillis = MutableStateFlow(0L)
    val stopwatchMillis: StateFlow<Long> = _stopwatchMillis.asStateFlow()
    private val _stopwatchRunning = MutableStateFlow(false)
    val stopwatchRunning: StateFlow<Boolean> = _stopwatchRunning.asStateFlow()
    private var stopwatchJob: Job? = null

    init {
        refreshInstalledApps()
        refreshPermissions()
        refreshFiles()
        runDiagnostics()
    }

    fun startVoiceListening() {
        val currentSettings = settings.value
        if (currentSettings.micKillSwitch) {
            _statusBanner.value = "Privacy Mic Kill-Switch is ON. Disable it in Security tab to use voice."
            return
        }
        speechEngine.stop()
        voiceManager.startListening(currentSettings.language) { spokenText ->
            executeCommand(spokenText)
        }
    }

    fun stopVoiceListening() {
        voiceManager.stopListening()
    }

    fun executeCommand(rawText: String) {
        val clean = rawText.trim()
        if (clean.isEmpty()) return
        val currentSettings = settings.value
        val activeMemories = (memoriesState.value as? UiState.Success)?.data.orEmpty()

        viewModelScope.launch {
            _commandOutputState.value = UiState.Loading
            try {
                commandProcessor.processCommand(
                    rawUtterance = clean,
                    userId = currentUserId,
                    settings = currentSettings,
                    memories = activeMemories,
                    ttsReady = ttsReady.value,
                    onNavigateToCamera = { capture, front ->
                        _openCameraEvent.value = capture to front
                    },
                    onNavigateToGalleryPicker = {
                        _openGalleryPickerEvent.value = true
                    },
                    onRequestConfirmation = { req ->
                        _pendingConfirmation.value = req
                    },
                    onStreamAiUpdate = { partial ->
                        _commandOutputState.value = UiState.Streaming(partial)
                    }
                ).collect { result ->
                    _commandOutputState.value = UiState.Success(result)
                    runCatching {
                        repository.addHistory(
                            userId = currentUserId,
                            utterance = result.utterance,
                            response = result.response,
                            intent = result.intentLabel,
                            language = result.language.code,
                            success = result.success
                        )
                    }
                    if (currentSettings.autoSpeak && result.response.isNotBlank()) {
                        speechEngine.speak(
                            text = result.response,
                            language = result.language,
                            rate = currentSettings.ttsRate,
                            pitch = currentSettings.ttsPitch
                        ) {
                            if (currentSettings.continuousListening &&
                                result.pendingConfirmation == null &&
                                !currentSettings.micKillSwitch
                            ) {
                                viewModelScope.launch {
                                    delay(350L)
                                    startVoiceListening()
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Command execution error", e)
                _commandOutputState.value = UiState.Error(e.localizedMessage ?: "Command failed")
            }
        }
    }

    fun confirmPendingAction() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        req.onConfirm()
        viewModelScope.launch {
            runCatching {
                repository.logAudit(currentUserId, req.actionType, req.description, approved = true)
            }
        }
    }

    fun cancelPendingAction() {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            runCatching {
                repository.logAudit(currentUserId, req.actionType, "Cancelled: ${req.description}", approved = false)
            }
        }
    }

    fun consumeCameraEvent() {
        _openCameraEvent.value = null
    }

    fun consumeGalleryPickerEvent() {
        _openGalleryPickerEvent.value = false
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _installedApps.value = appLauncher.getInstalledLauncherApps()
        }
    }

    fun refreshDeviceSnapshot() {
        _deviceSnapshot.value = deviceInfoManager.collectSnapshot()
    }

    fun refreshPermissions() {
        _runtimePermissions.value = permissionManager.getRuntimePermissionsSnapshot()
        _specialPermissions.value = permissionManager.getSpecialAccessSnapshot()
    }

    fun runAppPermissionAudit() {
        viewModelScope.launch {
            _appAudit.value = securityManager.auditInstalledAppPermissions()
        }
    }

    fun refreshFiles(query: String = "") {
        _files.value = fileManager.searchFiles(query, settings.value.safTreeUri)
    }

    fun onSafFolderPicked(uri: Uri) {
        viewModelScope.launch {
            settingsManager.setSafTreeUri(uri.toString())
            refreshFiles()
            showBanner("Folder linked via Storage Access Framework.")
        }
    }

    fun createNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.addNote(
                    currentUserId,
                    title.ifBlank { content.take(30) },
                    content,
                    settings.value.language.code
                )
            }.onFailure { showBanner("Save note failed: ${it.localizedMessage}") }
        }
    }

    fun toggleNotePin(note: NovaNote) {
        viewModelScope.launch {
            runCatching { repository.toggleNotePin(currentUserId, note) }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(currentUserId, noteId) }
        }
    }

    fun createTask(title: String, priority: String = "normal") {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addTask(currentUserId, title, priority) }
                .onFailure { showBanner("Add task failed: ${it.localizedMessage}") }
        }
    }

    fun toggleTask(task: NovaTask) {
        viewModelScope.launch {
            runCatching { repository.toggleTaskCompleted(currentUserId, task) }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTask(currentUserId, taskId) }
        }
    }

    fun addMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.addMemory(currentUserId, key, value) }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteMemory(currentUserId, memoryId) }
        }
    }

    fun addRoutine(name: String, trigger: String, stepsCsv: String) {
        val steps = stepsCsv.split(",", "\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (name.isBlank() || steps.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.addRoutine(currentUserId, name, trigger.ifBlank { name }, steps) }
        }
    }

    fun toggleRoutine(routine: AutomationRoutine) {
        viewModelScope.launch {
            runCatching { repository.toggleRoutineEnabled(currentUserId, routine) }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteRoutine(currentUserId, routineId) }
        }
    }

    fun runRoutine(routine: AutomationRoutine) {
        AccessibilityAutomationManager.resetEmergencyStop()
        val combined = routine.steps.joinToString(" then ")
        executeCommand(combined)
        viewModelScope.launch {
            runCatching {
                repository.logAudit(currentUserId, "ROUTINE_RUN", "Executed ${routine.name}", true)
            }
        }
    }

    fun wipeHistoryAndMemories() {
        viewModelScope.launch {
            runCatching {
                repository.wipeHistoryAndMemory(currentUserId)
                aiEngine.clearSession()
                repository.logAudit(currentUserId, "PRIVACY_WIPE", "Cleared history & AI memory", true)
                showBanner("Command history and AI memories cleared.")
            }.onFailure {
                showBanner("Wipe failed: ${it.localizedMessage}")
            }
        }
    }

    fun fetchWeather(city: String) {
        viewModelScope.launch {
            _weatherResult.value = UiState.Loading
            internetManager.fetchWeather(city).fold(
                onSuccess = { _weatherResult.value = UiState.Success(it) },
                onFailure = { _weatherResult.value = UiState.Error(it.localizedMessage ?: "Weather failed") }
            )
        }
    }

    fun fetchWikipedia(topic: String) {
        viewModelScope.launch {
            _wikiResult.value = UiState.Loading
            internetManager.fetchWikipediaSummary(topic).fold(
                onSuccess = { _wikiResult.value = UiState.Success(it) },
                onFailure = { _wikiResult.value = UiState.Error(it.localizedMessage ?: "Wiki lookup failed") }
            )
        }
    }

    fun fetchHeadlines() {
        viewModelScope.launch {
            _headlines.value = UiState.Loading
            internetManager.fetchTopHeadlines().fold(
                onSuccess = { _headlines.value = UiState.Success(it) },
                onFailure = { _headlines.value = UiState.Error(it.localizedMessage ?: "Headlines failed") }
            )
        }
    }

    fun analyzeImageWithGemini(bitmap: Bitmap, question: String) {
        viewModelScope.launch {
            _visionResult.value = UiState.Loading
            try {
                val reply = aiEngine.analyzeImage(
                    bitmap,
                    question.ifBlank { "Describe this image clearly." },
                    settings.value.language
                )
                _visionResult.value = UiState.Success(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _visionResult.value = UiState.Error(e.localizedMessage ?: "Vision error")
            }
        }
    }

    fun planAiWorkflow(goal: String) {
        if (goal.isBlank()) return
        viewModelScope.launch {
            _workflowPlan.value = UiState.Loading
            try {
                val plan = aiEngine.planWorkflow(goal)
                _workflowPlan.value = UiState.Success(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _workflowPlan.value = UiState.Error(e.localizedMessage ?: "Planner failed")
            }
        }
    }

    fun runPythonScript(script: String, sendRemote: Boolean) {
        viewModelScope.launch {
            _pythonOutput.value = "Running..."
            val out = pythonBridge.evaluateScript(script, settings.value.pythonBridgeEndpoint, sendRemote)
            _pythonOutput.value = out
        }
    }

    fun runDiagnostics() {
        _diagnostics.value = diagnosticsEngine.runAll(
            ttsReady.value,
            settings.value,
            deviceInfoManager,
            internetManager
        )
    }

    fun searchContacts(query: String) {
        _contactSearchResults.value = contactManager.searchContacts(query)
    }

    fun refreshCalendar() {
        _calendarEvents.value = reminderManager.readUpcomingEvents()
    }

    fun startCountdown(seconds: Int) {
        timerJob?.cancel()
        _countdownSeconds.value = seconds.coerceAtLeast(1)
        timerJob = viewModelScope.launch {
            while (_countdownSeconds.value > 0) {
                delay(1000L)
                _countdownSeconds.value -= 1
            }
            speechEngine.speak("Timer finished!", settings.value.language)
            showBanner("NOVA Timer finished!")
        }
    }

    fun stopCountdown() {
        timerJob?.cancel()
        _countdownSeconds.value = 0
    }

    fun toggleStopwatch() {
        if (_stopwatchRunning.value) {
            stopwatchJob?.cancel()
            _stopwatchRunning.value = false
        } else {
            _stopwatchRunning.value = true
            stopwatchJob = viewModelScope.launch {
                while (_stopwatchRunning.value) {
                    delay(100L)
                    _stopwatchMillis.value += 100L
                }
            }
        }
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        _stopwatchRunning.value = false
        _stopwatchMillis.value = 0L
    }

    fun updateLanguage(lang: NovaLanguage) = viewModelScope.launch { settingsManager.setLanguage(lang) }
    fun updateTheme(mode: NovaThemeMode) = viewModelScope.launch { settingsManager.setThemeMode(mode) }
    fun updateProfile(profile: NovaProfile) = viewModelScope.launch { settingsManager.setProfile(profile) }
    fun updateTtsRate(rate: Float) = viewModelScope.launch { settingsManager.setTtsRate(rate) }
    fun updateTtsPitch(pitch: Float) = viewModelScope.launch { settingsManager.setTtsPitch(pitch) }
    fun updateAutoSpeak(on: Boolean) = viewModelScope.launch { settingsManager.setAutoSpeak(on) }
    fun updateContinuousListening(on: Boolean) = viewModelScope.launch { settingsManager.setContinuousListening(on) }
    fun updateMicKillSwitch(on: Boolean) = viewModelScope.launch { settingsManager.setMicKillSwitch(on) }
    fun updateMemoryEnabled(on: Boolean) = viewModelScope.launch { settingsManager.setMemoryEnabled(on) }
    fun updateBiometricLock(on: Boolean) = viewModelScope.launch { settingsManager.setBiometricLock(on) }
    fun updatePythonEndpoint(ep: String) = viewModelScope.launch { settingsManager.setPythonBridgeEndpoint(ep) }
    fun saveCustomApiKey(key: String) = viewModelScope.launch {
        settingsManager.saveEncryptedCustomApiKey(key)
        showBanner("Custom key encrypted via Android Keystore (primary Gemini runs on App Check AI Logic).")
    }

    private fun <T> observeSafe(source: kotlinx.coroutines.flow.Flow<T>): StateFlow<UiState<T>> {
        return source
            .map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsError == null && error is CancellationException) throw error
                val root = fsError ?: error
                Log.w(TAG, "Stream error: ${root.message}", root)
                emit(UiState.Error(root.message ?: "Stream error"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )
    }

    override fun onCleared() {
        voiceManager.release()
        speechEngine.shutdown()
        mediaManager.stopLocalAudio()
        timerJob?.cancel()
        stopwatchJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val userId: String,
        private val repository: NovaRepository,
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NovaViewModel(context, userId, repository, settingsManager) as T
        }
    }

    private companion object {
        const val TAG = "NovaViewModel"
    }
}
