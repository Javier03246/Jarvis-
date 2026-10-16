package com.jarvez.assistant.command

import java.util.regex.Pattern

object CommandParser {

    private val AFFIRMATIVE_WORDS = setOf(
        "yes", "haan", "ha", "send", "bhejo", "ok", "okay", "sure", "yep",
        "proceed", "bilkul", "theek hai", "bhej do", "yes please", "yes send"
    )

    private val NEGATIVE_WORDS = setOf(
        "no", "nah", "nope", "nahi", "cancel", "stop", "mat bhejo",
        "don't send", "chhor do", "chhoro", "rehn do", "rehne do"
    )

    fun parse(
        normalizedText: String,
        originalText: String,
        context: ExecutionContext? = null
    ): ParsedCommand {
        val text = normalizedText.trim()

        // 1. Check for Pending Confirmation (e.g. WhatsApp message confirmation)
        if (context?.pendingConfirmationCommand != null) {
            if (isNegativeResponse(text)) {
                return ParsedCommand(
                    rawText = originalText,
                    intentType = IntentType.CONFIRMATION_RESPONSE,
                    confirmationType = ConfirmationType.NEGATIVE,
                    confidence = 1.0f
                )
            } else if (isAffirmativeResponse(text)) {
                return ParsedCommand(
                    rawText = originalText,
                    intentType = IntentType.CONFIRMATION_RESPONSE,
                    confirmationType = ConfirmationType.AFFIRMATIVE,
                    confidence = 1.0f
                )
            }
        }

        // 2. Check for System Controls (Torch, Volume, Settings)
        parseSystemControl(text, originalText)?.let { return it }

        // 3. Check for WhatsApp Messaging
        parseWhatsApp(text, originalText)?.let { return it }

        // 4. Check for YouTube Search or Playback
        parseYouTube(text, originalText)?.let { return it }

        // 5. Check for Web / Google Search
        parseWebSearch(text, originalText)?.let { return it }

        // 6. Check for Open Application
        parseOpenApp(text, originalText)?.let { return it }

        // 7. Fallback: Conversational query / AI Chat
        return ParsedCommand(
            rawText = originalText,
            intentType = IntentType.AI_CHAT,
            query = originalText,
            confidence = 0.5f
        )
    }

    private fun isNegativeResponse(text: String): Boolean {
        if (NEGATIVE_WORDS.contains(text)) return true
        return text.startsWith("no ") || text.startsWith("nahi ") || text.startsWith("mat ") ||
                text.contains("cancel") || text.contains("mat bhejo") || text.contains("don't")
    }

    private fun isAffirmativeResponse(text: String): Boolean {
        if (isNegativeResponse(text)) return false
        if (AFFIRMATIVE_WORDS.contains(text)) return true
        return text.startsWith("yes ") || text.startsWith("haan ") || text.startsWith("bhej ") ||
                text == "bhejo" || text.endsWith(" bhejo")
    }

    private fun parseSystemControl(text: String, original: String): ParsedCommand? {
        val action = when {
            text.contains("flashlight on") || text.contains("torch on") ||
                    text.contains("torch jalao") || text.contains("batti on") -> SystemAction.FLASHLIGHT_ON

            text.contains("flashlight off") || text.contains("torch off") ||
                    text.contains("torch band") || text.contains("batti band") -> SystemAction.FLASHLIGHT_OFF

            text.contains("volume up") || text.contains("awaz barhao") ||
                    text.contains("sound up") || text.contains("awaz tez") -> SystemAction.VOLUME_UP

            text.contains("volume down") || text.contains("awaz kam") ||
                    text.contains("sound down") || text.contains("volume slow") -> SystemAction.VOLUME_DOWN

            text.contains("volume mute") || text.contains("mute volume") ||
                    text == "mute" || text.contains("awaz band") -> SystemAction.VOLUME_MUTE

            text == "open settings" || text == "settings kholo" ||
                    text == "settings open karo" -> SystemAction.OPEN_SETTINGS

            text == "open bluetooth" || text == "bluetooth kholo" ||
                    text == "bluetooth settings" -> SystemAction.OPEN_BLUETOOTH

            text == "open wifi" || text == "wifi kholo" ||
                    text == "wifi settings" -> SystemAction.OPEN_WIFI

            else -> null
        }

        return action?.let {
            ParsedCommand(
                rawText = original,
                intentType = IntentType.SYSTEM_CONTROL,
                systemAction = it,
                confidence = 0.95f
            )
        }
    }

