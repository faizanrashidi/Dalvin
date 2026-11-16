package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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

class VoiceRecognitionManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var activeCallback: ((String) -> Unit)? = null

    private val _state = MutableStateFlow<VoiceRecognitionState>(VoiceRecognitionState.Idle)
    val state: StateFlow<VoiceRecognitionState> = _state.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(language: NovaLanguage, onResult: (String) -> Unit) {
        if (!isAvailable()) {
            _state.value = VoiceRecognitionState.Error(
                "Speech recognition service unavailable on this device. You can type commands below."
            )
            return
        }
        activeCallback = onResult
        stopListening()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = VoiceRecognitionState.Listening
                }

                override fun onBeginningOfSpeech() {
                    _state.value = VoiceRecognitionState.Listening
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsDb.value = rmsdB.coerceIn(0f, 12f)
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    _rmsDb.value = 0f
                }

                override fun onError(error: Int) {
                    _rmsDb.value = 0f
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech network error; try offline language pack or type command"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out."
                        else -> "Speech recognizer error ($error)"
                    }
                    Log.w(TAG, "STT error: $msg")
                    _state.value = VoiceRecognitionState.Error(msg)
                }

                override fun onResults(results: Bundle?) {
                    _rmsDb.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    _state.value = VoiceRecognitionState.Idle
                    if (spoken.isNotEmpty()) {
                        activeCallback?.invoke(spoken)
                    } else {
                        _state.value = VoiceRecognitionState.Error("No speech detected.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        _state.value = VoiceRecognitionState.Partial(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.speechLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
            _state.value = VoiceRecognitionState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            _state.value = VoiceRecognitionState.Error(e.localizedMessage ?: "Failed to start microphone")
        }
    }

    fun stopListening() {
        _rmsDb.value = 0f
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
        if (_state.value is VoiceRecognitionState.Listening || _state.value is VoiceRecognitionState.Partial) {
            _state.value = VoiceRecognitionState.Idle
        }
    }

    fun clearError() {
        if (_state.value is VoiceRecognitionState.Error) {
            _state.value = VoiceRecognitionState.Idle
        }
    }

    fun release() {
        stopListening()
        activeCallback = null
    }

    private companion object {
        const val TAG = "VoiceRecognitionMgr"
    }
}
