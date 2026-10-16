package com.jarvez.assistant.voice

object WakeWordDetector {

    // Primary wake words in English, Roman Urdu, and Urdu script
    val PRIMARY_WAKE_WORDS = listOf(
        "hey jarvez",
        "hey jarvis",
        "ok jarvez",
        "ok jarvis",
        "suno jarvez",
        "jarvez suno",
        "jarvez",
        "jarvis",
        // Urdu script
        "اے جارویز",
        "ہی جارویز",
        "سنو جارویز",
        "جارویز سنو",
        "جارویز",
        "جارویس"
    )

    data class DetectionResult(
        val isDetected: Boolean,
        val matchedWakeWord: String? = null,
        val directCommand: String? = null
    )

    fun detect(text: String): DetectionResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return DetectionResult(isDetected = false)

        val lower = trimmed.lowercase()

        for (wakeWord in PRIMARY_WAKE_WORDS) {
            val lowerW = wakeWord.lowercase()
            val idx = lower.indexOf(lowerW)
            if (idx != -1) {
                // Wake word detected! Extract command before and after
                val before = trimmed.substring(0, idx).trim()
                val after = trimmed.substring(idx + wakeWord.length).trim()
                val remaining = listOf(before, after)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .trim()

                return DetectionResult(
                    isDetected = true,
                    matchedWakeWord = wakeWord,
                    directCommand = if (remaining.isNotEmpty()) remaining else null
                )
            }
        }

        return DetectionResult(isDetected = false)
    }
}
