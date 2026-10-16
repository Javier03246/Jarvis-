package com.jarvez.assistant.task

import com.jarvez.assistant.command.ParsedCommand

data class TaskPlan(
    val command: ParsedCommand,
    val steps: List<TaskStep>,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)
