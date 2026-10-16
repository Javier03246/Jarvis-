package com.jarvez.assistant.data

import com.jarvez.assistant.command.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConversationRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addMessage(message: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(message)
        _messages.value = current
    }

    fun removePendingConfirmation() {
        val updated = _messages.value.map {
            if (it.isPendingConfirmation) it.copy(isPendingConfirmation = false) else it
        }
        _messages.value = updated
    }

    fun clearHistory() {
        _messages.value = emptyList()
    }

    fun getRecentHistory(limit: Int = 10): List<ChatMessage> {
        return _messages.value.takeLast(limit)
    }
}
