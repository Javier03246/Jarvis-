package com.jarvez.assistant.task

import com.jarvez.assistant.ai.AIRepository
import com.jarvez.assistant.ai.AIResult
import com.jarvez.assistant.apps.AppResolver
import com.jarvez.assistant.command.ChatMessage
import com.jarvez.assistant.command.IntentType
import com.jarvez.assistant.command.ParsedCommand
import com.jarvez.assistant.executor.AppLauncher
import com.jarvez.assistant.executor.BrowserExecutor
import com.jarvez.assistant.executor.SystemActionExecutor
import com.jarvez.assistant.executor.WhatsAppExecutor
import com.jarvez.assistant.executor.YouTubeExecutor
import com.jarvez.assistant.verification.VerificationManager
import com.jarvez.assistant.verification.VerificationResult

class TaskPlanner(
    private val appLauncher: AppLauncher,
    private val appResolver: AppResolver,
    private val browserExecutor: BrowserExecutor,
    private val youTubeExecutor: YouTubeExecutor,
    private val whatsAppExecutor: WhatsAppExecutor,
    private val systemActionExecutor: SystemActionExecutor,
    private val verificationManager: VerificationManager,
    private val aiRepository: AIRepository
) {

    fun planTask(
        command: ParsedCommand,
        conversationHistory: List<ChatMessage> = emptyList(),
        isExplicitlyConfirmed: Boolean = false
    ): TaskPlan {
        // WhatsApp confirmation check
        if (command.intentType == IntentType.WHATSAPP_MESSAGE && command.requiresConfirmation && !isExplicitlyConfirmed) {
            val contact = command.contactName ?: "contact"
            val body = command.messageBody ?: ""
            val prompt = if (body.isNotBlank()) {
                "Do you want to send this WhatsApp message to $contact: \"$body\"?"
            } else {
                "Do you want to open WhatsApp chat for $contact?"
            }
            return TaskPlan(
                command = command,
                steps = emptyList(),
                requiresConfirmation = true,
                confirmationPrompt = prompt
            )
        }

        val steps = mutableListOf<TaskStep>()

        when (command.intentType) {
            IntentType.OPEN_APP -> {
                val appName = command.targetApp ?: "app"
                steps.add(
                    TaskStep(
                        id = "launch_app",
                        description = "Launching $appName"
                    ) {
                        val launchResult = appLauncher.launchAppByName(appName)
                        if (launchResult is TaskResult.Success) {
                            val pkg = launchResult.data as? String
                            if (pkg != null) {
                                val verified = verificationManager.verifyAppInForeground(pkg)
                                if (verified is VerificationResult.Unverified) {
                                    return@TaskStep TaskResult.Failure(verified.reason)
                                }
                            }
                        }
                        launchResult
                    }
                )
            }

            IntentType.YOUTUBE_PLAY -> {
                val query = command.query ?: "trending music"
                steps.add(
                    TaskStep(
                        id = "play_youtube",
                        description = "Playing '$query' on YouTube"
                    ) {
                        youTubeExecutor.playOnYouTube(query)
                    }
                )
            }

            IntentType.YOUTUBE_SEARCH -> {
                val query = command.query ?: ""
                steps.add(
                    TaskStep(
                        id = "search_youtube",
                        description = "Searching YouTube for '$query'"
                    ) {
                        youTubeExecutor.searchYouTube(query)
                    }
                )
            }

            IntentType.SEARCH_WEB -> {
                val query = command.query ?: ""
                steps.add(
                    TaskStep(
                        id = "search_web",
                        description = "Searching Google for '$query'"
                    ) {
                        browserExecutor.executeSearch(query)
                    }
                )
            }

            IntentType.WHATSAPP_MESSAGE -> {
                steps.add(
                    TaskStep(
                        id = "send_whatsapp",
                        description = "Opening WhatsApp for ${command.contactName ?: "contact"}"
                    ) {
                        whatsAppExecutor.sendMessage(
                            contactName = command.contactName,
                            messageText = command.messageBody,
                            autoSendWithAccessibility = true
                        )
                    }
                )
            }

            IntentType.SYSTEM_CONTROL -> {
                val action = command.systemAction
                if (action != null) {
                    steps.add(
                        TaskStep(
                            id = "system_action",
                            description = "Executing system command: ${action.name}"
                        ) {
                            systemActionExecutor.execute(action)
                        }
                    )
                } else {
                    steps.add(
                        TaskStep("invalid_action", "Invalid system action") {
                            TaskResult.Failure("Unknown system action.")
                        }
                    )
                }
            }

            IntentType.AI_CHAT -> {
                val query = command.query ?: command.rawText
                steps.add(
                    TaskStep(
                        id = "ai_process",
                        description = "Consulting JARVEZ Core AI"
                    ) {
                        when (val aiResult = aiRepository.processWithAI(query, conversationHistory)) {
                            is AIResult.ConversationalAnswer -> {
                                TaskResult.Success(aiResult.answer)
                            }
                            is AIResult.ActionCommand -> {
                                // Execute nested action
                                val subPlan = planTask(aiResult.command, conversationHistory, isExplicitlyConfirmed = true)
                                var lastRes: TaskResult = TaskResult.Success(aiResult.spokenResponse)
                                for (subStep in subPlan.steps) {
                                    lastRes = subStep.execute()
                                    if (lastRes !is TaskResult.Success) break
                                }
                                lastRes
                            }
                            is AIResult.Error -> {
                                TaskResult.Failure(aiResult.message)
                            }
                        }
                    }
                )
            }

            IntentType.CONFIRMATION_RESPONSE -> {
                // Handled in pipeline
            }

            IntentType.UNKNOWN -> {
                steps.add(
                    TaskStep("unknown", "Unknown command") {
                        TaskResult.Failure("I didn't recognize that command, sir. Try asking me to open an app, search YouTube, send a WhatsApp message, or ask anything.")
                    }
                )
            }
        }

        return TaskPlan(command = command, steps = steps)
    }
}
