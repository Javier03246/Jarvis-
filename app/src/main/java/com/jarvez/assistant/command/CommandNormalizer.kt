package com.jarvez.assistant.command

import java.util.Locale

object CommandNormalizer {

    private val WAKE_PREFIXES = listOf(
        "hey jarvez", "ok jarvez", "jarvez",
        "hey jarvis", "ok jarvis", "jarvis",
        "hello jarvez", "suno jarvez", "sun jarvez",
        "ai jarvez", "jarvez suno"
    )

    private val FILLER_WORDS = listOf(
        "please", "plz", "bhai", "yaar", "janab", "meherbani",
        "kindly", "could you", "can you", "would you",
        "zara", "ek dafa", "jaldi se", "foran"
    )

    fun normalize(input: String): NormalizedResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return NormalizedResult(cleaned = "", original = input, strippedWakeWord = false)
        }

        var text = trimmed.lowercase(Locale.ROOT)
        var strippedWakeWord = false

        // Strip wake prefixes
        for (prefix in WAKE_PREFIXES) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
                strippedWakeWord = true
                break
            }
        }

        // Clean conversational fillers at the beginning
        var changed = true
        while (changed) {
            changed = false
            for (filler in FILLER_WORDS) {
                if (text.startsWith("$filler ")) {
                    text = text.removePrefix("$filler ").trim()
                    changed = true
                }
            }
        }

        // Standardize common Urdu / Roman Urdu verb variations
        text = text
            .replace(Regex("\\bkhol do\\b"), "kholo")
            .replace(Regex("\\bopen kar do\\b"), "open karo")
            .replace(Regex("\\bopen kar do\\b"), "open karo")
            .replace(Regex("\\bchala do\\b"), "chalao")
            .replace(Regex("\\blaga do\\b"), "chalao")
            .replace(Regex("\\bplay kar do\\b"), "play karo")
            .replace(Regex("\\bbhej do\\b"), "bhejo")
            .replace(Regex("\\bsend kar do\\b"), "send karo")
            .replace(Regex("\\bsearch kar do\\b"), "search karo")
            .replace(Regex("\\bdhoond do\\b"), "dhoondo")
            .replace(Regex("\\bband kar do\\b"), "band karo")
            .replace(Regex("\\bon kar do\\b"), "on karo")
            .replace(Regex("\\bjala do\\b"), "on karo")
            .replace(Regex("\\bbujha do\\b"), "band karo")
            .replace(Regex("\\bbarha do\\b"), "barhao")
            .replace(Regex("\\bkam kar do\\b"), "kam karo")

        return NormalizedResult(
            cleaned = text.trim(),
            original = trimmed,
            strippedWakeWord = strippedWakeWord
        )
    }

    data class NormalizedResult(
        val cleaned: String,
        val original: String,
        val strippedWakeWord: Boolean
    )
}
