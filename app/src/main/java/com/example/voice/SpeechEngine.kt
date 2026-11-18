package com.example.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.core.NovaLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpeechEngine(context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val completionCallbacks =
        ConcurrentHashMap<String, (() -> Unit)>()

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->

            if (status == TextToSpeech.SUCCESS) {

                _isReady.value = true

                tts?.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {

                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            utteranceId?.let {
                                completionCallbacks.remove(it)?.invoke()
                            }

                            if (completionCallbacks.isEmpty()) {
                                _isSpeaking.value = false
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            utteranceId?.let {
                                completionCallbacks.remove(it)
                            }

                            if (completionCallbacks.isEmpty()) {
                                _isSpeaking.value = false
                            }
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int
                        ) {
                            utteranceId?.let {
                                completionCallbacks.remove(it)
                            }

                            Log.e(TAG, "TTS error: $errorCode")
                            if (completionCallbacks.isEmpty()) {
                                _isSpeaking.value = false
                            }
                        }
                    }
                )

            } else {
                Log.e(TAG, "TTS initialization failed: $status")
                _isReady.value = false
            }
        }
    }

    fun speak(
        text: String,
        language: NovaLanguage,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        onDone: (() -> Unit)? = null
    ) {
        val engine = tts

        if (engine == null || !_isReady.value || text.isBlank()) {
            onDone?.invoke()
            return
        }

        val locale = when (language) {
            NovaLanguage.HINDI ->
                Locale.forLanguageTag("hi-IN")

            NovaLanguage.HINGLISH,
            NovaLanguage.ENGLISH ->
                Locale.forLanguageTag("en-IN")
        }

        val languageResult = engine.setLanguage(locale)

        if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            engine.language = Locale.US
        }

        engine.setSpeechRate(rate.coerceIn(0.6f, 1.8f))
        engine.setPitch(pitch.coerceIn(0.6f, 1.6f))

        val utteranceId = UUID.randomUUID().toString()

        if (onDone != null) {
            completionCallbacks[utteranceId] = onDone
        }

        _isSpeaking.value = true

        val result = engine.speak(
            text.take(3500),
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (result == TextToSpeech.ERROR) {
            completionCallbacks.remove(utteranceId)
            _isSpeaking.value = false
            Log.e(TAG, "TTS speak request failed")
            onDone?.invoke()
        }
    }

    /**
     * Immediately interrupt current speech.
     */
    fun stop() {
        completionCallbacks.clear()
        _isSpeaking.value = false

        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "TTS stop failed", e)
        }
    }

    /**
     * Alias for stop().
     */
    fun stopSpeaking() {
        stop()
    }

    fun shutdown() {
        stop()

        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "TTS shutdown failed", e)
        }

        tts = null
        _isReady.value = false
    }

    private companion object {
        const val TAG = "NovaSpeechEngine"
    }
}
