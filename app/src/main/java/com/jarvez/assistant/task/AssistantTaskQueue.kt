package com.jarvez.assistant.task

import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AssistantTaskQueue {

    private val mutex = Mutex()
    private var activeJob: Job? = null

    suspend fun <T> enqueueTask(
        taskName: String,
        block: suspend () -> T
    ): T {
        Logger.i("AssistantTaskQueue", "[TASK_QUEUED] Queued: $taskName")
        return mutex.withLock {
            try {
                Logger.i("AssistantTaskQueue", "[TASK_STARTED] Running: $taskName")
                block()
            } catch (e: CancellationException) {
                Logger.i("AssistantTaskQueue", "[TASK_CANCELLED] Cancelled: $taskName")
                throw e
            } catch (e: Exception) {
                Logger.e("AssistantTaskQueue", "[TASK_FAILED] Failed: $taskName with error: ${e.message}")
                throw e
            }
        }
    }

    fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
        Logger.i("AssistantTaskQueue", "[TASK_CANCEL_REQUESTED] All active tasks cancelled")
    }
}
