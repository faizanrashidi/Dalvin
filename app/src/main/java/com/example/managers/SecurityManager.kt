package com.example.managers

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.services.AccessibilityAutomationManager
import com.example.services.NovaNotificationListenerService

data class PermissionStatusItem(
    val permission: String,
    val label: String,
    val rationale: String,
    val granted: Boolean,
    val isSpecialAccess: Boolean = false
)

data class AppPermissionAuditItem(
    val appName: String,
    val packageName: String,
    val requestedCount: Int,
    val dangerousGranted: List<String>
)

class PermissionManager(private val context: Context) {

    fun getRuntimePermissionsSnapshot(): List<PermissionStatusItem> {
        val items = mutableListOf(
            checkRuntime(
                Manifest.permission.RECORD_AUDIO,
                "Microphone (Voice Input)",
                "Used only when you tap the mic or enable wake listening for Hindi/Hinglish/English STT."
            ),
            checkRuntime(
                Manifest.permission.CAMERA,
                "Camera (CameraX)",
                "Allows voice-triggered photo capture and video recording inside NOVA."
            ),
            checkRuntime(
                Manifest.permission.READ_CONTACTS,
                "Contacts",
                "Enables voice contact search and name-to-number calling."
            ),
            checkRuntime(
                Manifest.permission.CALL_PHONE,
                "Direct Calling",
                "Places phone calls after your explicit confirmation."
            ),
            checkRuntime(
                Manifest.permission.SEND_SMS,
                "Direct SMS",
                "Sends SMS messages after confirmation (without it, NOVA opens your SMS app safely)."
            ),
            checkRuntime(
                Manifest.permission.READ_CALENDAR,
                "Calendar",
                "Reads your upcoming schedule for Daily Briefings."
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            items.add(
                checkRuntime(
                    Manifest.permission.POST_NOTIFICATIONS,
                    "Notifications",
                    "Shows scheduled reminders and active wake-service indicators."
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(
                checkRuntime(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    "Bluetooth Connect",
                    "Reads Bluetooth adapter state for Phone Info & shortcuts."
                )
            )
        }
        return items
    }

    fun getSpecialAccessSnapshot(): List<PermissionStatusItem> {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val canWriteSettings = Settings.System.canWrite(context)
        return listOf(
            PermissionStatusItem(
                permission = "ACCESSIBILITY_SERVICE",
                label = "Accessibility Automation",
                rationale = "Optional: lets NOVA scroll, tap buttons, and type search queries inside opened apps.",
                granted = AccessibilityAutomationManager.isServiceActive(),
                isSpecialAccess = true
            ),
            PermissionStatusItem(
                permission = "NOTIFICATION_LISTENER",
                label = "Notification & Media Listener",
                rationale = "Optional: reads incoming messages aloud and shows Now-Playing track metadata.",
                granted = NovaNotificationListenerService.isListenerConnected.value,
                isSpecialAccess = true
            ),
            PermissionStatusItem(
                permission = "ACCESS_NOTIFICATION_POLICY",
                label = "Do Not Disturb Policy",
                rationale = "Allows NOVA to toggle phone Silent / Vibrate modes.",
                granted = nm.isNotificationPolicyAccessGranted,
                isSpecialAccess = true
            ),
            PermissionStatusItem(
                permission = "WRITE_SETTINGS",
                label = "System Brightness Control",
                rationale = "Optional: allows adjusting system screen brightness directly.",
                granted = canWriteSettings,
                isSpecialAccess = true
            )
        )
    }

    private fun checkRuntime(perm: String, label: String, rationale: String): PermissionStatusItem {
        val granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        return PermissionStatusItem(perm, label, rationale, granted, isSpecialAccess = false)
    }
}

class SecurityManager(private val context: Context) {

    fun auditInstalledAppPermissions(limit: Int = 25): List<AppPermissionAuditItem> {
        val pm = context.packageManager
        val launcherApps = AppLauncher(context).getInstalledLauncherApps().take(limit)
        return launcherApps.map { app ->
            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                }
            }.getOrNull()

            val perms = pkgInfo?.requestedPermissions.orEmpty()
            val flags = pkgInfo?.requestedPermissionsFlags
            val grantedSensitive = mutableListOf<String>()
            perms.forEachIndexed { idx, p ->
                val isGranted = flags != null && idx < flags.size &&
                    (flags[idx] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                if (isGranted && isSensitivePerm(p)) {
                    grantedSensitive.add(p.substringAfterLast('.'))
                }
            }
            AppPermissionAuditItem(
                appName = app.label,
                packageName = app.packageName,
                requestedCount = perms.size,
                dangerousGranted = grantedSensitive
            )
        }
    }

    fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateVault(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("NOVA Security Vault")
            .setSubtitle("Verify identity to access security & audit controls")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    private fun isSensitivePerm(p: String): Boolean = p in setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CALENDAR
    )
}
