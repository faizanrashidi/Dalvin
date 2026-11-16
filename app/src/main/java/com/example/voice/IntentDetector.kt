package com.example.voice

import com.example.core.DeviceInfoTopic
import com.example.core.GlobalNavAction
import com.example.core.MediaAction
import com.example.core.NovaIntent
import com.example.core.NovaLanguage
import com.example.core.RingerTarget
import com.example.core.SystemPanelType
import com.example.core.VolumeDirection
import com.example.core.VolumeStream
import com.example.core.WebTarget
import java.util.Locale
import kotlin.math.pow

class IntentDetector {

    fun detectLanguage(input: String): NovaLanguage {
        val hasDevanagari = input.any { it in '\u0900'..'\u097F' }
        if (hasDevanagari) return NovaLanguage.HINDI
        val lower = input.lowercase(Locale.ROOT)
        val hinglishMarkers = listOf(
            "kholo", "karo", "lagao", "bhejo", "batao", "sunao", "likho",
            "chalao", "band", "jalao", "kitna", "kitni", "mera", "mere",
            "kya", "hai", "nahi", "karna", "dikhao", "khicho", "kar do", "karke"
        )
        return if (hinglishMarkers.any { lower.contains(it) }) {
            NovaLanguage.HINGLISH
        } else {
            NovaLanguage.ENGLISH
        }
    }

