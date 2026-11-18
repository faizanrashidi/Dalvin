package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.core.NovaListeningService
import com.example.core.configureAppCheck
import com.example.ui.NovaAppRoot

class MainActivity : FragmentActivity() {

    private var voiceTriggerCounter by mutableIntStateOf(0)

    private var pendingListeningStart = false

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted && pendingListeningStart) {
                pendingListeningStart = false
                startNovaListening()
            } else {
                pendingListeningStart = false
                Log.w("NOVA", "Microphone permission denied")
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            Log.d("NOVA", "Notification permission: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase App Check initialization
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        if (intent?.getBooleanExtra(
                "nova_voice_trigger",
                false
            ) == true
        ) {
            voiceTriggerCounter += 1
        }

        setContent {
            NovaAppRoot(
                voiceTriggerCount = voiceTriggerCounter
            )
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // Call this when the user turns NOVA microphone ON
    fun requestNovaListening() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startNovaListening()
        } else {
            pendingListeningStart = true

            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    // Start background listening service
    private fun startNovaListening() {
        try {
            val serviceIntent = Intent(
                this,
                NovaListeningService::class.java
            ).apply {
                action = NovaListeningService.ACTION_START
            }

            ContextCompat.startForegroundService(
                this,
                serviceIntent
            )

            Log.d("NOVA", "Background listening requested")

        } catch (e: Exception) {
            Log.e(
                "NOVA",
                "Unable to start listening service",
                e
            )
        }
    }

    // Stop background listening service
    fun stopNovaListening() {
        val serviceIntent = Intent(
            this,
            NovaListeningService::class.java
        ).apply {
            action = NovaListeningService.ACTION_STOP
        }

        startService(serviceIntent)

        Log.d("NOVA", "Background listening stopped")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra(
                "nova_voice_trigger",
                false
            )
        ) {
            voiceTriggerCounter += 1
        }
    }
}
