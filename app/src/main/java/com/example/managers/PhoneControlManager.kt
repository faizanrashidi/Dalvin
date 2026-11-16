package com.example.managers

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as SystemCameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.example.core.RingerTarget
import com.example.core.SystemPanelType
import com.example.core.VolumeDirection
import com.example.core.VolumeStream

class VolumeController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun adjustVolume(direction: VolumeDirection, stream: VolumeStream): String {
        val streamType = when (stream) {
            VolumeStream.MUSIC -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
        }
        val max = audioManager.getStreamMaxVolume(streamType)
        val current = audioManager.getStreamVolume(streamType)
        val step = (max / 5).coerceAtLeast(1)

        val target = when (direction) {
            VolumeDirection.UP -> (current + step).coerceAtMost(max)
            VolumeDirection.DOWN -> (current - step).coerceAtLeast(0)
            VolumeDirection.MAX -> max
            VolumeDirection.MUTE -> 0
        }

        return try {
            audioManager.setStreamVolume(streamType, target, AudioManager.FLAG_SHOW_UI)
            val pct = if (max > 0) (target * 100) / max else 0
            "${stream.name.lowercase().replaceFirstChar { it.uppercase() }} volume set to $pct%"
        } catch (e: SecurityException) {
            "DND permission required to change ${stream.name.lowercase()} volume."
        }
    }

    fun setVolumePercent(stream: VolumeStream, percent: Int): String {
        val streamType = when (stream) {
            VolumeStream.MUSIC -> AudioManager.STREAM_MUSIC
            VolumeStream.RING -> AudioManager.STREAM_RING
            VolumeStream.ALARM -> AudioManager.STREAM_ALARM
            VolumeStream.CALL -> AudioManager.STREAM_VOICE_CALL
        }
        val max = audioManager.getStreamMaxVolume(streamType)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        return try {
            audioManager.setStreamVolume(streamType, target, AudioManager.FLAG_SHOW_UI)
            "${stream.name.lowercase().replaceFirstChar { it.uppercase() }} volume set to $percent%"
        } catch (_: SecurityException) {
            "Grant Do Not Disturb access to adjust ringer volume."
        }
    }

    fun getVolumeLevels(): Map<VolumeStream, Int> {
        fun pct(streamType: Int): Int {
            val max = audioManager.getStreamMaxVolume(streamType).coerceAtLeast(1)
            return (audioManager.getStreamVolume(streamType) * 100) / max
        }
        return mapOf(
            VolumeStream.MUSIC to pct(AudioManager.STREAM_MUSIC),
            VolumeStream.RING to pct(AudioManager.STREAM_RING),
            VolumeStream.ALARM to pct(AudioManager.STREAM_ALARM),
            VolumeStream.CALL to pct(AudioManager.STREAM_VOICE_CALL)
        )
    }

    fun setRingerMode(target: RingerTarget): String {
        if (target == RingerTarget.SILENT && !notificationManager.isNotificationPolicyAccessGranted) {
            openDndSettings()
            return "Please grant Do Not Disturb access to enable Silent mode."
        }
        return try {
            val mode = when (target) {
                RingerTarget.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingerTarget.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                RingerTarget.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            audioManager.ringerMode = mode
            "Phone ringer mode set to ${target.name.lowercase()}"
        } catch (_: SecurityException) {
            openDndSettings()
            "Do Not Disturb access required for silent mode."
        }
    }

    fun toggleSpeakerphone(enable: Boolean): String {
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = enable
        return if (enable) "Speakerphone enabled" else "Speakerphone disabled"
    }

    private fun openDndSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

class PhoneControlManager(private val context: Context) {

    private var torchEnabled = false

    fun toggleFlashlight(enable: Boolean? = null): Pair<Boolean, String> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as SystemCameraManager
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false to "No flashlight hardware detected on this device."
            val next = enable ?: !torchEnabled
            cameraManager.setTorchMode(cameraId, next)
            torchEnabled = next
            true to if (next) "Flashlight turned ON." else "Flashlight turned OFF."
        } catch (e: Exception) {
            false to "Flashlight unavailable: ${e.localizedMessage ?: "hardware busy"}"
        }
    }

    fun vibratePulse(durationMs: Long = 120L): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(durationMs)
                }
            }
            "Haptic pulse triggered."
        } catch (e: Exception) {
            "Vibration unavailable: ${e.localizedMessage}"
        }
    }

    fun getWifiAndBluetoothSummary(): String {
        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = wifiMgr?.isWifiEnabled == true
        val btMgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btEnabled = runCatching { btMgr?.adapter?.isEnabled == true }.getOrDefault(false)
        return "Wi-Fi: ${if (wifiEnabled) "ON" else "OFF"} · Bluetooth: ${if (btEnabled) "ON" else "OFF"}"
    }

    fun openSystemPanel(panel: SystemPanelType): String {
        val intent = when (panel) {
            SystemPanelType.WIFI -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Intent(Settings.Panel.ACTION_WIFI)
            } else {
                Intent(Settings.ACTION_WIFI_SETTINGS)
            }
            SystemPanelType.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            SystemPanelType.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            SystemPanelType.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
            SystemPanelType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            SystemPanelType.NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            SystemPanelType.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            SystemPanelType.BATTERY -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            SystemPanelType.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opened ${panel.name.lowercase().replace('_', ' ')} settings."
        } catch (e: Exception) {
            "Unable to open settings: ${e.localizedMessage}"
        }
    }
}
