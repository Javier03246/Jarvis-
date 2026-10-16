package com.jarvez.assistant.command

enum class SystemAction {
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    OPEN_SETTINGS,
    OPEN_BLUETOOTH,
    OPEN_WIFI
}

enum class ConfirmationType {
    AFFIRMATIVE,
    NEGATIVE
}

data class ParsedCommand(
    val rawText: String,
    val intentType: IntentType,
    val targetApp: String? = null,
    val query: String? = null,
    val contactName: String? = null,
    val messageBody: String? = null,
    val systemAction: SystemAction? = null,
    val confirmationType: ConfirmationType? = null,
    val confidence: Float = 1.0f,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

data class ExecutionContext(
    val lastIntent: IntentType? = null,
    val pendingConfirmationCommand: ParsedCommand? = null,
    val activeQuery: String? = null
)

enum class MessageSender {
    USER,
    JARVEZ,
    SYSTEM
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val intentType: IntentType? = null,
    val isPendingConfirmation: Boolean = false,
    val pendingCommand: ParsedCommand? = null
)

enum class AssistantState {
    READY,
    LISTENING,
    THINKING,
    EXECUTING,
    SPEAKING,
    ERROR
}
