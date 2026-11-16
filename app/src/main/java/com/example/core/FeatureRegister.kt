package com.example.core

enum class FeatureStatus(val label: String) {
    NOT_STARTED("NOT STARTED"),
    IN_DEVELOPMENT("IN DEVELOPMENT"),
    IMPLEMENTED("IMPLEMENTED"),
    BUILD_VERIFIED("BUILD VERIFIED"),
    DEVICE_TESTED("DEVICE TESTED"),
    BLOCKED_BY_ANDROID_RESTRICTION("BLOCKED BY ANDROID RESTRICTION")
}

data class FeatureEntry(
    val id: String,
    val name: String,
    val categoryCode: String,
    val categoryName: String,
    val phase: Int,
    val description: String,
    val permissions: String,
    val moduleClass: String,
    val apis: String,
    val connectivity: String,
    val status: FeatureStatus = FeatureStatus.IMPLEMENTED,
    val android16Note: String = "Supported on Android 16 (API 36)"
)

object FeatureRegister {
    val categories: List<Pair<String, String>> = listOf(
        "CAT-01" to "Basic Voice Assistant",
        "CAT-02" to "Phone Control",
        "CAT-03" to "Phone Information",
        "CAT-04" to "Calling & Contacts",
        "CAT-05" to "SMS & Messaging",
        "CAT-06" to "Camera & Gallery",
        "CAT-07" to "Internet & Browser",
        "CAT-08" to "Smart AI",
        "CAT-09" to "File Manager",
        "CAT-10" to "Music & Media",
        "CAT-11" to "Productivity",
        "CAT-12" to "Security & Privacy",
        "CAT-13" to "Advanced Android Automation",
        "CAT-14" to "NOVA Advanced"
    )

    private fun f(
        id: String,
        name: String,
        cat: String,
        phase: Int,
        desc: String,
        perm: String,
        mod: String,
        api: String,
        conn: String,
        status: FeatureStatus = FeatureStatus.IMPLEMENTED,
        note: String = "Supported on Android 16 (API 36)"
    ): FeatureEntry {
        val catName = categories.first { it.first == cat }.second
        return FeatureEntry(id, name, cat, catName, phase, desc, perm, mod, api, conn, status, note)
    }

