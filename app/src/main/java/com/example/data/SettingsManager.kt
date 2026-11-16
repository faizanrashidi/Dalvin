package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.NovaLanguage
import com.example.core.NovaProfile
import com.example.ui.theme.NovaThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.novaDataStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

data class NovaSettingsState(
    val language: NovaLanguage = NovaLanguage.HINGLISH,
    val themeMode: NovaThemeMode = NovaThemeMode.DARK,
    val profile: NovaProfile = NovaProfile.GENERAL,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val autoSpeak: Boolean = true,
    val continuousListening: Boolean = false,
    val micKillSwitch: Boolean = false,
    val memoryEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false,
    val pythonBridgeEndpoint: String = "http://127.0.0.1:5050/nova",
    val safTreeUri: String = "",
    val hasCustomApiKey: Boolean = false,
    val customApiKeyPreview: String = ""
)

class SettingsManager(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PROFILE = stringPreferencesKey("profile")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val TTS_PITCH = floatPreferencesKey("tts_pitch")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val CONTINUOUS_LISTENING = booleanPreferencesKey("continuous_listening")
        val MIC_KILL_SWITCH = booleanPreferencesKey("mic_kill_switch")
        val MEMORY_ENABLED = booleanPreferencesKey("memory_enabled")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val PYTHON_ENDPOINT = stringPreferencesKey("python_endpoint")
        val SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")
        val CUSTOM_KEY_CIPHER = stringPreferencesKey("custom_key_cipher")
        val CUSTOM_KEY_IV = stringPreferencesKey("custom_key_iv")
        val CUSTOM_KEY_PREVIEW = stringPreferencesKey("custom_key_preview")
    }

    val settingsFlow: Flow<NovaSettingsState> = context.novaDataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: NovaLanguage.HINGLISH.code
        val themeStr = prefs[Keys.THEME_MODE] ?: NovaThemeMode.DARK.name
        val profileId = prefs[Keys.PROFILE] ?: NovaProfile.GENERAL.id
        val cipherText = prefs[Keys.CUSTOM_KEY_CIPHER].orEmpty()
        NovaSettingsState(
            language = NovaLanguage.entries.firstOrNull { it.code == langCode } ?: NovaLanguage.HINGLISH,
            themeMode = runCatching { NovaThemeMode.valueOf(themeStr) }.getOrDefault(NovaThemeMode.DARK),
            profile = NovaProfile.entries.firstOrNull { it.id == profileId } ?: NovaProfile.GENERAL,
            ttsRate = prefs[Keys.TTS_RATE] ?: 1.0f,
            ttsPitch = prefs[Keys.TTS_PITCH] ?: 1.0f,
            autoSpeak = prefs[Keys.AUTO_SPEAK] ?: true,
            continuousListening = prefs[Keys.CONTINUOUS_LISTENING] ?: false,
            micKillSwitch = prefs[Keys.MIC_KILL_SWITCH] ?: false,
            memoryEnabled = prefs[Keys.MEMORY_ENABLED] ?: true,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            pythonBridgeEndpoint = prefs[Keys.PYTHON_ENDPOINT] ?: "http://127.0.0.1:5050/nova",
            safTreeUri = prefs[Keys.SAF_TREE_URI].orEmpty(),
            hasCustomApiKey = cipherText.isNotBlank(),
            customApiKeyPreview = prefs[Keys.CUSTOM_KEY_PREVIEW].orEmpty()
        )
    }

    suspend fun setLanguage(language: NovaLanguage) {
        context.novaDataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(mode: NovaThemeMode) {
        context.novaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setProfile(profile: NovaProfile) {
        context.novaDataStore.edit { it[Keys.PROFILE] = profile.id }
    }

    suspend fun setTtsRate(rate: Float) {
        context.novaDataStore.edit { it[Keys.TTS_RATE] = rate.coerceIn(0.6f, 1.8f) }
    }

    suspend fun setTtsPitch(pitch: Float) {
        context.novaDataStore.edit { it[Keys.TTS_PITCH] = pitch.coerceIn(0.6f, 1.6f) }
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setContinuousListening(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.CONTINUOUS_LISTENING] = enabled }
    }

    suspend fun setMicKillSwitch(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.MIC_KILL_SWITCH] = enabled }
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.MEMORY_ENABLED] = enabled }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.novaDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setPythonBridgeEndpoint(endpoint: String) {
        context.novaDataStore.edit { it[Keys.PYTHON_ENDPOINT] = endpoint.trim() }
    }

    suspend fun setSafTreeUri(uri: String) {
        context.novaDataStore.edit { it[Keys.SAF_TREE_URI] = uri }
    }

    suspend fun saveEncryptedCustomApiKey(rawKey: String) {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) {
            clearEncryptedCustomApiKey()
            return
        }
        val (cipherB64, ivB64) = encryptWithKeystore(trimmed)
        val preview = if (trimmed.length > 8) {
            "${trimmed.take(4)}••••${trimmed.takeLast(4)}"
        } else {
            "••••••••"
        }
        context.novaDataStore.edit {
            it[Keys.CUSTOM_KEY_CIPHER] = cipherB64
            it[Keys.CUSTOM_KEY_IV] = ivB64
            it[Keys.CUSTOM_KEY_PREVIEW] = preview
        }
    }

    suspend fun clearEncryptedCustomApiKey() {
        context.novaDataStore.edit {
            it.remove(Keys.CUSTOM_KEY_CIPHER)
            it.remove(Keys.CUSTOM_KEY_IV)
            it.remove(Keys.CUSTOM_KEY_PREVIEW)
        }
    }

    private fun getOrCreateKeystoreSecret(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encryptWithKeystore(plainText: String): Pair<String, String> {
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreSecret())
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val encB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            encB64 to ivB64
        } catch (e: Exception) {
            Log.e(TAG, "Keystore encryption failed, falling back to masked marker", e)
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) to ""
        }
    }

    fun decryptPreviewCheck(cipherB64: String, ivB64: String): Boolean {
        if (cipherB64.isBlank()) return false
        if (ivB64.isBlank()) return true
        return try {
            val cipher = Cipher.getInstance(AES_GCM)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreSecret(), GCMParameterSpec(128, iv))
            cipher.doFinal(Base64.decode(cipherB64, Base64.NO_WRAP)).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "SettingsManager"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nova_voice_ai_aes_key"
        const val AES_GCM = "AES/GCM/NoPadding"
    }
}
