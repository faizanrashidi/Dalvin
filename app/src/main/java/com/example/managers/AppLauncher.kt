package com.example.managers

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.services.AccessibilityAutomationManager
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

sealed interface AppLaunchOutcome {
    data class Launched(val appLabel: String, val packageName: String, val message: String) : AppLaunchOutcome
    data class NotInstalled(val requestedName: String) : AppLaunchOutcome
    data class Failed(val message: String) : AppLaunchOutcome
}

class AppLauncher(private val context: Context) {

    private val aliasMap = mapOf(
        "yt" to "youtube",
        "insta" to "instagram",
        "wa" to "whatsapp",
        "wp" to "whatsapp",
        "fb" to "facebook",
        "gmap" to "maps",
        "map" to "maps",
        "playstore" to "google play store",
        "play store" to "google play store",
        "cam" to "camera",
        "calc" to "calculator",
        "msg" to "messages"
    )

    fun getInstalledLauncherApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }
        return resolveInfos.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) null else InstalledAppInfo(label, pkg)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun findBestMatchingApp(spokenName: String): InstalledAppInfo? {
        val clean = normalize(spokenName)
        val mapped = aliasMap[clean] ?: clean
        val apps = getInstalledLauncherApps()

        apps.firstOrNull { normalize(it.label) == mapped }?.let { return it }
        apps.firstOrNull { normalize(it.label).contains(mapped) || mapped.contains(normalize(it.label)) }
            ?.let { return it }

        // Fuzzy Levenshtein match within small edit distance
        return apps.minByOrNull { levenshtein(normalize(it.label), mapped) }
            ?.takeIf { levenshtein(normalize(it.label), mapped) <= (mapped.length / 3).coerceAtLeast(2) }
    }

    fun launchApp(spokenName: String, inAppQuery: String? = null): AppLaunchOutcome {
        val match = findBestMatchingApp(spokenName)
            ?: return AppLaunchOutcome.NotInstalled(spokenName.trim())

        val pm = context.packageManager
        if (!inAppQuery.isNullOrBlank()) {
            // Try direct in-app search intent first
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(match.packageName)
                putExtra(SearchManager.QUERY, inAppQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(pm) != null) {
                return try {
                    context.startActivity(searchIntent)
                    AppLaunchOutcome.Launched(
                        match.label,
                        match.packageName,
                        "Opened ${match.label} and searched '$inAppQuery'."
                    )
                } catch (_: Exception) {
                    launchWithOptionalAccessibilitySearch(match, inAppQuery)
                }
            }
            return launchWithOptionalAccessibilitySearch(match, inAppQuery)
        }

        val launchIntent = pm.getLaunchIntentForPackage(match.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AppLaunchOutcome.Failed("No launch activity found for ${match.label}.")

        return try {
            context.startActivity(launchIntent)
            AppLaunchOutcome.Launched(match.label, match.packageName, "Opened ${match.label}.")
        } catch (e: Exception) {
            AppLaunchOutcome.Failed("Failed to launch ${match.label}: ${e.localizedMessage}")
        }
    }

    private fun launchWithOptionalAccessibilitySearch(
        match: InstalledAppInfo,
        query: String
    ): AppLaunchOutcome {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(match.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return AppLaunchOutcome.Failed("Unable to launch ${match.label}.")

        context.startActivity(launchIntent)
        return if (AccessibilityAutomationManager.isServiceActive()) {
            AccessibilityAutomationManager.scheduleInAppSearch(match.packageName, query)
            AppLaunchOutcome.Launched(
                match.label,
                match.packageName,
                "Opened ${match.label} and queued Accessibility search for '$query'."
            )
        } else {
            AppLaunchOutcome.Launched(
                match.label,
                match.packageName,
                "Opened ${match.label}. Enable NOVA Accessibility Service in Automation tab for hands-free in-app typing of '$query'."
            )
        }
    }

    fun openPlayStoreSearch(appName: String): String {
        val encoded = Uri.encode(appName.trim())
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$encoded&c=apps")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=$encoded&c=apps")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(marketIntent)
            "Opened Play Store search for '$appName'."
        } catch (_: Exception) {
            runCatching {
                context.startActivity(webIntent)
                "Opened Play Store web search for '$appName'."
            }.getOrElse { "No Play Store or browser available." }
        }
    }

    fun shareTextToApp(targetAppName: String?, text: String): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (!targetAppName.isNullOrBlank()) {
            findBestMatchingApp(targetAppName)?.let { intent.setPackage(it.packageName) }
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Share via NOVA").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Opened share flow."
        } catch (e: Exception) {
            "Could not share: ${e.localizedMessage}"
        }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), "").trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            curr.copyInto(prev)
        }
        return prev[b.length]
    }
}
