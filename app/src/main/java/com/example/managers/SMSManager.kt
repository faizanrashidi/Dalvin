package com.example.managers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SMSManager(private val context: Context) {

    fun hasSendSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun openSmsComposer(recipientNumber: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(recipientNumber)}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Opened SMS composer for $recipientNumber."
        } catch (e: Exception) {
            "No SMS app available: ${e.localizedMessage}"
        }
    }

    fun sendConfirmedSms(recipientNumber: String, body: String): Pair<Boolean, String> {
        if (!hasSendSmsPermission()) {
            return false to openSmsComposer(recipientNumber, body) + " (SEND_SMS not granted; opened composer)"
        }
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(body)
            sms.sendMultipartTextMessage(recipientNumber, null, parts, null, null)
            true to "SMS sent to $recipientNumber."
        } catch (e: Exception) {
            false to "Failed to send SMS: ${e.localizedMessage}"
        }
    }

    fun openWhatsAppMessage(phoneOrEmpty: String, message: String): String {
        val cleaned = phoneOrEmpty.replace(Regex("[^0-9+]"), "")
        val url = if (cleaned.isNotEmpty()) {
            "https://wa.me/${cleaned.trimStart('+')}?text=${Uri.encode(message)}"
        } else {
            "https://wa.me/?text=${Uri.encode(message)}"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened WhatsApp with prepared message."
        }.getOrElse {
            "WhatsApp or browser unavailable."
        }
    }
}
