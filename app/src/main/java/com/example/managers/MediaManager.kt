package com.example.managers

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.example.core.MediaAction
import com.example.services.NovaNotificationListenerService

class MediaManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var localPlayer: MediaPlayer? = null

    fun handleMediaAction(action: MediaAction, query: String? = null): String {
        return when (action) {
            MediaAction.LAUNCH_PLAYER -> launchMusicApp()
            MediaAction.PLAY_QUERY -> playSearch(query.orEmpty())
            MediaAction.PAUSE_RESUME -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Toggled Play/Pause.")
            MediaAction.NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Skipped to next track.")
            MediaAction.PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Went to previous track.")
            MediaAction.STOP -> {
                stopLocalAudio()
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_STOP, "Stopped media playback.")
            }
            MediaAction.NOW_PLAYING -> NovaNotificationListenerService.currentMediaTrack.value
                ?: "No active media metadata detected. Enable NOVA Notification Listener for live track titles."
        }
    }

    fun launchMusicApp(): String {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Opened music player."
        }.getOrElse {
            "No default music app found on device."
        }
    }

    fun playSearch(query: String): String {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            "Requested music playback for '$query'."
        }.getOrElse {
            launchMusicApp()
        }
    }

    fun playLocalAudioUri(uri: Uri): String {
        stopLocalAudio()
        return runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                start()
            }
            localPlayer = player
            "Playing selected audio track inside NOVA."
        }.getOrElse {
            "Cannot play audio: ${it.localizedMessage}"
        }
    }

    fun stopLocalAudio() {
        runCatching {
            localPlayer?.stop()
            localPlayer?.release()
        }
        localPlayer = null
    }

    private fun dispatchMediaKey(keyCode: Int, label: String): String {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return label
    }
}
