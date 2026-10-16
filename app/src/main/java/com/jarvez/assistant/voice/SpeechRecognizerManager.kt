package com.jarvez.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechRecognizerManager(
    private val context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorOccurred: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _soundLevelRms = MutableStateFlow(0f)
    val soundLevelRms: StateFlow<Float> = _soundLevelRms.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            onListeningStateChanged(true)
            Logger.i("SpeechRecognizer", "Ready for speech input")
        }

        override fun onBeginningOfSpeech() {
            Logger.i("SpeechRecognizer", "Speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize dB value (usually -2 to 10) to 0.0f..1.0f for visualizer
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _soundLevelRms.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _isListening.value = false
            onListeningStateChanged(false)
            _soundLevelRms.value = 0f
            Logger.i("SpeechRecognizer", "End of speech")
        }

        override fun onError(error: Int) {
            _isListening.value = false
            onListeningStateChanged(false)
            _soundLevelRms.value = 0f

            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions for recording"
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected within time limit"
                else -> "Speech recognition error ($error)"
            }
            Logger.w("SpeechRecognizer", "Error: $errorMsg")
            if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                onErrorOccurred(errorMsg)
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            onListeningStateChanged(false)
            _soundLevelRms.value = 0f

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
                Logger.i("SpeechRecognizer", "Recognized speech: \"$recognizedText\"")
                onCommandRecognized(recognizedText)
            } else {
                Logger.w("SpeechRecognizer", "No recognized text in results")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                Logger.d("SpeechRecognizer", "Partial result: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening(languagePreference: String = "auto") {
        if (_isListening.value) {
            stopListening()
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorOccurred("Speech recognition service is not available on this device.")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                when (languagePreference) {
                    "ur" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                    }
                    "en" -> {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                    }
                    else -> {
                        // Multilingual / Default
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US,ur-PK")
                    }
                }
            }

            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Logger.e("SpeechRecognizer", "Failed to start speech recognizer: ${e.message}", e)
            _isListening.value = false
            onListeningStateChanged(false)
            onErrorOccurred("Failed to initialize microphone: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Logger.w("SpeechRecognizer", "Error stopping recognizer: ${e.message}")
        } finally {
            _isListening.value = false
            onListeningStateChanged(false)
            _soundLevelRms.value = 0f
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Logger.w("SpeechRecognizer", "Error destroying speech recognizer: ${e.message}")
        }
    }
}