    fun detect(rawInput: String): NovaIntent {
        val input = rawInput.trim()
        val lower = input.lowercase(Locale.ROOT)

        // 1. Diagnostics & Briefing
        if (containsAny(lower, "diagnostic", "health check", "self test", "system check")) {
            return NovaIntent.RunDiagnostics
        }
        if (containsAny(lower, "daily briefing", "briefing", "aaj ka summary", "morning summary")) {
            return NovaIntent.DailyBriefing
        }

        // 2. Camera & Gallery
        if (containsAny(lower, "photo click", "take photo", "take a photo", "photo khicho", "selfie", "capture photo")) {
            val front = lower.contains("selfie") || lower.contains("front")
            return NovaIntent.OpenCamera(capturePhoto = true, frontLens = front)
        }
        if (containsAny(lower, "record video", "video banao", "shoot video")) {
            return NovaIntent.OpenCamera(capturePhoto = false, video = true)
        }
        if (containsAny(lower, "open camera", "camera kholo", "camera open")) {
            return NovaIntent.OpenCamera(capturePhoto = false)
        }
        if (containsAny(lower, "open gallery", "gallery kholo", "show photos", "photo picker")) {
            return NovaIntent.OpenGallery
        }

        // 3. Flashlight
        if (containsAny(lower, "flashlight", "torch", "flash light")) {
            val enable = when {
                containsAny(lower, "off", "band", "disable", "close") -> false
                containsAny(lower, "on", "jalao", "start", "enable", "kholo") -> true
                else -> null
            }
            return NovaIntent.ToggleFlashlight(enable)
        }

        // 4. Volume & Ringer
        if (containsAny(lower, "volume", "awaz", "sound")) {
            val stream = when {
                lower.contains("ring") -> VolumeStream.RING
                lower.contains("alarm") -> VolumeStream.ALARM
                lower.contains("call") -> VolumeStream.CALL
                else -> VolumeStream.MUSIC
            }
            val dir = when {
                containsAny(lower, "up", "badhao", "increase", "tez", "zyada", "high") -> VolumeDirection.UP
                containsAny(lower, "down", "kam", "decrease", "slow", "low") -> VolumeDirection.DOWN
                containsAny(lower, "max", "full") -> VolumeDirection.MAX
                containsAny(lower, "mute", "band", "silent") -> VolumeDirection.MUTE
                else -> VolumeDirection.UP
            }
            return NovaIntent.ControlVolume(dir, stream)
        }
        if (containsAny(lower, "silent mode", "mute phone", "vibrate mode", "normal mode", "ringer")) {
            val mode = when {
                lower.contains("vibrate") -> RingerTarget.VIBRATE
                lower.contains("normal") || lower.contains("unmute") -> RingerTarget.NORMAL
                else -> RingerTarget.SILENT
            }
            return NovaIntent.SetRingerMode(mode)
        }

        // 5. System Panels & Accessibility Global Nav
        if (containsAny(lower, "wifi", "wi-fi")) return NovaIntent.OpenSystemPanel(SystemPanelType.WIFI)
        if (containsAny(lower, "bluetooth")) return NovaIntent.OpenSystemPanel(SystemPanelType.BLUETOOTH)
        if (containsAny(lower, "brightness", "display settings")) return NovaIntent.OpenSystemPanel(SystemPanelType.DISPLAY)
        if (containsAny(lower, "accessibility settings", "accessibility kholo")) return NovaIntent.OpenSystemPanel(SystemPanelType.ACCESSIBILITY)
        if (containsAny(lower, "dnd", "do not disturb")) return NovaIntent.OpenSystemPanel(SystemPanelType.DND)
        if (containsAny(lower, "go back", "piche jao", "navigate back")) return NovaIntent.GlobalNavigation(GlobalNavAction.BACK)
        if (containsAny(lower, "go home", "home screen")) return NovaIntent.GlobalNavigation(GlobalNavAction.HOME)
        if (containsAny(lower, "recents", "recent apps")) return NovaIntent.GlobalNavigation(GlobalNavAction.RECENTS)
        if (containsAny(lower, "scroll down", "niche scroll")) return NovaIntent.AutomationStep("scroll_down", "")
        if (containsAny(lower, "scroll up", "upar scroll")) return NovaIntent.AutomationStep("scroll_up", "")

        // 6. Device Info
        if (containsAny(lower, "battery", "charging")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.BATTERY)
        if (containsAny(lower, "ram", "memory usage")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.RAM)
        if (containsAny(lower, "storage", "space left", "disk")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.STORAGE)
        if (containsAny(lower, "device model", "phone model", "android version", "os version")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.MODEL)
        if (containsAny(lower, "network status", "internet status", "speed check")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.NETWORK)
        if (containsAny(lower, "sensors", "sensor list")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.SENSORS)
        if (containsAny(lower, "phone info", "device info", "system info")) return NovaIntent.DeviceInfoQuery(DeviceInfoTopic.ALL)

        // 7. Calling & Contacts
        val callMatch = Regex("(?:call|phone lagao|call lagao|call karo|dial)\\s+(?:to\\s+|ko\\s+)?(.+)", RegexOption.IGNORE_CASE).find(input)
        if (callMatch != null) {
            val target = callMatch.groupValues[1]
                .replace(Regex("\\b(ko|par|pe|karo|lagao|please)\\b", RegexOption.IGNORE_CASE), "")
                .trim()
            if (target.isNotEmpty()) return NovaIntent.InitiateCall(target)
        }
        val contactSearch = Regex("(?:search contact|find contact|contact kholo|contact search|contacts me)\\s+(.+)", RegexOption.IGNORE_CASE).find(input)
        if (contactSearch != null) {
            return NovaIntent.SearchContact(contactSearch.groupValues[1].trim(), openUi = true)
        }

        // 8. SMS & Messaging
        val smsMatch = Regex("(?:send sms|sms|message bhejo|text)\\s+(?:to\\s+)?([^:,]+?)(?:\\s+(?:saying|that|likho|bol|ki)\\s+|:)(.+)", RegexOption.IGNORE_CASE).find(input)
        if (smsMatch != null) {
            return NovaIntent.ComposeSms(smsMatch.groupValues[1].trim(), smsMatch.groupValues[2].trim())
        }

        // 9. Alarm, Timer, Note, Task, Reminder
        val alarmMatch = Regex("(?:set alarm|alarm lagao|alarm)\\s+(?:for\\s+|at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(lower)
        if (alarmMatch != null) {
            var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
            val min = alarmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = alarmMatch.groupValues[3]
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
            return NovaIntent.SetAlarm(hour.coerceIn(0, 23), min.coerceIn(0, 59), "NOVA Alarm")
        }
        val timerMatch = Regex("(?:set timer|timer lagao|timer)\\s+(?:for\\s+)?(\\d+)\\s*(sec|second|seconds|min|minute|minutes|m|s)?", RegexOption.IGNORE_CASE).find(lower)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 60
            val unit = timerMatch.groupValues[2]
            val secs = if (unit.startsWith("m")) amount * 60 else amount
            return NovaIntent.SetTimer(secs.coerceAtLeast(1), "NOVA Timer")
        }
        val noteMatch = Regex("(?:take note|save note|create note|note likho|note banao)\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE).find(input)
        if (noteMatch != null) {
            val text = noteMatch.groupValues[1].trim()
            return NovaIntent.CreateNote(text.take(40), text)
        }
        val taskMatch = Regex("(?:add task|create task|todo|task add karo)\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE).find(input)
        if (taskMatch != null) {
            return NovaIntent.CreateTask(taskMatch.groupValues[1].trim())
        }
        val reminderMatch = Regex("(?:remind me|reminder|yaad dilao)\\s+(?:in\\s+(\\d+)\\s*(?:min|minute|minutes)\\s+)?(?:to\\s+)?(.+)", RegexOption.IGNORE_CASE).find(input)
        if (reminderMatch != null) {
            val mins = reminderMatch.groupValues[1].toLongOrNull() ?: 5L
            val title = reminderMatch.groupValues[2].trim().ifEmpty { "NOVA Reminder" }
            return NovaIntent.CreateReminder(title, mins)
        }

        // 10. Calculator
        if (containsAny(lower, "calculate", "calc", "hisab", "kitna hoga", "kitna hota") ||
            Regex("^\\s*[\\d.]+\\s*[+\\-*/x×÷%]\\s*[\\d.]+").containsMatchIn(lower)
        ) {
            return NovaIntent.CalculateExpression(input)
        }

        // 11. Media Controls
        if (containsAny(lower, "pause music", "pause song", "resume music", "gaana roko", "play pause")) {
            return NovaIntent.MediaControl(MediaAction.PAUSE_RESUME)
        }
        if (containsAny(lower, "next song", "next track", "agla gaana", "skip song")) {
            return NovaIntent.MediaControl(MediaAction.NEXT)
        }
        if (containsAny(lower, "previous song", "previous track", "pichla gaana")) {
            return NovaIntent.MediaControl(MediaAction.PREVIOUS)
        }
        if (containsAny(lower, "stop music", "stop song", "music band")) {
            return NovaIntent.MediaControl(MediaAction.STOP)
        }
        if (containsAny(lower, "what song is playing", "now playing", "kaun sa gaana")) {
            return NovaIntent.MediaControl(MediaAction.NOW_PLAYING)
        }
        val playMatch = Regex("(?:play|chalao|sunao)\\s+(?:song\\s+|music\\s+)?(.+)", RegexOption.IGNORE_CASE).find(input)
        if (playMatch != null && !lower.contains("play store")) {
            return NovaIntent.MediaControl(MediaAction.PLAY_QUERY, playMatch.groupValues[1].trim())
        }

        // 12. Weather, Wikipedia, URL, Web Search, YouTube, Maps
        val weatherMatch = Regex("(?:weather|mausam)\\s*(?:in|of|at|ka|ki|me)?\\s*(.*)", RegexOption.IGNORE_CASE).find(input)
        if (weatherMatch != null) {
            val city = weatherMatch.groupValues[1].trim().ifEmpty { "New Delhi" }
            return NovaIntent.WeatherLookup(city)
        }
        val wikiMatch = Regex("(?:wikipedia|wiki)\\s+(.+)", RegexOption.IGNORE_CASE).find(input)
        if (wikiMatch != null) {
            return NovaIntent.WikiLookup(wikiMatch.groupValues[1].trim())
        }
        if (Regex("https?://\\S+|www\\.\\S+", RegexOption.IGNORE_CASE).containsMatchIn(input)) {
            val url = Regex("(https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE).find(input)?.value.orEmpty()
            return NovaIntent.OpenUrl(if (url.startsWith("http")) url else "https://$url")
        }
        val ytSearch = Regex("(?:youtube|yt)\\s+(?:par|pe|on|me)?\\s*(?:search\\s+)?(.+)", RegexOption.IGNORE_CASE).find(input)
        if (ytSearch != null && !lower.startsWith("open youtube") && !lower.startsWith("youtube kholo")) {
            return NovaIntent.WebSearch(ytSearch.groupValues[1].trim(), WebTarget.YOUTUBE)
        }
        val mapsSearch = Regex("(?:navigate to|directions to|maps me|map par)\\s+(.+)", RegexOption.IGNORE_CASE).find(input)
        if (mapsSearch != null) {
            return NovaIntent.WebSearch(mapsSearch.groupValues[1].trim(), WebTarget.MAPS)
        }
        val playStoreSearch = Regex("(?:play store|playstore)\\s+(?:me|pe|on|search|install)?\\s*(.+)", RegexOption.IGNORE_CASE).find(input)
        if (playStoreSearch != null) {
            return NovaIntent.InstallAppPlayStore(playStoreSearch.groupValues[1].trim())
        }
        val fileSearch = Regex("(?:search file|find file|file dhundo|file search)\\s+(.+)", RegexOption.IGNORE_CASE).find(input)
        if (fileSearch != null) {
            return NovaIntent.FileSearch(fileSearch.groupValues[1].trim())
        }

        // 13. Open App (+ optional in-app search)
        val openAndSearch = Regex(
            "(?:open|launch|kholo)\\s+([a-zA-Z0-9 ._-]+?)\\s+(?:and|aur|me|par)\\s+(?:search|find|dhundo)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(input)
        if (openAndSearch != null) {
            return NovaIntent.OpenApp(
                appName = openAndSearch.groupValues[1].trim(),
                inAppQuery = openAndSearch.groupValues[2].trim()
            )
        }
        val openApp = Regex("^(?:open|launch|start)\\s+(.+)$", RegexOption.IGNORE_CASE).find(input)
            ?: Regex("^(.+?)\\s+(?:kholo|open karo|chalu karo)$", RegexOption.IGNORE_CASE).find(input)
        if (openApp != null) {
            return NovaIntent.OpenApp(openApp.groupValues[1].trim())
        }

        // 14. Web Search
        val webSearch = Regex("(?:search|google|dhundo)\\s+(?:for\\s+|about\\s+)?(.+)", RegexOption.IGNORE_CASE).find(input)
        if (webSearch != null) {
            return NovaIntent.WebSearch(webSearch.groupValues[1].trim(), WebTarget.GOOGLE)
        }

        // 15. Translate / Summarize
        val translate = Regex("(?:translate|anuvad)\\s+(.+?)\\s+(?:to|in|into|me)\\s+(\\w+)", RegexOption.IGNORE_CASE).find(input)
        if (translate != null) {
            return NovaIntent.TranslateText(translate.groupValues[1].trim(), translate.groupValues[2].trim())
        }
        val summarize = Regex("(?:summarize|summary|saaransh)\\s*[:\\-]?\\s*(.+)", RegexOption.IGNORE_CASE).find(input)
        if (summarize != null) {
            return NovaIntent.SummarizeText(summarize.groupValues[1].trim())
        }

        // Default: Smart AI
        return NovaIntent.AskAi(input)
    }

    fun evaluateMath(raw: String): String? {
        val cleaned = raw.lowercase(Locale.ROOT)
            .replace("×", "*")
            .replace("x", "*")
            .replace("÷", "/")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("into", "*")
            .replace("divided by", "/")
            .replace("ka", "% of")

        // Percentage pattern: "250 % of 18" or "18% of 250"
        val pctOf = Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*(?:of|ka)\\s*(\\d+(?:\\.\\d+)?)").find(cleaned)
        if (pctOf != null) {
            val a = pctOf.groupValues[1].toDoubleOrNull() ?: return null
            val b = pctOf.groupValues[2].toDoubleOrNull() ?: return null
            return formatNumber((a / 100.0) * b)
        }
        val exprMatch = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/^%])\\s*(-?\\d+(?:\\.\\d+)?)").find(cleaned) ?: return null
        val a = exprMatch.groupValues[1].toDoubleOrNull() ?: return null
        val op = exprMatch.groupValues[2]
        val b = exprMatch.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) return "Cannot divide by zero" else a / b
            "^" -> a.pow(b)
            "%" -> if (b == 0.0) return "Cannot mod by zero" else a % b
            else -> return null
        }
        return formatNumber(res)
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0 && value in -1e12..1e12) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    private fun containsAny(text: String, vararg keys: String): Boolean = keys.any { text.contains(it) }
}
