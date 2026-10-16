package com.jarvez.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onSpeakingStateChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onSpeakingStateChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }

                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeakingStateChanged(false)
                }
            })
            Logger.i("TtsManager", "TextToSpeech initialized successfully")
        } else {
            Logger.e("TtsManager", "TextToSpeech initialization failed with status: $status")
        }
    }

    fun speak(text: String, languagePreference: String = "auto") {
        if (!isInitialized || tts == null) {
            Logger.w("TtsManager", "TTS not initialized, skipping speak")
            return
        }

        // Configure language
        val targetLocale = when (languagePreference) {
            "ur" -> Locale.forLanguageTag("ur-PK")
            "en" -> Locale.US
            else -> {
                // If contains Urdu script characters, use Urdu locale if available
                if (containsUrduCharacters(text)) {
                    Locale.forLanguageTag("ur-PK")
                } else {
                    Locale.US
                }
            }
        }

        val available = tts?.isLanguageAvailable(targetLocale)
        if (available == TextToSpeech.LANG_AVAILABLE || available == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
            tts?.language = targetLocale
        } else {
            tts?.language = Locale.US
        }

        val utteranceId = "JARVEZ_UTTERANCE_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
            _isSpeaking.value = false
            onSpeakingStateChanged(false)
        }
    }

    fun shutdown() {
        if (isInitialized) {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        }
    }

    private fun containsUrduCharacters(text: String): Boolean {
        // Arabic/Urdu Unicode range: 0600-06FF
        return text.any { it.code in 0x0600..0x06FF }
    }
}
