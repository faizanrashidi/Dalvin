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

class SpeechEngine(context: Context) {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var onCompleteAction: (() -> Unit)? = null
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _isReady.value = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        val cb = onCompleteAction
                        onCompleteAction = null
                        cb?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        onCompleteAction = null
                    }
                })
            } else {
                Log.w(TAG, "TTS initialization returned status=$status")
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
        val engine = tts ?: return
        if (!_isReady.value || text.isBlank()) {
            onDone?.invoke()
            return
        }
        val locale = when (language) {
            NovaLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
            NovaLanguage.HINGLISH -> Locale.forLanguageTag("en-IN")
            NovaLanguage.ENGLISH -> Locale.forLanguageTag("en-IN")
        }
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.US
        }
        engine.setSpeechRate(rate.coerceIn(0.6f, 1.8f))
        engine.setPitch(pitch.coerceIn(0.6f, 1.6f))
        onCompleteAction = onDone
        val utteranceId = UUID.randomUUID().toString()
        engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        onCompleteAction = null
        _isSpeaking.value = false
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        _isReady.value = false
    }

    private companion object {
        const val TAG = "NovaSpeechEngine"
    }
}
