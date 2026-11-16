package com.example.services

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val hasQuickReply: Boolean
)

class NovaNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerConnected.value = true
        instance = this
    }

    override fun onListenerDisconnected() {
        _isListenerConnected.value = false
        instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        if (notification.category == Notification.CATEGORY_TRANSPORT) {
            _currentMediaTrack.value = listOf(title, body).filter { it.isNotBlank() }.joinToString(" — ")
        }

        val hasReply = notification.actions?.any { !it.remoteInputs.isNullOrEmpty() } == true
        val entry = CapturedNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = body,
            timestamp = sbn.postTime,
            hasQuickReply = hasReply
        )
        _recentNotifications.value = (listOf(entry) + _recentNotifications.value)
            .distinctBy { it.key }
            .take(25)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        _recentNotifications.value = _recentNotifications.value.filterNot { it.key == key }
    }

    companion object {
        private var instance: NovaNotificationListenerService? = null

        private val _isListenerConnected = MutableStateFlow(false)
        val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()

        private val _currentMediaTrack = MutableStateFlow<String?>(null)
        val currentMediaTrack: StateFlow<String?> = _currentMediaTrack.asStateFlow()

        fun sendQuickReply(notificationKey: String, replyText: String): String {
            val svc = instance ?: return "Notification Listener is not enabled."
            val sbn = svc.activeNotifications?.firstOrNull { it.key == notificationKey }
                ?: return "Notification no longer active."
            val action = sbn.notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
                ?: return "No quick-reply action on this notification."
            val remoteInputs = action.remoteInputs ?: return "No RemoteInput."
            val intent = Intent()
            val bundle = Bundle()
            remoteInputs.forEach { input ->
                bundle.putCharSequence(input.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            return runCatching {
                action.actionIntent.send(svc, 0, intent)
                "Sent quick reply: '$replyText'."
            }.getOrElse {
                "Quick reply failed: ${it.localizedMessage}"
            }
        }
    }
}
