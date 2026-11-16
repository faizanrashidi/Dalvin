package com.example.managers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.core.DeviceInfoTopic
import java.util.Locale

data class DeviceSnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val chargingSource: String,
    val batteryTempC: Float,
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val totalRamGb: Double,
    val availableRamGb: Double,
    val totalStorageGb: Double,
    val freeStorageGb: Double,
    val networkSummary: String,
    val sensorNames: List<String>
)

class DeviceInfoManager(private val context: Context) {

    fun collectSnapshot(): DeviceSnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "On Battery"
        }
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val gb = 1024.0 * 1024.0 * 1024.0
        val totalRam = memInfo.totalMem / gb
        val availRam = memInfo.availMem / gb

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorage = (stat.blockCountLong * stat.blockSizeLong) / gb
        val freeStorage = (stat.availableBlocksLong * stat.blockSizeLong) / gb

        val networkSummary = getNetworkDescription()

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            .map { it.name }
            .distinct()
            .take(30)

        return DeviceSnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            chargingSource = source,
            batteryTempC = tempC,
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE ?: "16",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            totalRamGb = totalRam,
            availableRamGb = availRam,
            totalStorageGb = totalStorage,
            freeStorageGb = freeStorage,
            networkSummary = networkSummary,
            sensorNames = sensors
        )
    }

    fun getNetworkDescription(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork ?: return "Offline (No active network)"
        val caps = cm.getNetworkCapabilities(active) ?: return "Disconnected"
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Connected"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return "$transport (${if (validated) "Online" else "Limited"})"
    }

    fun formatTopicReport(topic: DeviceInfoTopic): String {
        val s = collectSnapshot()
        return when (topic) {
            DeviceInfoTopic.BATTERY ->
                "Battery is at ${s.batteryPercent}% (${s.chargingSource}), temperature ${s.batteryTempC}°C."
            DeviceInfoTopic.MODEL, DeviceInfoTopic.OS ->
                "${s.manufacturer} ${s.deviceModel} running Android ${s.androidVersion} (API ${s.sdkInt}), patch ${s.securityPatch}."
            DeviceInfoTopic.RAM ->
                String.format(Locale.US, "Available RAM: %.2f GB / %.2f GB.", s.availableRamGb, s.totalRamGb)
            DeviceInfoTopic.STORAGE ->
                String.format(Locale.US, "Free storage: %.2f GB / %.2f GB.", s.freeStorageGb, s.totalStorageGb)
            DeviceInfoTopic.NETWORK ->
                "Network status: ${s.networkSummary}."
            DeviceInfoTopic.SENSORS ->
                "Detected ${s.sensorNames.size} sensors including: ${s.sensorNames.take(5).joinToString(", ")}."
            DeviceInfoTopic.APPS ->
                "App inventory available in Controls & Apps."
            DeviceInfoTopic.ALL -> String.format(
                Locale.US,
                "%s %s (Android %s, API %d) · Battery %d%% (%s) · RAM %.1f/%.1f GB · Storage %.1f/%.1f GB free · %s",
                s.manufacturer,
                s.deviceModel,
                s.androidVersion,
                s.sdkInt,
                s.batteryPercent,
                s.chargingSource,
                s.availableRamGb,
                s.totalRamGb,
                s.freeStorageGb,
                s.totalStorageGb,
                s.networkSummary
            )
        }
    }
}
