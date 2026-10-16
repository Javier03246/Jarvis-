package com.jarvez.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.jarvez.assistant.command.AssistantState
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceController(
    context: Context,
    private val onCommandRecognized: (String) -> Unit,
    private val onWakeWordDetected: (directCommand: String?) -> Unit,
    private val onStateChanged: (AssistantState, String) -> Unit
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main)

    private var speechRecognizer: SpeechRecognizer? = null
    private var scheduledListenJob: Job? = null

    enum class ListeningMode {
        IDLE,
        WAKE_WORD_LISTENING,
        COMMAND_LISTENING
    }

    private var currentMode = ListeningMode.IDLE
    private var isHandsFreeActive = false
    private var isTtsSpeaking = false
    private var pendingCommandListenAfterTts = false
    private var consecutiveErrors = 0
    private var currentLanguage = "auto"

    private val _soundLevelRms = MutableStateFlow(0f)
    val soundLevelRms: StateFlow<Float> = _soundLevelRms.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _isListening.value = true
            consecutiveErrors = 0
            if (currentMode == ListeningMode.COMMAND_LISTENING) {
                onStateChanged(AssistantState.LISTENING, "AUDIO SENSORS ACTIVE // LISTENING...")
            } else if (currentMode == ListeningMode.WAKE_WORD_LISTENING) {
                onStateChanged(AssistantState.WAKE_LISTENING, "HANDS-FREE ACTIVE // AWAITING WAKE WORD")
            }
            Logger.i(TAG, "SpeechRecognizer onReadyForSpeech [Mode: $currentMode]")
        }

        override fun onBeginningOfSpeech() {
            Logger.i(TAG, "Beginning of speech detected")
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _soundLevelRms.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            _soundLevelRms.value = 0f
            Logger.i(TAG, "End of speech detected")
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _soundLevelRms.value = 0f

            val isNormalTimeout = (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Speech recognition error ($error)"
            }

            Logger.d(TAG, "SpeechRecognizer onError: $errorMsg ($error) [Mode: $currentMode]")

            if (!isNormalTimeout) {
                consecutiveErrors++
            } else {
                consecutiveErrors = 0
            }

            // Recycle recognizer instance on error to prevent frozen state
            cleanupRecognizer()

            // If we are in hands-free mode and not speaking, re-arm with controlled backoff
            if (isHandsFreeActive && !isTtsSpeaking) {
                val delayTime = if (isNormalTimeout) {
                    400L
                } else {
                    (600L * consecutiveErrors.coerceAtMost(5)).coerceAtLeast(800L)
                }
                scheduleListen(delayTime, ListeningMode.WAKE_WORD_LISTENING)
            } else if (currentMode == ListeningMode.COMMAND_LISTENING) {
                currentMode = ListeningMode.IDLE
                onStateChanged(AssistantState.READY, "JARVEZ ONLINE // READY")
            }
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _soundLevelRms.value = 0f
            consecutiveErrors = 0

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim()

            Logger.i(TAG, "SpeechRecognizer onResults: \"$text\" [Mode: $currentMode]")
            cleanupRecognizer()

            if (text.isNullOrBlank()) {
                if (isHandsFreeActive && !isTtsSpeaking) {
                    scheduleListen(400L, ListeningMode.WAKE_WORD_LISTENING)
                }
                return
            }

            when (currentMode) {
                ListeningMode.WAKE_WORD_LISTENING -> {
                    val detection = WakeWordDetector.detect(text)
                    if (detection.isDetected) {
                        Logger.i(TAG, "Wake word matched: \"${detection.matchedWakeWord}\", directCommand: \"${detection.directCommand}\"")
                        currentMode = ListeningMode.IDLE
                        if (!detection.directCommand.isNullOrBlank()) {
                            // User spoke wake-word and command in a single breath!
                            onCommandRecognized(detection.directCommand)
                        } else {
                            // User only spoke wake-word. Notify to acknowledge and capture command
                            onWakeWordDetected(null)
                        }
                    } else {
                        // User spoke words, but not the wake word. Resume wake-word listening loop
                        if (isHandsFreeActive && !isTtsSpeaking) {
                            scheduleListen(400L, ListeningMode.WAKE_WORD_LISTENING)
                        }
                    }
                }

                ListeningMode.COMMAND_LISTENING -> {
                    currentMode = ListeningMode.IDLE
                    onCommandRecognized(text)
                }

                ListeningMode.IDLE -> {
                    // Do nothing
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                Logger.d(TAG, "Partial speech: $partial")
                // Fast wake-word check on partial results for snappy response
                if (currentMode == ListeningMode.WAKE_WORD_LISTENING) {
                    val detection = WakeWordDetector.detect(partial)
                    if (detection.isDetected && !detection.directCommand.isNullOrBlank()) {
                        Logger.i(TAG, "Fast partial wake word detected! ${detection.directCommand}")
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Start continuous background hands-free listening
     */
    fun startHandsFree(language: String = "auto") {
        isHandsFreeActive = true
        pendingCommandListenAfterTts = false
        currentLanguage = language
        cancelScheduled()
        scheduleListen(100L, ListeningMode.WAKE_WORD_LISTENING)
    }

    /**
     * Stop continuous background hands-free listening
     */
    fun stopHandsFree() {
        isHandsFreeActive = false
        pendingCommandListenAfterTts = false
        cancelScheduled()
        cleanupRecognizer()
        currentMode = ListeningMode.IDLE
        _isListening.value = false
        _soundLevelRms.value = 0f
        onStateChanged(AssistantState.READY, "JARVEZ ONLINE // READY")
    }

    /**
     * Manual "Listen Now" action triggered by the mic button
     */
    fun startManualListening(language: String = "auto") {
        pendingCommandListenAfterTts = false
        currentLanguage = language
        cancelScheduled()
        cleanupRecognizer()
        currentMode = ListeningMode.COMMAND_LISTENING
        startRecognitionInternal(ListeningMode.COMMAND_LISTENING)
    }

    /**
     * Switch into dedicated command capture (e.g. after "Hey Jarvez" wake word is triggered)
     */
    fun startCommandListening(language: String = "auto") {
        pendingCommandListenAfterTts = false
        currentLanguage = language
        cancelScheduled()
        cleanupRecognizer()
        currentMode = ListeningMode.COMMAND_LISTENING
        startRecognitionInternal(ListeningMode.COMMAND_LISTENING)
    }

    /**
     * Flag that command recognition should start as soon as TTS voice playback finishes
     */
    fun requestCommandListeningAfterTts(language: String = "auto") {
        currentLanguage = language
        pendingCommandListenAfterTts = true
        cancelScheduled()
        cleanupRecognizer()
        currentMode = ListeningMode.COMMAND_LISTENING
    }

    /**
     * Temporarily pause microphone during TTS voice playback to prevent feedback loop
     */
    fun onTtsStarted() {
        isTtsSpeaking = true
        cancelScheduled()
        cleanupRecognizer()
        _isListening.value = false
        _soundLevelRms.value = 0f
    }

    /**
     * Called when TTS voice playback completes.
     * Automatically transitions to command recognition if wake-word requested it,
     * or returns to WAKE_WORD_LISTENING if hands-free is enabled!
     */
    fun onTtsFinished(isHandsFreeEnabled: Boolean) {
        isTtsSpeaking = false
        if (pendingCommandListenAfterTts) {
            pendingCommandListenAfterTts = false
            startCommandListening(currentLanguage)
        } else if (isHandsFreeEnabled) {
            isHandsFreeActive = true
            scheduleListen(400L, ListeningMode.WAKE_WORD_LISTENING)
        } else {
            currentMode = ListeningMode.IDLE
            onStateChanged(AssistantState.READY, "JARVEZ ONLINE // READY")
        }
    }

    private fun scheduleListen(delayMs: Long, mode: ListeningMode) {
        cancelScheduled()
        if (isTtsSpeaking) return

        scheduledListenJob = scope.launch {
            delay(delayMs)
            if (isActive && !isTtsSpeaking && (isHandsFreeActive || mode == ListeningMode.COMMAND_LISTENING)) {
                startRecognitionInternal(mode)
            }
        }
    }

    private fun cancelScheduled() {
        scheduledListenJob?.cancel()
        scheduledListenJob = null
    }

    private fun startRecognitionInternal(mode: ListeningMode) {
        mainHandler.post {
            if (isTtsSpeaking) return@post

            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                Logger.e(TAG, "SpeechRecognizer is not available on this device")
                onStateChanged(AssistantState.ERROR, "SPEECH SENSORS NOT AVAILABLE")
                return@post
            }

            try {
                cleanupRecognizer()

                currentMode = mode
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(recognitionListener)
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // Multilingual support: Urdu (Pakistan) and English
                    when (currentLanguage) {
                        "ur" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK,en-US")
                        }
                        "en" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US,ur-PK")
                        }
                        else -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US,ur-PK")
                        }
                    }
                }

                speechRecognizer?.startListening(intent)
                Logger.d(TAG, "Started speech recognition session in mode: $mode")
            } catch (e: Exception) {
                Logger.e(TAG, "Exception starting SpeechRecognizer: ${e.message}", e)
                cleanupRecognizer()
                if (isHandsFreeActive && !isTtsSpeaking) {
                    scheduleListen(1200L, ListeningMode.WAKE_WORD_LISTENING)
                }
            }
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Logger.w(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
        } finally {
            speechRecognizer = null
            _isListening.value = false
        }
    }

    fun destroy() {
        isHandsFreeActive = false
        cancelScheduled()
        cleanupRecognizer()
    }

    companion object {
        private const val TAG = "VoiceController"
    }
}
