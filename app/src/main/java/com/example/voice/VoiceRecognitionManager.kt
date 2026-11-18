package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.core.NovaLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface VoiceRecognitionState {
    data object Idle : VoiceRecognitionState
    data object Listening : VoiceRecognitionState
    data class Partial(val text: String) : VoiceRecognitionState
    data class Error(val message: String) : VoiceRecognitionState
}

class VoiceRecognitionManager(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var callback: ((String) -> Unit)? = null
    private var language: NovaLanguage? = null

    private var continuous = false
    private var paused = false
    private var session = 0

    private val _state =
        MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val restartRunnable = Runnable {
        if (continuous && !paused && recognizer == null) {
            startRecognizer()
        }
    }

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(
        language: NovaLanguage,
        onResult: (String) -> Unit
    ) {
        handler.post {
            continuous = false
            paused = false
            callback = onResult
            this.language = language

            handler.removeCallbacks(restartRunnable)
            startRecognizer()
        }
    }

    fun startContinuousListening(
        language: NovaLanguage,
        onResult: (String) -> Unit
    ) {
        handler.post {
            callback = onResult
            this.language = language
            continuous = true
            paused = false

            handler.removeCallbacks(restartRunnable)
            startRecognizer()
        }
    }

    private fun startRecognizer() {
        if (paused || recognizer != null) return

        val selectedLanguage = language ?: return

        if (!isAvailable()) {
            _state.value = VoiceRecognitionState.Error(
                "Speech recognition service unavailable."
            )
            continuous = false
            return
        }

        val currentSession = ++session

        try {
            val newRecognizer =
                SpeechRecognizer.createSpeechRecognizer(appContext)

            recognizer = newRecognizer

            newRecognizer.setRecognitionListener(
                object : RecognitionListener {

                    private fun isCurrent(): Boolean {
                        return currentSession == session &&
                            recognizer === newRecognizer
                    }

                    override fun onReadyForSpeech(params: Bundle?) {
                        if (!isCurrent()) return
                        _state.value = VoiceRecognitionState.Listening
                    }

                    override fun onBeginningOfSpeech() {
                        if (!isCurrent()) return
                        _state.value = VoiceRecognitionState.Listening
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (!isCurrent()) return
                        _rmsDb.value = rmsdB.coerceIn(0f, 12f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        if (!isCurrent()) return
                        _rmsDb.value = 0f
                    }

                    override fun onError(error: Int) {
                        if (!isCurrent()) return

                        recognizer = null
                        _rmsDb.value = 0f

                        val message = when (error) {
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Audio recording error"

                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                "Microphone permission required"

                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech network error"

                            SpeechRecognizer.ERROR_NO_MATCH ->
                                "Didn't catch that. Listening again..."

                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Listening again..."

                            else -> "Speech recognizer error ($error)"
                        }

                        Log.w(TAG, message)
                        _state.value = VoiceRecognitionState.Error(message)

                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            continuous = false
                            return
                        }

                        scheduleRestart()
                    }

                    override fun onResults(results: Bundle?) {
                        if (!isCurrent()) return

                        recognizer = null
                        _rmsDb.value = 0f

                        val spoken = results
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()

                        _state.value = VoiceRecognitionState.Idle

                        if (spoken.isNotBlank()) {
                            try {
                                callback?.invoke(spoken)
                            } catch (e: Exception) {
                                Log.e(TAG, "Voice callback failed", e)
                            }
                        }

                        scheduleRestart()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!isCurrent()) return

                        val partial = partialResults
                            ?.getStringArrayList(
                                SpeechRecognizer.RESULTS_RECOGNITION
                            )
                            ?.firstOrNull()
                            .orEmpty()

                        if (partial.isNotBlank()) {
                            _state.value =
                                VoiceRecognitionState.Partial(partial)
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) = Unit
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    selectedLanguage.speechLocale
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    selectedLanguage.speechLocale
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

            newRecognizer.startListening(intent)
            _state.value = VoiceRecognitionState.Listening

        } catch (e: Exception) {
            Log.e(TAG, "Unable to start recognition", e)

            recognizer = null

            _state.value = VoiceRecognitionState.Error(
                e.localizedMessage ?: "Unable to start microphone"
            )

            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!continuous || paused) return

        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, RESTART_DELAY)
    }

    fun pauseListening() {
        handler.post {
            paused = true
            handler.removeCallbacks(restartRunnable)
            destroyRecognizer()
            _rmsDb.value = 0f
            _state.value = VoiceRecognitionState.Idle
        }
    }

    fun resumeListening() {
        handler.post {
            if (continuous && paused) {
                paused = false
                startRecognizer()
            }
        }
    }

    private fun destroyRecognizer() {
        session++

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Recognizer cleanup failed", e)
        } finally {
            recognizer = null
        }
    }

    fun stopListening() {
        handler.post {
            continuous = false
            paused = false

            handler.removeCallbacks(restartRunnable)
            destroyRecognizer()

            callback = null
            language = null

            _rmsDb.value = 0f
            _state.value = VoiceRecognitionState.Idle
        }
    }

    fun clearError() {
        handler.post {
            if (_state.value is VoiceRecognitionState.Error) {
                _state.value = VoiceRecognitionState.Idle
            }
        }
    }

    fun release() {
        stopListening()
    }

    private companion object {
        const val TAG = "VoiceRecognitionMgr"
        const val RESTART_DELAY = 700L
    }
}