    private fun parseWhatsApp(text: String, original: String): ParsedCommand? {
        // Pattern 1a: send whatsapp message to [contact] saying [message]
        val p1WithSaying = Pattern.compile("^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?\\s+to\\s+(.+?)\\s+(?:saying|that|with message)\\s+(.+)$", Pattern.CASE_INSENSITIVE)
        val m1WithSaying = p1WithSaying.matcher(text)
        if (m1WithSaying.find()) {
            val contact = m1WithSaying.group(1)?.trim()
            val msg = m1WithSaying.group(2)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.WHATSAPP_MESSAGE,
                contactName = contact,
                messageBody = msg,
                requiresConfirmation = true,
                confidence = 0.95f
            )
        }

        // Pattern 1b: send whatsapp message to [contact]
        val p1OnlyContact = Pattern.compile("^(?:send\\s+)?(?:a\\s+)?whatsapp(?:\\s+message)?\\s+to\\s+([^\\s]+(?:\\s+[^\\s]+)?)$", Pattern.CASE_INSENSITIVE)
        val m1OnlyContact = p1OnlyContact.matcher(text)
        if (m1OnlyContact.find()) {
            val contact = m1OnlyContact.group(1)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.WHATSAPP_MESSAGE,
                contactName = contact,
                messageBody = null,
                requiresConfirmation = true,
                confidence = 0.9f
            )
        }

        // Pattern 2: [contact] ko whatsapp message bhejo ke [message] / [contact] ko whatsapp karo [message]
        val p2 = Pattern.compile("^(.+?)\\s+ko\\s+whatsapp(?:\\s+message)?\\s+(?:bhejo|karo)(?:\\s+(?:ke|keh|that)\\s+(.*))?$", Pattern.CASE_INSENSITIVE)
        val m2 = p2.matcher(text)
        if (m2.find()) {
            val contact = m2.group(1)?.trim()
            val msg = m2.group(2)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.WHATSAPP_MESSAGE,
                contactName = contact,
                messageBody = msg,
                requiresConfirmation = true,
                confidence = 0.95f
            )
        }

        // Pattern 3: whatsapp [contact] [message]
        if (text.startsWith("whatsapp ") && !text.contains("kholo") && !text.contains("open")) {
            val remainder = text.removePrefix("whatsapp ").trim()
            val parts = remainder.split(Regex("\\s+"), limit = 2)
            val contact = parts.getOrNull(0)
            val msg = parts.getOrNull(1)
            if (!contact.isNullOrBlank()) {
                return ParsedCommand(
                    rawText = original,
                    intentType = IntentType.WHATSAPP_MESSAGE,
                    contactName = contact,
                    messageBody = msg,
                    requiresConfirmation = true,
                    confidence = 0.85f
                )
            }
        }

        return null
    }

    private fun parseYouTube(text: String, original: String): ParsedCommand? {
        // Urdu: youtube par [query] chalao / youtube pe [query] search karo
        val pUrduPlay = Pattern.compile("youtube\\s+(?:par|pe)\\s+(.+?)\\s+chalao", Pattern.CASE_INSENSITIVE)
        val mUrduPlay = pUrduPlay.matcher(text)
        if (mUrduPlay.find()) {
            val query = mUrduPlay.group(1)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_PLAY,
                query = query,
                confidence = 0.95f
            )
        }

        val pUrduSearch = Pattern.compile("youtube\\s+(?:par|pe)\\s+(.+?)\\s+(?:search\\s+karo|dhoondo)", Pattern.CASE_INSENSITIVE)
        val mUrduSearch = pUrduSearch.matcher(text)
        if (mUrduSearch.find()) {
            val query = mUrduSearch.group(1)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_SEARCH,
                query = query,
                confidence = 0.95f
            )
        }

        // English: play [query] on youtube
        val pPlayYt = Pattern.compile("play\\s+(.+?)\\s+(?:on|in)\\s+youtube", Pattern.CASE_INSENSITIVE)
        val mPlayYt = pPlayYt.matcher(text)
        if (mPlayYt.find()) {
            val query = mPlayYt.group(1)?.trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_PLAY,
                query = query,
                confidence = 0.95f
            )
        }

        // English: search [query] on youtube / search youtube for [query]
        val pSearchYt1 = Pattern.compile("search\\s+(?:on\\s+)?youtube\\s+for\\s+(.+)", Pattern.CASE_INSENSITIVE)
        val mSearchYt1 = pSearchYt1.matcher(text)
        if (mSearchYt1.find()) {
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_SEARCH,
                query = mSearchYt1.group(1)?.trim(),
                confidence = 0.95f
            )
        }

        val pSearchYt2 = Pattern.compile("search\\s+(.+?)\\s+(?:on|in)\\s+youtube", Pattern.CASE_INSENSITIVE)
        val mSearchYt2 = pSearchYt2.matcher(text)
        if (mSearchYt2.find()) {
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_SEARCH,
                query = mSearchYt2.group(1)?.trim(),
                confidence = 0.95f
            )
        }

        // General "play [song/query]" defaults to YouTube play
        if (text.startsWith("play ") && text.length > 5) {
            val query = text.removePrefix("play ").trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_PLAY,
                query = query,
                confidence = 0.9f
            )
        }

        // Urdu: [query] chalao
        if (text.endsWith(" chalao") && text.length > 7) {
            val query = text.removeSuffix(" chalao").trim()
            return ParsedCommand(
                rawText = original,
                intentType = IntentType.YOUTUBE_PLAY,
                query = query,
                confidence = 0.9f
            )
        }

        return null
    }

    private fun parseWebSearch(text: String, original: String): ParsedCommand? {
        val patterns = listOf(
            Pattern.compile("search\\s+(?:google|web)\\s+for\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("search\\s+(.+?)\\s+on\\s+(?:google|web|internet)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("google\\s+search\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("google\\s+(?:par|pe)\\s+(?:search\\s+karo\\s+)?(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("search\\s+karo\\s+(.+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("search\\s+for\\s+(.+)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val query = matcher.group(1)?.trim()
                if (!query.isNullOrBlank()) {
                    return ParsedCommand(
                        rawText = original,
                        intentType = IntentType.SEARCH_WEB,
                        query = query,
                        confidence = 0.9f
                    )
                }
            }
        }

        return null
    }

    private fun parseOpenApp(text: String, original: String): ParsedCommand? {
        // English: open [app] / launch [app] / start [app]
        val pEng = Pattern.compile("^(?:open|launch|start)\\s+([a-zA-Z0-9\\s]+)$", Pattern.CASE_INSENSITIVE)
        val mEng = pEng.matcher(text)
        if (mEng.find()) {
            val app = mEng.group(1)?.trim()
            if (!app.isNullOrBlank() && app != "settings" && app != "bluetooth" && app != "wifi") {
                return ParsedCommand(
                    rawText = original,
                    intentType = IntentType.OPEN_APP,
                    targetApp = app,
                    confidence = 0.9f
                )
            }
        }

        // Urdu: [app] kholo / [app] open karo
        val pUrdu = Pattern.compile("^([a-zA-Z0-9\\s]+)\\s+(?:kholo|open\\s+karo)$", Pattern.CASE_INSENSITIVE)
        val mUrdu = pUrdu.matcher(text)
        if (mUrdu.find()) {
            val app = mUrdu.group(1)?.trim()
            if (!app.isNullOrBlank() && app != "settings") {
                return ParsedCommand(
                    rawText = original,
                    intentType = IntentType.OPEN_APP,
                    targetApp = app,
                    confidence = 0.9f
                )
            }
        }

        return null
    }
}
