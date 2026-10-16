package com.jarvez.assistant.executor

import com.jarvez.assistant.task.TaskPlan
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class TaskExecutor {

    private val isCancelled = AtomicBoolean(false)

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _currentStepDescription = MutableStateFlow<String?>(null)
    val currentStepDescription: StateFlow<String?> = _currentStepDescription.asStateFlow()

    fun cancelActiveTask() {
        isCancelled.set(true)
        _isExecuting.value = false
        _currentStepDescription.value = null
        Logger.i("TaskExecutor", "Active task cancellation requested")
    }

    suspend fun executePlan(
        plan: TaskPlan,
        onStepStarted: ((String) -> Unit)? = null
    ): TaskResult {
        isCancelled.set(false)
        _isExecuting.value = true

        if (plan.requiresConfirmation) {
            _isExecuting.value = false
            return TaskResult.RequiresConfirmation(
                prompt = plan.confirmationPrompt ?: "Do you wish to proceed with this action, sir?",
                pendingCommand = plan.command
            )
        }

        try {
            var finalResult: TaskResult = TaskResult.Success("Task completed successfully")

            for ((index, step) in plan.steps.withIndex()) {
                if (isCancelled.get()) {
                    Logger.i("TaskExecutor", "Task cancelled before step: ${step.id}")
                    return TaskResult.Cancelled
                }

                _currentStepDescription.value = step.description
                onStepStarted?.invoke(step.description)
                Logger.i("TaskExecutor", "Executing step ${index + 1}/${plan.steps.size}: ${step.description}")

                var stepResult = step.execute()

                // Retry once if requested
                if (stepResult is TaskResult.Failure && stepResult.canRetry && !isCancelled.get()) {
                    Logger.w("TaskExecutor", "Step failed, attempting retry: ${step.description}")
                    stepResult = step.execute()
                }

                if (stepResult !is TaskResult.Success) {
                    finalResult = stepResult
                    break
                } else {
                    finalResult = stepResult
                }
            }

            return finalResult
        } catch (e: CancellationException) {
            Logger.i("TaskExecutor", "Task cancelled by coroutine scope")
            return TaskResult.Cancelled
        } catch (e: Exception) {
            Logger.e("TaskExecutor", "Task execution error: ${e.message}", e)
            return TaskResult.Failure("Execution error: ${e.message}")
        } finally {
            _isExecuting.value = false
            _currentStepDescription.value = null
        }
    }
}
