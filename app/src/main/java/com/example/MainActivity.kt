package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.core.configureAppCheck
import com.example.ui.NovaAppRoot

class MainActivity : FragmentActivity() {

    private var voiceTriggerCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST be configured first before Compose/ViewModels invoke Firebase.ai
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        if (intent?.getBooleanExtra("nova_voice_trigger", false) == true) {
            voiceTriggerCounter += 1
        }

        setContent {
            NovaAppRoot(voiceTriggerCount = voiceTriggerCounter)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("nova_voice_trigger", false)) {
            voiceTriggerCounter += 1
        }
    }
}
