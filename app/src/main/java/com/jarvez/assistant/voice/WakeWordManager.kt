package com.jarvez.assistant.voice

import android.content.Context
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

class WakeWordManager(
    context: Context,
    private val onWakeWordDetected: (String?) -> Unit
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main)
    private var listeningJob: Job? = null

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private var speechRecognizerManager: SpeechRecognizerManager? = null

    fun startWakeWordListening() {
        if (_isWakeWordActive.value) return
        _isWakeWordActive.value = true

        speechRecognizerManager = SpeechRecognizerManager(
            context = appContext,
            onCommandRecognized = { recognizedText ->
                val lower = recognizedText.lowercase().trim()
                if (lower.contains("jarvez") || lower.contains("jarvis")) {
                    Logger.i("WakeWordManager", "Wake word recognized in: \"$recognizedText\"")
                    // Clean out wake word prefix if user said command in one breath
                    val command = extractCommandAfterWakeWord(recognizedText)
                    onWakeWordDetected(command)
                } else {
                    scheduleNextWakeListen()
                }
            },
            onListeningStateChanged = {},
            onErrorOccurred = {
                scheduleNextWakeListen()
            }
        )

        scheduleNextWakeListen()
    }

    private fun extractCommandAfterWakeWord(text: String): String? {
        val lower = text.lowercase()
        val wakeWords = listOf("hey jarvez", "hey jarvis", "ok jarvez", "ok jarvis", "jarvez", "jarvis")
        for (w in wakeWords) {
            val idx = lower.indexOf(w)
            if (idx != -1) {
                val remainder = text.substring(idx + w.length).trim()
                if (remainder.isNotBlank()) {
                    return remainder
                }
            }
        }
        return null
    }

    private fun scheduleNextWakeListen() {
        if (!_isWakeWordActive.value) return

        listeningJob?.cancel()
        listeningJob = scope.launch {
            delay(1000)
            if (isActive && _isWakeWordActive.value) {
                speechRecognizerManager?.startListening()
            }
        }
    }

    fun stopWakeWordListening() {
        _isWakeWordActive.value = false
        listeningJob?.cancel()
        listeningJob = null
        speechRecognizerManager?.stopListening()
        speechRecognizerManager?.destroy()
        speechRecognizerManager = null
    }
}
