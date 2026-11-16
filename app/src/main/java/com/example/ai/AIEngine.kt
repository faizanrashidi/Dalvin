package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.core.NovaLanguage
import com.example.core.NovaMemoryItem
import com.example.core.NovaProfile
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiWorkflowPlan(
    val summary: String,
    val steps: List<String>
)

class AIEngine {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = DEFAULT_MODEL)
    }

    private val plannerSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Brief explanation in user language"),
            "steps" to Schema.array(
                items = Schema.string(),
                description = "Executable NOVA voice commands in order"
            )
        )
    )

    private val plannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = plannerSchema
            }
        )
    }

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    fun streamChat(
        prompt: String,
        language: NovaLanguage,
        profile: NovaProfile,
        memories: List<NovaMemoryItem>
    ): Flow<String> = flow {
        val systemContext = buildSystemPrompt(language, profile, memories)
        val fullPrompt = buildString {
            appendLine(systemContext)
            if (conversationHistory.isNotEmpty()) {
                appendLine("Recent conversation:")
                conversationHistory.takeLast(6).forEach { (u, a) ->
                    appendLine("User: $u")
                    appendLine("NOVA: $a")
                }
            }
            appendLine("User: $prompt")
            append("NOVA:")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(TIMEOUT_MS) {
                chatModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            val finalReply = accumulated.toString().trim()
            if (finalReply.isNotEmpty()) {
                conversationHistory.add(prompt to finalReply)
                if (conversationHistory.size > 20) conversationHistory.removeAt(0)
            } else {
                throw IllegalStateException("Gemini returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI stream timed out", e)
            throw IllegalStateException("AI request timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI stream failed", e)
            throw e
        }
    }

    suspend fun generateOneShot(prompt: String, language: NovaLanguage): String {
        return try {
            val langHint = when (language) {
                NovaLanguage.HINDI -> "Respond in natural Hindi (Devanagari)."
                NovaLanguage.HINGLISH -> "Respond in friendly conversational Hinglish (Roman Hindi + English)."
                NovaLanguage.ENGLISH -> "Respond in clear, concise English."
            }
            val response = withTimeout(TIMEOUT_MS) {
                chatModel.generateContent("$langHint\n\n$prompt")
            }
            response.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalStateException("Empty response from Gemini.")
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            throw IllegalStateException("AI timed out after 60s.", e)
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun analyzeImage(bitmap: Bitmap, question: String, language: NovaLanguage): String {
        val scaled = scaleBitmapForPayload(bitmap)
        val langInstruction = when (language) {
            NovaLanguage.HINDI -> "Answer in Hindi."
            NovaLanguage.HINGLISH -> "Answer in Hinglish."
            NovaLanguage.ENGLISH -> "Answer in English."
        }
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                chatModel.generateContent(
                    content {
                        image(scaled)
                        text("$langInstruction $question")
                    }
                )
            }
            response.text?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Could not analyze image."
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Vision analysis timed out after 60s.", e)
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun planWorkflow(goal: String): AiWorkflowPlan {
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                plannerModel.generateContent(
                    "Convert this user goal into a short summary and 1-5 simple Android voice commands " +
                        "(like 'open youtube', 'set alarm 7 am', 'battery status', 'take note ...'): $goal"
                )
            }
            val jsonStr = response.text ?: throw IllegalStateException("Empty workflow response")
            val obj = JSONObject(jsonStr)
            val summary = obj.optString("summary", "Workflow ready")
            val arr = obj.optJSONArray("steps")
            val steps = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i))
                    }
                }
            }.filter { it.isNotBlank() }
            AiWorkflowPlan(summary, steps)
        } catch (e: CancellationException) {
            throw e
        }
    }

    fun clearSession() {
        conversationHistory.clear()
    }

    private fun buildSystemPrompt(
        language: NovaLanguage,
        profile: NovaProfile,
        memories: List<NovaMemoryItem>
    ): String = buildString {
        appendLine("You are NOVA, an intelligent Android voice assistant.")
        appendLine("Active profile: ${profile.displayName} (${profile.description}).")
        when (language) {
            NovaLanguage.HINDI -> appendLine("Always respond in clear, natural Hindi.")
            NovaLanguage.HINGLISH -> appendLine("Always respond in natural Hinglish (Hindi words in English script + English).")
            NovaLanguage.ENGLISH -> appendLine("Always respond in clear, concise English.")
        }
        appendLine("Keep spoken answers concise, accurate, and helpful.")
        if (memories.isNotEmpty()) {
            appendLine("User-approved memory facts:")
            memories.take(10).forEach { appendLine("- ${it.key}: ${it.value}") }
        }
    }

    private fun scaleBitmapForPayload(bitmap: Bitmap): Bitmap {
        val maxDim = 1024
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    private companion object {
        const val TAG = "NovaAIEngine"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val TIMEOUT_MS = 60_000L
    }
}
