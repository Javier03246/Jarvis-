package com.jarvez.assistant.data

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JarvezSettings(
    val isWakeWordEnabled: Boolean = false,
    val isConfirmWhatsAppEnabled: Boolean = true,
    val preferredLanguage: String = "auto", // auto, en, ur
    val isBackgroundServiceActive: Boolean = false,
    val hasCustomApiKey: Boolean = false
)

class SettingsRepository(
    private val context: Context,
    private val secureStorage: SecureStorage
) {
    private val prefs = context.getSharedPreferences("jarvez_settings_prefs", Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<JarvezSettings> = _settingsState.asStateFlow()

    private fun loadSettings(): JarvezSettings {
        val wakeWord = prefs.getBoolean("wake_word_enabled", false)
        val confirmWhatsApp = prefs.getBoolean("confirm_whatsapp_enabled", true)
        val language = prefs.getString("preferred_language", "auto") ?: "auto"
        val bgService = prefs.getBoolean("bg_service_active", false)
        val hasCustomKey = !secureStorage.getDecryptedString(SecureStorage.KEY_GEMINI_API_KEY).isNullOrBlank()

        return JarvezSettings(
            isWakeWordEnabled = wakeWord,
            isConfirmWhatsAppEnabled = confirmWhatsApp,
            preferredLanguage = language,
            isBackgroundServiceActive = bgService,
            hasCustomApiKey = hasCustomKey
        )
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
        _settingsState.value = _settingsState.value.copy(isWakeWordEnabled = enabled)
    }

    fun setConfirmWhatsAppEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("confirm_whatsapp_enabled", enabled).apply()
        _settingsState.value = _settingsState.value.copy(isConfirmWhatsAppEnabled = enabled)
    }

    fun setPreferredLanguage(language: String) {
        prefs.edit().putString("preferred_language", language).apply()
        _settingsState.value = _settingsState.value.copy(preferredLanguage = language)
    }

    fun setBackgroundServiceActive(active: Boolean) {
        prefs.edit().putBoolean("bg_service_active", active).apply()
        _settingsState.value = _settingsState.value.copy(isBackgroundServiceActive = active)
    }

    fun saveApiKey(apiKey: String) {
        secureStorage.saveEncryptedString(SecureStorage.KEY_GEMINI_API_KEY, apiKey.trim())
        _settingsState.value = _settingsState.value.copy(hasCustomApiKey = apiKey.isNotBlank())
    }

    fun getEffectiveApiKey(): String {
        val customKey = secureStorage.getDecryptedString(SecureStorage.KEY_GEMINI_API_KEY)
        if (!customKey.isNullOrBlank()) {
            return customKey
        }
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }
    }

    fun clearCustomApiKey() {
        secureStorage.clearKey(SecureStorage.KEY_GEMINI_API_KEY)
        _settingsState.value = _settingsState.value.copy(hasCustomApiKey = false)
    }
}
