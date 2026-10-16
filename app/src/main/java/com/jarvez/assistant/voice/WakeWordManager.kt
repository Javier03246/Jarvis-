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
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var listeningJob: Job? = null

    private val _isWakeWordActive = MutableStateFlow(false)
    val isWakeWordActive: StateFlow<Boolean> = _isWakeWordActive.asStateFlow()

    private var speechRecognizerManager: SpeechRecognizerManager? = null

    fun startWakeWordListening() {
        if (_isWakeWordActive.value) return
        _isWakeWordActive.value = true

        speechRecognizerManager = SpeechRecognizerManager(
            context = context,
            onCommandRecognized = { recognizedText ->
                val lower = recognizedText.lowercase()
                if (lower.contains("jarvez") || lower.contains("jarvis") || lower.contains("hey jarvez")) {
                    Logger.i("WakeWordManager", "Wake word recognized: $recognizedText")
                    onWakeWordDetected()
                } else {
                    // Schedule next wake-word listening loop
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
