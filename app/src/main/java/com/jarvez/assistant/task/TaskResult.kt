package com.jarvez.assistant.task

import com.jarvez.assistant.command.ParsedCommand

sealed class TaskResult {
    data class Success(val message: String, val data: Any? = null) : TaskResult()
    data class Failure(val reason: String, val canRetry: Boolean = false) : TaskResult()
    data class RequiresConfirmation(val prompt: String, val pendingCommand: ParsedCommand) : TaskResult()
    data class RequiresPermission(val permission: String, val explanation: String) : TaskResult()
    data class RequiresAccessibility(val message: String) : TaskResult()
    object Cancelled : TaskResult()
}
