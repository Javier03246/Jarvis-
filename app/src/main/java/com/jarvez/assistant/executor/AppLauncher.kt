package com.jarvez.assistant.executor

import android.content.Context
import android.content.Intent
import com.jarvez.assistant.apps.AppResolver
import com.jarvez.assistant.apps.InstalledApp
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger

class AppLauncher(
    private val context: Context,
    private val appResolver: AppResolver
) {

    fun launchAppByName(name: String): TaskResult {
        val app = appResolver.findApp(name)
            ?: return TaskResult.Failure("App '$name' is not installed on this device.")

        return launchApp(app)
    }

    fun launchApp(app: InstalledApp): TaskResult {
        return try {
            val intent = app.launchIntent ?: context.packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                Logger.i("AppLauncher", "Launched application: ${app.name} (${app.packageName})")
                TaskResult.Success("Opened ${app.name}", data = app.packageName)
            } else {
                TaskResult.Failure("Cannot launch ${app.name}: No launch activity found.")
            }
        } catch (e: Exception) {
            Logger.e("AppLauncher", "Error launching ${app.name}: ${e.message}")
            TaskResult.Failure("Failed to launch ${app.name}: ${e.message}")
        }
    }
}
