package com.jarvez.assistant.command

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommandContextManager {

    private val _contextState = MutableStateFlow(ExecutionContext())
    val contextState: StateFlow<ExecutionContext> = _contextState.asStateFlow()

    fun setPendingConfirmation(command: ParsedCommand) {
        _contextState.value = _contextState.value.copy(
            pendingConfirmationCommand = command
        )
    }

    fun clearPendingConfirmation(): ParsedCommand? {
        val pending = _contextState.value.pendingConfirmationCommand
        _contextState.value = _contextState.value.copy(
            pendingConfirmationCommand = null
        )
        return pending
    }

    fun updateLastIntent(intent: IntentType, query: String? = null) {
        _contextState.value = _contextState.value.copy(
            lastIntent = intent,
            activeQuery = query
        )
    }

    fun updateForegroundApp(packageName: String?, appName: String?) {
        _contextState.value = _contextState.value.copy(
            activeForegroundPackage = packageName,
            activeForegroundAppName = appName
        )
    }

    fun getSnapshot(): ExecutionContext {
        return _contextState.value
    }
}