    val allFeatures: List<FeatureEntry> by lazy {
        listOf(
            // CAT-01: Basic Voice Assistant (12)
            f("NV-001", "Voice Activation Trigger", "CAT-01", 2, "Tap-to-speak mic & floating trigger", "RECORD_AUDIO", "VoiceRecognitionManager", "SpeechRecognizer", "Offline/Online"),
            f("NV-002", "English Speech Recognition", "CAT-01", 2, "Live STT transcription in en-IN / en-US", "RECORD_AUDIO", "VoiceRecognitionManager", "RecognizerIntent", "Offline/Online"),
            f("NV-003", "Hindi Speech Recognition", "CAT-01", 2, "Live STT transcription in hi-IN", "RECORD_AUDIO", "VoiceRecognitionManager", "SpeechRecognizer", "Offline/Online"),
            f("NV-004", "Hinglish Code-Mixed Parsing", "CAT-01", 3, "Parse Hindi+English mixed commands", "RECORD_AUDIO", "CommandProcessor", "Custom NLP + Regex", "Offline/Online"),
            f("NV-005", "Multilingual Text-to-Speech", "CAT-01", 2, "Speak replies in Hindi, Hinglish & English", "None", "SpeechEngine", "TextToSpeech", "Offline"),
            f("NV-006", "Voice Pitch & Speed Control", "CAT-01", 2, "Adjust TTS speech rate & pitch", "None", "SpeechEngine", "TextToSpeech.setSpeechRate", "Offline"),
            f("NV-007", "Continuous Conversation Loop", "CAT-01", 2, "Auto-listen follow-up after TTS ends", "RECORD_AUDIO", "VoiceRecognitionManager", "UtteranceProgressListener", "Offline/Online"),
            f("NV-008", "Wake-Word / Hotkey Architecture", "CAT-01", 2, "Foreground microphone wake service & tile", "FOREGROUND_SERVICE_MICROPHONE", "NovaWakeService", "ForegroundService + TileService", "Offline"),
            f("NV-009", "Live Waveform & Voice Feedback", "CAT-01", 2, "Real-time RMS dB orb visualizer & haptics", "VIBRATE", "NovaOrbVisualizer", "onRmsChanged", "Offline"),
            f("NV-010", "Assistant Role Integration", "CAT-01", 2, "ACTION_ASSIST intent integration", "None", "MainActivity", "ACTION_ASSIST", "Offline"),
            f("NV-011", "Command History Persistence", "CAT-01", 2, "Cloud-synced command history log", "INTERNET", "NovaRepository", "Firestore snapshots()", "Offline/Online"),
            f("NV-012", "Assistant Language & Voice Settings", "CAT-01", 2, "Default language, theme & auto-speak toggles", "None", "SettingsManager", "Jetpack DataStore", "Offline"),

            // CAT-02: Phone Control (15)
            f("NV-013", "Voice Launch Installed App", "CAT-02", 4, "Launch any installed app by spoken name", "<queries>", "AppLauncher", "PackageManager", "Offline"),
            f("NV-014", "Fuzzy & Hinglish App Matching", "CAT-02", 4, "Alias & fuzzy matching for Hinglish names", "<queries>", "AppLauncher", "Levenshtein + AliasMap", "Offline"),
            f("NV-015", "Missing App Play Store Prompt", "CAT-02", 4, "Voice prompt when requested app is missing", "None", "AppLauncher", "SpeechEngine + Confirm", "Offline/Online"),
            f("NV-016", "Play Store App Search & Install", "CAT-02", 4, "Open Play Store search/install screen on confirm", "None", "AppLauncher", "market://search Intent", "Online", note = "Silent install blocked by Android; launches Play Store legally"),
            f("NV-017", "Open App & Search In-App", "CAT-02", 4, "Launch app & pass search query via Intent/Accessibility", "BIND_ACCESSIBILITY_SERVICE (opt)", "AppLauncher", "ACTION_SEARCH / Node Input", "Offline/Online"),
            f("NV-018", "Voice Media/Message Share Routing", "CAT-02", 4, "Route text, photo or video to target app", "None", "AppLauncher", "Intent.ACTION_SEND", "Offline/Online"),
            f("NV-019", "Volume Up / Down / Set Level", "CAT-02", 4, "Adjust Media, Ring, Alarm & Call streams", "None", "VolumeController", "AudioManager.setStreamVolume", "Offline"),
            f("NV-020", "Mute / Unmute / Ringer Mode", "CAT-02", 4, "Switch Normal, Vibrate or Silent with DND check", "ACCESS_NOTIFICATION_POLICY", "VolumeController", "AudioManager.ringerMode", "Offline"),
            f("NV-021", "Brightness Adjustment & Shortcut", "CAT-02", 4, "System Display settings & Write Settings check", "WRITE_SETTINGS (opt)", "PhoneControlManager", "ACTION_DISPLAY_SETTINGS", "Offline"),
            f("NV-022", "Wi-Fi Status & Settings Panel", "CAT-02", 4, "Show Wi-Fi state & open Android Wi-Fi Panel", "ACCESS_WIFI_STATE", "PhoneControlManager", "Settings.Panel.ACTION_WIFI", "Offline", note = "Silent toggle restricted on Android 10+; uses Settings Panel"),
            f("NV-023", "Bluetooth Status & Settings Shortcut", "CAT-02", 4, "Check BT adapter state & open BT settings", "BLUETOOTH_CONNECT", "PhoneControlManager", "BluetoothAdapter", "Offline"),
            f("NV-024", "Flashlight Toggle", "CAT-02", 4, "Toggle rear camera torch ON/OFF by voice", "None", "PhoneControlManager", "CameraManager.setTorchMode", "Offline"),
            f("NV-025", "Haptic Vibration Control", "CAT-02", 4, "Trigger haptic pulse & vibration test", "VIBRATE", "PhoneControlManager", "VibratorManager", "Offline"),
            f("NV-026", "System Navigation Actions", "CAT-02", 12, "Back, Home, Recents, Notifications, Quick Settings", "BIND_ACCESSIBILITY_SERVICE", "AccessibilityAutomationManager", "performGlobalAction", "Offline"),
            f("NV-027", "System Settings Shortcuts", "CAT-02", 4, "Open Battery, Location, Hotspot, Display settings", "None", "PhoneControlManager", "android.provider.Settings", "Offline"),

            // CAT-03: Phone Information (11)
            f("NV-028", "Battery Percentage & Health", "CAT-03", 4, "Battery %, temperature, voltage & health", "None", "DeviceInfoManager", "BatteryManager", "Offline"),
            f("NV-029", "Charging Source & Status", "CAT-03", 4, "AC/USB/Wireless charging state", "None", "DeviceInfoManager", "ACTION_BATTERY_CHANGED", "Offline"),
            f("NV-030", "Device Model & Hardware Info", "CAT-03", 4, "Manufacturer, model, ABI, CPU cores", "None", "DeviceInfoManager", "android.os.Build", "Offline"),
            f("NV-031", "Android Version & Security Patch", "CAT-03", 4, "Android 16 release, SDK & patch level", "None", "DeviceInfoManager", "Build.VERSION", "Offline"),
            f("NV-032", "Live RAM Usage Report", "CAT-03", 4, "Available & total system RAM", "None", "DeviceInfoManager", "ActivityManager.MemoryInfo", "Offline"),
            f("NV-033", "Internal & External Storage Info", "CAT-03", 4, "Total, used & free storage in GB", "None", "DeviceInfoManager", "StatFs", "Offline"),
            f("NV-034", "Active Network & IP Info", "CAT-03", 4, "Wi-Fi/Cellular/5G & metered capabilities", "ACCESS_NETWORK_STATE", "DeviceInfoManager", "ConnectivityManager", "Offline"),
            f("NV-035", "Hardware Sensor Inventory", "CAT-03", 4, "List device hardware sensors", "None", "DeviceInfoManager", "SensorManager.getSensorList", "Offline"),
            f("NV-036", "Live Sensor Diagnostics", "CAT-03", 4, "Live accelerometer & light sensor values", "ACTIVITY_RECOGNITION (opt)", "DeviceInfoManager", "SensorEventListener", "Offline"),
            f("NV-037", "Installed Apps Inventory", "CAT-03", 4, "Count & filter installed launcher apps", "<queries>", "AppLauncher", "PackageManager", "Offline"),
            f("NV-038", "Full Device Diagnostic Report", "CAT-03", 4, "Complete spoken & visual hardware summary", "None", "DeviceInfoManager", "Aggregated Diagnostics", "Offline"),

            // CAT-04: Calling & Contacts (10)
            f("NV-039", "Voice Contact Search", "CAT-04", 5, "Search device contacts by spoken name", "READ_CONTACTS", "ContactManager", "ContactsContract", "Offline"),
            f("NV-040", "Open Contacts & Search Query", "CAT-04", 5, "Open Contacts UI / filter by name", "READ_CONTACTS", "ContactManager", "ContactsContract + Intent", "Offline"),
            f("NV-041", "Read Contact Details Aloud", "CAT-04", 5, "Speak phone number & details via TTS", "READ_CONTACTS", "ContactManager", "ContactsContract.Data", "Offline"),
            f("NV-042", "Open Dialer with Number", "CAT-04", 5, "Launch phone dialer pre-filled", "None", "ContactManager", "Intent.ACTION_DIAL", "Offline"),
            f("NV-043", "Confirmed Voice Call Initiation", "CAT-04", 5, "Ask user confirmation before placing call", "CALL_PHONE", "ContactManager", "Intent.ACTION_CALL", "Offline"),
            f("NV-044", "Create New Contact by Voice", "CAT-04", 5, "Launch insert contact flow pre-filled", "None", "ContactManager", "ContactsContract.Intents.Insert", "Offline"),
            f("NV-045", "Favorite Contacts Quick Dial", "CAT-04", 5, "Quick access to starred/favorite contacts", "READ_CONTACTS", "ContactManager", "ContactsContract.Contacts.STARRED", "Offline"),
            f("NV-046", "Disambiguate Duplicate Contacts", "CAT-04", 5, "Interactive picker for multiple matches", "READ_CONTACTS", "ContactManager", "Disambiguation Sheet", "Offline"),
            f("NV-047", "Call History Access (Policy-Compliant)", "CAT-04", 5, "Open system Call Log; explain Play policy on READ_CALL_LOG", "None", "ContactManager", "CallLog Intent", "Offline", note = "Direct READ_CALL_LOG restricted by Play policy; opens system Call Log"),
            f("NV-048", "Speakerphone / Audio Route Info", "CAT-04", 5, "Toggle speakerphone audio routing", "MODIFY_AUDIO_SETTINGS", "VolumeController", "AudioManager", "Offline"),

            // CAT-05: SMS & Messaging (10)
            f("NV-049", "Voice SMS Dictation & Compose", "CAT-05", 5, "Dictate recipient & message in Hindi/Hinglish/EN", "RECORD_AUDIO", "SMSManager", "SpeechRecognizer", "Offline"),
            f("NV-050", "Confirmed SMS Dispatch", "CAT-05", 5, "Confirm before sending SMS via SmsManager", "SEND_SMS", "SMSManager", "SmsManager.getDefault", "Offline"),
            f("NV-051", "Zero-Permission SMS App Launch", "CAT-05", 5, "Open default SMS app pre-filled via smsto:", "None", "SMSManager", "Intent.ACTION_SENDTO", "Offline"),
            f("NV-052", "Launch Messaging App & Search", "CAT-05", 5, "Open WhatsApp/Telegram & search chat", "BIND_ACCESSIBILITY_SERVICE (opt)", "AppLauncher", "Package + Accessibility", "Offline/Online"),
            f("NV-053", "Quick WhatsApp Message Intent", "CAT-05", 5, "Open WhatsApp chat with pre-filled message", "None", "SMSManager", "https://wa.me/ Intent", "Online"),
            f("NV-054", "Incoming Notification Reader", "CAT-05", 5, "Read incoming message notifications aloud", "BIND_NOTIFICATION_LISTENER_SERVICE", "NovaNotificationListenerService", "NotificationListenerService", "Offline"),
            f("NV-055", "Notification Quick Reply", "CAT-05", 5, "Reply via RemoteInput on active notification", "BIND_NOTIFICATION_LISTENER_SERVICE", "NotificationManager", "RemoteInput", "Offline"),
            f("NV-056", "AI Message Drafting", "CAT-05", 5, "Polish or translate Hinglish message draft", "INTERNET", "AIEngine", "Firebase AI Logic", "Online"),
            f("NV-057", "Share Media via Messaging App", "CAT-05", 5, "Pick photo/video and route to messaging app", "None", "SMSManager", "PickVisualMedia + ACTION_SEND", "Offline/Online"),
            f("NV-058", "SMS Audit Trail", "CAT-05", 5, "Log approved SMS/message actions to Audit Log", "INTERNET", "NovaRepository", "Firestore auditLogs", "Offline/Online"),

            // CAT-06: Camera & Gallery (10)
            f("NV-059", "Voice Launch System Camera", "CAT-06", 6, "Launch system camera app in photo/video mode", "None", "CameraManager", "STILL_IMAGE_CAMERA", "Offline"),
            f("NV-060", "In-App CameraX Viewfinder", "CAT-06", 6, "Built-in CameraX preview with voice HUD", "CAMERA", "CameraManager", "CameraX Preview", "Offline"),
            f("NV-061", "Voice-Triggered Photo Capture", "CAT-06", 6, "Capture photo via voice ('Photo click karo')", "CAMERA", "CameraManager", "ImageCapture.takePicture", "Offline"),
            f("NV-062", "Front / Back Lens Voice Switch", "CAT-06", 6, "Switch selfie & rear lenses by voice", "CAMERA", "CameraManager", "CameraSelector", "Offline"),
            f("NV-063", "Camera Flash Mode Control", "CAT-06", 6, "Toggle Flash Auto / On / Off", "CAMERA", "CameraManager", "ImageCapture.FLASH_MODE", "Offline"),
            f("NV-064", "Voice Video Recording", "CAT-06", 6, "Record video via CameraX VideoCapture", "CAMERA, RECORD_AUDIO", "CameraManager", "CameraX VideoCapture", "Offline"),
            f("NV-065", "Launch System Gallery", "CAT-06", 6, "Open system Gallery / Photos app", "None", "CameraManager", "Intent.ACTION_VIEW", "Offline"),
            f("NV-066", "Zero-Permission Photo/Video Picker", "CAT-06", 6, "Browse media via Android Photo Picker", "None", "CameraManager", "PickVisualMedia", "Offline"),
            f("NV-067", "Recent Capture Preview & Share", "CAT-06", 6, "Preview & share captured media", "None", "CameraManager", "MediaStore + ACTION_SEND", "Offline/Online"),
            f("NV-068", "Voice Photo Upload Routing", "CAT-06", 6, "Pick photo and share/upload to target app", "None", "CameraManager", "ACTION_SEND", "Online"),

            // CAT-07: Internet & Browser (10)
            f("NV-069", "Voice Web Search", "CAT-07", 7, "Launch Google/browser search for spoken query", "INTERNET", "InternetManager", "ACTION_WEB_SEARCH", "Online"),
            f("NV-070", "Launch Browser & URL Opening", "CAT-07", 7, "Validate & open spoken/typed URLs", "INTERNET", "InternetManager", "ACTION_VIEW", "Online"),
            f("NV-071", "YouTube Voice Search", "CAT-07", 7, "Search YouTube directly from voice query", "None", "InternetManager", "youtube.com/results Intent", "Online"),
            f("NV-072", "Google Maps Voice Navigation", "CAT-07", 7, "Search places or launch turn-by-turn nav", "None", "InternetManager", "geo: / google.navigation:", "Online"),
            f("NV-073", "Wikipedia / Knowledge Lookup", "CAT-07", 7, "Fetch real Wikipedia REST summary in-app", "INTERNET", "InternetManager", "OkHttp REST API", "Online"),
            f("NV-074", "Live Weather Lookup", "CAT-07", 7, "Real-time weather via Open-Meteo Geocoding + Forecast API", "INTERNET", "InternetManager", "OkHttp + Open-Meteo", "Online"),
            f("NV-075", "Connectivity & Latency Check", "CAT-07", 7, "Live network status & DNS/HTTPS latency check", "ACCESS_NETWORK_STATE", "InternetManager", "ConnectivityManager", "Offline/Online"),
            f("NV-076", "In-App Web Link Launcher", "CAT-07", 7, "Open curated web resources & docs safely", "INTERNET", "InternetManager", "ACTION_VIEW", "Online"),
            f("NV-077", "News Headline Voice Digest", "CAT-07", 7, "Fetch public RSS headlines & read aloud", "INTERNET", "InternetManager", "OkHttp + XML PullParser", "Online"),
            f("NV-078", "Play Store Web/App Fallback", "CAT-07", 7, "Open Play Store web or app search", "None", "InternetManager", "play.google.com Intent", "Online"),

            // CAT-08: Smart AI (12)
            f("NV-079", "Gemini Natural Language Engine", "CAT-08", 8, "Conversational AI via gemini-3.8-flash", "INTERNET", "AIEngine", "Firebase AI Logic", "Online"),
            f("NV-080", "Streaming AI Responses", "CAT-08", 8, "Live token streaming via generateContentStream()", "INTERNET", "AIEngine", "Firebase.ai", "Online"),
            f("NV-081", "Structured Intent Classification", "CAT-08", 8, "Fallback JSON intent detection with Schema.obj", "INTERNET", "AIEngine", "Schema.obj + responseSchema", "Online"),
            f("NV-082", "Hindi / Hinglish / English AI Reply", "CAT-08", 8, "Language-matched responses across all 3 modes", "INTERNET", "AIEngine", "System Instruction", "Online"),
            f("NV-083", "Multi-Turn Context Memory", "CAT-08", 8, "Session conversation history with startChat()", "INTERNET", "AIEngine", "GenerativeModel.startChat", "Online"),
            f("NV-084", "Persistent User Memory Controls", "CAT-08", 8, "Opt-in memory entries users can view/edit/delete", "INTERNET", "NovaRepository", "Firestore memories", "Offline/Online"),
            f("NV-085", "Text & Article Summarization", "CAT-08", 8, "Summarize pasted text or notes via Gemini", "INTERNET", "AIEngine", "gemini-3.8-flash", "Online"),
            f("NV-086", "Instant Multilingual Translation", "CAT-08", 8, "Translate Hindi <-> Hinglish <-> English", "INTERNET", "AIEngine", "gemini-3.8-flash", "Online"),
            f("NV-087", "Voice Q&A Knowledge Assistant", "CAT-08", 8, "General Q&A, study help & coding assistant", "INTERNET", "AIEngine", "gemini-3.8-flash", "Online"),
            f("NV-088", "Multimodal Image & Camera Q&A", "CAT-08", 8, "Analyze picked/captured photos with Gemini Vision", "INTERNET", "AIEngine", "content { image(); text() }", "Online"),
            f("NV-089", "Smart Command Disambiguation", "CAT-08", 8, "Clarify ambiguous voice requests cleanly", "INTERNET", "AIEngine", "Structured JSON", "Online"),
            f("NV-090", "AI Provider & Key Configuration", "CAT-08", 8, "App Check AI status & custom API config panel", "None", "SettingsManager", "Firebase App Check + DataStore", "Offline/Online"),

            // CAT-09: File Manager (11)
            f("NV-091", "SAF Folder Tree Access", "CAT-09", 9, "Grant persistent folder access via SAF", "None (SAF URI)", "FileManager", "OpenDocumentTree", "Offline"),
            f("NV-092", "Browse Files & Directories", "CAT-09", 9, "List SAF & workspace files with metadata", "SAF URI", "FileManager", "DocumentFile", "Offline"),
            f("NV-093", "Voice File Search", "CAT-09", 9, "Search accessible folders by spoken name", "SAF URI", "FileManager", "DocumentFile traversal", "Offline"),
            f("NV-094", "Create New Folder by Voice", "CAT-09", 9, "Create subfolder in active directory", "SAF URI", "FileManager", "DocumentFile.createDirectory", "Offline"),
            f("NV-095", "Copy File Between Folders", "CAT-09", 9, "Stream-copy file safely to chosen folder", "SAF URI", "FileManager", "ContentResolver Streams", "Offline"),
            f("NV-096", "Move File Between Folders", "CAT-09", 9, "Copy, verify & delete source file", "SAF URI", "FileManager", "DocumentFile", "Offline"),
            f("NV-097", "Rename File or Folder", "CAT-09", 9, "Rename item via DocumentFile.renameTo", "SAF URI", "FileManager", "DocumentFile.renameTo", "Offline"),
            f("NV-098", "Share File to Any App", "CAT-09", 9, "Share file URI via FileProvider / SAF", "None", "FileManager", "ACTION_SEND + FileProvider", "Offline"),
            f("NV-099", "Confirmed File Deletion", "CAT-09", 9, "Delete file ONLY after explicit confirmation", "SAF URI", "FileManager", "DocumentFile.delete + Confirm", "Offline", note = "Enforces confirmation per Section 4 safety rules"),
            f("NV-100", "Open / Preview File", "CAT-09", 9, "Open file in appropriate viewer app", "SAF URI", "FileManager", "Intent.ACTION_VIEW", "Offline"),
            f("NV-101", "App Workspace Storage Sandbox", "CAT-09", 9, "Dedicated NOVA workspace directory ready out-of-box", "None", "FileManager", "getExternalFilesDir", "Offline"),

            // CAT-10: Music & Media (10)
            f("NV-102", "Launch Music App by Voice", "CAT-10", 6, "Open installed music player or streaming app", "<queries>", "MediaManager", "CATEGORY_APP_MUSIC", "Offline"),
            f("NV-103", "Play / Search Music Intent", "CAT-10", 6, "Search & play spoken song/artist in music app", "None", "MediaManager", "MEDIA_PLAY_FROM_SEARCH", "Online/Offline"),
            f("NV-104", "Pause / Resume Playback", "CAT-10", 6, "Toggle playback via media key dispatch", "None", "MediaManager", "KEYCODE_MEDIA_PLAY_PAUSE", "Offline"),
            f("NV-105", "Next Track Control", "CAT-10", 6, "Skip to next track via AudioManager", "None", "MediaManager", "KEYCODE_MEDIA_NEXT", "Offline"),
            f("NV-106", "Previous Track Control", "CAT-10", 6, "Go to previous track via AudioManager", "None", "MediaManager", "KEYCODE_MEDIA_PREVIOUS", "Offline"),
            f("NV-107", "Stop Playback Control", "CAT-10", 6, "Stop active media playback", "None", "MediaManager", "KEYCODE_MEDIA_STOP", "Offline"),
            f("NV-108", "Media Volume Voice Control", "CAT-10", 6, "Dedicated STREAM_MUSIC percentage/step control", "None", "VolumeController", "AudioManager.STREAM_MUSIC", "Offline"),
            f("NV-109", "Now-Playing Media Metadata", "CAT-10", 6, "Read active track metadata via Notification Listener", "BIND_NOTIFICATION_LISTENER_SERVICE", "MediaManager", "MediaSessionManager", "Offline"),
            f("NV-110", "Built-In Audio File Player", "CAT-10", 6, "Play picked local audio files inside NOVA", "None (OpenDocument)", "MediaManager", "MediaPlayer", "Offline"),
            f("NV-111", "Spoken Audio Queue Announcement", "CAT-10", 6, "Announce current playing song via TTS", "BIND_NOTIFICATION_LISTENER_SERVICE", "MediaManager", "SpeechEngine + MediaSession", "Offline"),

            // CAT-11: Productivity (11)
            f("NV-112", "Voice Set Alarm", "CAT-11", 10, "Set system alarm with time & label", "SET_ALARM", "ReminderManager", "AlarmClock.ACTION_SET_ALARM", "Offline"),
            f("NV-113", "Voice Countdown Timer", "CAT-11", 10, "Start system & in-app countdown timer", "SET_ALARM", "ReminderManager", "ACTION_SET_TIMER + Coroutine", "Offline"),
            f("NV-114", "Smart Reminders & Notifications", "CAT-11", 10, "Schedule background WorkManager reminders", "POST_NOTIFICATIONS", "ReminderManager", "WorkManager + NotificationCompat", "Offline"),
            f("NV-115", "Voice Notes Capture", "CAT-11", 10, "Create, pin, search & sync multilingual notes", "INTERNET", "NovaRepository", "Firestore notes", "Offline/Online"),
            f("NV-116", "Task & Todo Manager", "CAT-11", 10, "Add, prioritize, toggle & sync tasks", "INTERNET", "NovaRepository", "Firestore tasks", "Offline/Online"),
            f("NV-117", "Calendar Event Creation", "CAT-11", 10, "Launch pre-filled system Calendar insert", "None", "ReminderManager", "CalendarContract.Events", "Offline"),
            f("NV-118", "Calendar Schedule Reader", "CAT-11", 10, "Read upcoming calendar events with permission", "READ_CALENDAR", "ReminderManager", "CalendarContract.Instances", "Offline"),
            f("NV-119", "Voice Calculator & Unit Converter", "CAT-11", 3, "Evaluate math, percentages & unit conversions", "None", "IntentDetector", "Expression Evaluator", "Offline"),
            f("NV-120", "Voice Dictation Pad", "CAT-11", 2, "Dictate text directly into notes or clipboard", "RECORD_AUDIO", "VoiceRecognitionManager", "SpeechRecognizer + Clipboard", "Offline/Online"),
            f("NV-121", "Daily Briefing Generator", "CAT-11", 10, "Speak battery, tasks, weather & schedule", "None", "CommandProcessor", "Aggregated Briefing", "Offline/Online"),
            f("NV-122", "Stopwatch & Lap Voice Control", "CAT-11", 10, "Built-in stopwatch with lap recording", "None", "ReminderManager", "Coroutine Ticker", "Offline"),

            // CAT-12: Security & Privacy (11)
            f("NV-123", "Live Permission Dashboard", "CAT-12", 11, "Real-time grant status for all permissions", "None", "PermissionManager", "ContextCompat", "Offline"),
            f("NV-124", "Permission Rationale Explainer", "CAT-12", 11, "Clear Hindi/English rationale before prompts", "None", "PermissionManager", "Compose Rationale Dialog", "Offline"),
            f("NV-125", "Special Access Status Monitor", "CAT-12", 11, "Monitor Accessibility, Listener, DND, Write Settings", "None", "PermissionManager", "Settings.Secure", "Offline"),
            f("NV-126", "Installed App Permission Auditor", "CAT-12", 11, "Audit dangerous permissions held by installed apps", "<queries>", "SecurityManager", "PackageManager.GET_PERMISSIONS", "Offline"),
            f("NV-127", "Encrypted Local Preferences", "CAT-12", 1, "Android Keystore AES-GCM local secret storage", "None", "SettingsManager", "AndroidKeyStore + AES/GCM", "Offline"),
            f("NV-128", "Sensitive Action Confirmation Gate", "CAT-12", 11, "Confirm calls, SMS, deletes & script runs", "None", "CommandProcessor", "ConfirmationRequest Gate", "Offline"),
            f("NV-129", "Immutable Security Audit Log", "CAT-12", 11, "Append-only Firestore audit trail", "INTERNET", "NovaRepository", "Firestore auditLogs", "Offline/Online"),
            f("NV-130", "Privacy Mode / Mic Kill Switch", "CAT-12", 11, "Instant toggle to disable mic or AI memory", "None", "SecurityManager", "DataStore Privacy Flags", "Offline"),
            f("NV-131", "One-Tap History & Memory Wipe", "CAT-12", 11, "Delete user history & AI memories cleanly", "INTERNET", "NovaRepository", "Firestore Batch Delete", "Offline/Online"),
            f("NV-132", "Biometric App/Vault Lock", "CAT-12", 11, "Fingerprint / Face unlock for Security Vault", "USE_BIOMETRIC", "SecurityManager", "BiometricPrompt", "Offline"),
            f("NV-133", "Transparent Data Handling Notice", "CAT-12", 11, "Clear disclosure of on-device vs Gemini AI data", "None", "SecurityManager", "Privacy Notice Screen", "Offline"),

            // CAT-13: Advanced Android Automation (11)
            f("NV-134", "Opt-In Accessibility Service", "CAT-13", 12, "User-activated NovaAccessibilityService", "BIND_ACCESSIBILITY_SERVICE", "NovaAccessibilityService", "AccessibilityService", "Offline"),
            f("NV-135", "Accessibility Activation Guide", "CAT-13", 12, "One-tap shortcut & guide to Accessibility settings", "None", "AccessibilityAutomationManager", "ACTION_ACCESSIBILITY_SETTINGS", "Offline"),
            f("NV-136", "Active Screen Node Inspector", "CAT-13", 12, "Inspect current foreground window & clickable nodes", "BIND_ACCESSIBILITY_SERVICE", "NovaAccessibilityService", "rootInActiveWindow", "Offline"),
            f("NV-137", "Automated In-App Search Typing", "CAT-13", 12, "Find search input, set query text & submit", "BIND_ACCESSIBILITY_SERVICE", "AccessibilityAutomationManager", "ACTION_SET_TEXT", "Offline"),
            f("NV-138", "Voice Scroll & Swipe Navigation", "CAT-13", 12, "Scroll up/down or dispatch swipe gesture", "BIND_ACCESSIBILITY_SERVICE", "AccessibilityAutomationManager", "dispatchGesture", "Offline"),
            f("NV-139", "Voice Click by Element Text", "CAT-13", 12, "Tap visible button matching spoken text", "BIND_ACCESSIBILITY_SERVICE", "AccessibilityAutomationManager", "findAccessibilityNodeInfosByText", "Offline"),
            f("NV-140", "User-Approved Automation Routines", "CAT-13", 12, "Create, enable & save multi-step routines", "INTERNET", "NovaRepository", "Firestore routines", "Offline/Online"),
            f("NV-141", "One-Tap / Voice Routine Execution", "CAT-13", 12, "Execute saved routines step-by-step", "Routine-dependent", "CommandProcessor", "Routine Runner", "Offline/Online"),
            f("NV-142", "Notification Triggered Actions", "CAT-13", 12, "Monitor & announce incoming notifications", "BIND_NOTIFICATION_LISTENER_SERVICE", "NovaNotificationListenerService", "onNotificationPosted", "Offline"),
            f("NV-143", "Battery / Connectivity Triggers", "CAT-13", 12, "Evaluate battery & network conditions for triggers", "None", "DeviceInfoManager", "Condition Evaluator", "Offline"),
            f("NV-144", "Automation Emergency Stop", "CAT-13", 12, "Immediate cancel flag for active automation macros", "None", "AccessibilityAutomationManager", "AtomicBoolean Cancel", "Offline"),

            // CAT-14: NOVA Advanced (11)
            f("NV-145", "Multi-Step Chained Voice Engine", "CAT-14", 13, "Execute chained clauses ('aur / and / then')", "Command-dependent", "CommandProcessor", "Sequential Pipeline", "Offline/Online"),
            f("NV-146", "Personalized Assistant Profiles", "CAT-14", 13, "General, Work, Study & Driving modes", "None", "SettingsManager", "DataStore", "Offline"),
            f("NV-147", "Local Offline Command Engine", "CAT-14", 3, "Zero-network multilingual command interpreter", "None", "IntentDetector", "Deterministic Engine", "Offline"),
            f("NV-148", "Modular Skill Plugin Registry", "CAT-14", 1, "Extensible NovaSkill plugin architecture", "None", "SkillPluginRegistry", "Kotlin Plugin Registry", "Offline"),
            f("NV-149", "Script / Python Expression Bridge", "CAT-14", 13, "Safe sandboxed Python-syntax evaluator & local HTTP webhook bridge (no DCL)", "INTERNET (opt local)", "PythonBridge", "AST Evaluator + OkHttp", "Offline/Online", note = "Zero prohibited DCL; safe bundled interpreter + local server bridge"),
            f("NV-150", "Gemini Agentic Workflow Planner", "CAT-14", 13, "Break complex goals into executable NOVA step plans", "INTERNET", "AIEngine", "Schema.obj Step Planner", "Online"),
            f("NV-151", "Intelligent Error Recovery & Retry", "CAT-14", 1, "Classify errors & suggest actionable recovery", "None", "ErrorHandler", "Sealed NovaError", "Offline"),
            f("NV-152", "Self-Diagnostics & Health Suite", "CAT-14", 13, "Live verification of STT, TTS, AI, Auth & Storage", "None", "DiagnosticsEngine", "Subsystem Health Probes", "Offline/Online"),
            f("NV-153", "Quick Settings Tile & App Shortcuts", "CAT-14", 13, "Android Quick Settings tile to launch NOVA", "None", "NovaTileService", "TileService", "Offline"),
            f("NV-154", "Cloud Backup & Cross-Device Sync", "CAT-14", 13, "Google Sign-In + Firestore real-time cloud sync", "INTERNET", "NovaRepository", "CredentialManager + Firestore", "Online"),
            f("NV-155", "Automated Feature Coverage Tracker", "CAT-14", 1, "Live register & phase coverage report of all 155 features", "None", "FeatureRegister", "Embedded Registry", "Offline")
        )
    }

    fun countByCategory(): Map<String, Int> = allFeatures.groupingBy { it.categoryCode }.eachCount()
    fun countByPhase(): Map<Int, Int> = allFeatures.groupingBy { it.phase }.eachCount()
}
