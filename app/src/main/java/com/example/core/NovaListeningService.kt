
package com.example.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.util.Locale

class NovaListeningService : Service() {

    companion object {
        const val ACTION_START =
            "com.example.NOVA_LISTENING_START"

        const val ACTION_STOP =
            "com.example.NOVA_LISTENING_STOP"

        const val ACTION_TRANSCRIPT =
            "com.example.NOVA_TRANSCRIPT"

        const val EXTRA_TEXT = "recognized_text"

        private const val CHANNEL_ID = "nova_listening_channel"
        private const val NOTIFICATION_ID = 7101
        private const val TAG = "NOVA_LISTENING"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false

    private val restartRunnable = Runnable {
        startRecognition()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopListening()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                if (!hasMicrophonePermission()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (Build.VERSION.SDK_INT >= 34 &&
                    !hasMicrophonePermission()
                ) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                createNotificationChannel()

                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )

                if (!listening) {
                    listening = true
                    startRecognition()
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startRecognition() {
        if (!listening) return

        if (!hasMicrophonePermission()) {
            stopListening()
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition unavailable")
            stopListening()
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this)

                speechRecognizer?.setRecognitionListener(
                    recognitionListener
                )
            }

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault().toLanguageTag()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

            speechRecognizer?.startListening(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Recognition start failed", e)
            scheduleRestart()
        }
    }

    private val recognitionListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Listening started")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech detected")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended")
            }

            override fun onError(error: Int) {
                Log.w(TAG, "Recognition error: $error")
                scheduleRestart()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )

                val spokenText = matches?.firstOrNull()

                if (!spokenText.isNullOrBlank()) {
                    sendTranscript(spokenText)
                }

                scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {}
        }

    private fun sendTranscript(text: String) {
        val broadcast = Intent(ACTION_TRANSCRIPT).apply {
            setPackage(packageName)
            putExtra(EXTRA_TEXT, text)
        }

        sendBroadcast(broadcast)

        Log.d(TAG, "Recognized: $text")
    }

    private fun scheduleRestart() {
        if (!listening) return

        handler.removeCallbacks(restartRunnable)

        // Small delay prevents rapid restart loops.
        handler.postDelayed(restartRunnable, 800)
    }

    private fun stopListening() {
        listening = false

        handler.removeCallbacks(restartRunnable)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Recognizer cleanup failed", e)
        }

        speechRecognizer = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NOVA Voice Listening",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(
            this,
            NovaListeningService::class.java
        ).apply {
            action = ACTION_STOP
        }

        val pendingIntent = PendingIntent.getService(
            this,
            7102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("NOVA Voice AI")
            .setContentText("Voice listening is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Listening",
                pendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        listening = false
        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Destroy failed", e)
        }

        speechRecognizer = null
        super.onDestroy()
    }
}
