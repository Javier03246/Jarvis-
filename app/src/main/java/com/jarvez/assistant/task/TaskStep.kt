package com.jarvez.assistant.task

data class TaskStep(
    val id: String,
    val description: String,
    val execute: suspend () -> TaskResult
)
