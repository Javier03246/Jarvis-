package com.jarvez.assistant.ai

import com.jarvez.assistant.command.ChatMessage
import com.jarvez.assistant.command.IntentType
import com.jarvez.assistant.command.MessageSender
import com.jarvez.assistant.command.ParsedCommand
import com.jarvez.assistant.command.SystemAction
import com.jarvez.assistant.data.SettingsRepository
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed class AIResult {
    data class ActionCommand(val command: ParsedCommand, val spokenResponse: String) : AIResult()
    data class ConversationalAnswer(val answer: String) : AIResult()
    data class Error(val message: String) : AIResult()
}

class AIRepository(private val settingsRepository: SettingsRepository) {

    private val systemPrompt = """
        You are JARVEZ, an advanced, highly capable autonomous AI system assistant.
        The user can speak or type in English, Urdu, Roman Urdu, or a mix of languages.
        If the user asks to perform an action on their device (open app, play song/video on YouTube, search YouTube, send WhatsApp message, search web, control flashlight or volume), you MUST respond with a JSON object format:
        {
          "is_action": true,
          "action_type": "OPEN_APP" | "YOUTUBE_PLAY" | "YOUTUBE_SEARCH" | "WHATSAPP_MESSAGE" | "SEARCH_WEB" | "SYSTEM_CONTROL",
          "target_app": "app name if applicable",
          "query": "search query or song title if applicable",
          "contact": "contact name for whatsapp if applicable",
          "message": "message body for whatsapp if applicable",
          "system_action": "FLASHLIGHT_ON" | "FLASHLIGHT_OFF" | "VOLUME_UP" | "VOLUME_DOWN" | "VOLUME_MUTE" | "OPEN_SETTINGS",
          "spoken_response": "Short confirmation in assistant style"
        }
        
        If the user asks a question, seeks advice, or is chatting, respond with natural text as JARVEZ (clear, confident, intelligent, concise).
    """.trimIndent()

    suspend fun testConnection(testKey: String? = null): Result<Long> = withContext(Dispatchers.IO) {
        val key = testKey ?: settingsRepository.getEffectiveApiKey()
        if (key.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key configured."))
        }

        val startTime = System.currentTimeMillis()
        try {
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = "Hello JARVEZ, connection ping.")))
                ),
                generationConfig = GenerationConfig(maxOutputTokens = 64)
            )

            val response = AIClient.apiService.generateContent(
                model = AIConfig.MODEL_NAME,
                apiKey = key,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            if (candidate != null) {
                val latency = System.currentTimeMillis() - startTime
                Result.success(latency)
            } else {
                Result.failure(IllegalStateException("Received empty response candidate from Gemini."))
            }
        } catch (e: Exception) {
            Logger.e("AIRepository", "Connection test failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun processWithAI(
        userPrompt: String,
        history: List<ChatMessage>
    ): AIResult = withContext(Dispatchers.IO) {
        val key = settingsRepository.getEffectiveApiKey()
        if (key.isBlank()) {
            return@withContext AIResult.Error("Gemini API key is missing. Please configure it in Settings or via Secrets.")
        }

        try {
            val contentList = mutableListOf<Content>()

            // Add recent history context (last 4 turns)
            val recentTurns = history.takeLast(4)
            for (msg in recentTurns) {
                val role = if (msg.sender == MessageSender.USER) "user" else "model"
                contentList.add(
                    Content(
                        role = role,
                        parts = listOf(Part(text = msg.text))
                    )
                )
            }

            // Add current query
            contentList.add(
                Content(
                    role = "user",
                    parts = listOf(Part(text = userPrompt))
                )
            )

            val request = GenerateContentRequest(
                contents = contentList,
                systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                generationConfig = GenerationConfig(
                    temperature = 0.3f,
                    maxOutputTokens = 512
                )
            )

            val response = AIClient.apiService.generateContent(
                model = AIConfig.MODEL_NAME,
                apiKey = key,
                request = request
            )

            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?: return@withContext AIResult.Error("No response received from Gemini.")

            // Check if response contains action JSON
            if (text.contains("\"is_action\": true") || text.contains("\"action_type\"")) {
                try {
                    val jsonStart = text.indexOf('{')
                    val jsonEnd = text.lastIndexOf('}')
                    if (jsonStart >= 0 && jsonEnd > jsonStart) {
                        val jsonStr = text.substring(jsonStart, jsonEnd + 1)
                        val json = JSONObject(jsonStr)

                        val actionTypeStr = json.optString("action_type")
                        val spoken = json.optString("spoken_response", "Executing request, sir.")

                        val parsed = when (actionTypeStr) {
                            "OPEN_APP" -> ParsedCommand(
                                rawText = userPrompt,
                                intentType = IntentType.OPEN_APP,
                                targetApp = json.optString("target_app"),
                                confidence = 0.95f
                            )
                            "YOUTUBE_PLAY" -> ParsedCommand(
                                rawText = userPrompt,
                                intentType = IntentType.YOUTUBE_PLAY,
                                query = json.optString("query"),
                                confidence = 0.95f
                            )
                            "YOUTUBE_SEARCH" -> ParsedCommand(
                                rawText = userPrompt,
                                intentType = IntentType.YOUTUBE_SEARCH,
                                query = json.optString("query"),
                                confidence = 0.95f
                            )
                            "WHATSAPP_MESSAGE" -> ParsedCommand(
                                rawText = userPrompt,
                                intentType = IntentType.WHATSAPP_MESSAGE,
                                contactName = json.optString("contact"),
                                messageBody = json.optString("message"),
                                requiresConfirmation = settingsRepository.settingsState.value.isConfirmWhatsAppEnabled,
                                confidence = 0.95f
                            )
                            "SEARCH_WEB" -> ParsedCommand(
                                rawText = userPrompt,
                                intentType = IntentType.SEARCH_WEB,
                                query = json.optString("query"),
                                confidence = 0.95f
                            )
                            "SYSTEM_CONTROL" -> {
                                val sysActionStr = json.optString("system_action")
                                val sysAction = try {
                                    SystemAction.valueOf(sysActionStr)
                                } catch (e: Exception) {
                                    SystemAction.OPEN_SETTINGS
                                }
                                ParsedCommand(
                                    rawText = userPrompt,
                                    intentType = IntentType.SYSTEM_CONTROL,
                                    systemAction = sysAction,
                                    confidence = 0.95f
                                )
                            }
                            else -> null
                        }

                        if (parsed != null) {
                            return@withContext AIResult.ActionCommand(parsed, spoken)
                        }
                    }
                } catch (e: Exception) {
                    Logger.w("AIRepository", "Error parsing AI action JSON: ${e.message}")
                }
            }

            AIResult.ConversationalAnswer(text)
        } catch (e: Exception) {
            Logger.e("AIRepository", "Gemini API error: ${e.message}", e)
            AIResult.Error("JARVEZ AI connection failed: ${e.message}")
        }
    }
}
