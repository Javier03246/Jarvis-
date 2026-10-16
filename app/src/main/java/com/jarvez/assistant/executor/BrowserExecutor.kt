package com.jarvez.assistant.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class BrowserExecutor(private val context: Context) {

    fun executeSearch(query: String): TaskResult {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val url = "https://www.google.com/search?q=$encoded"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Prefer Chrome if installed
                `package` = "com.android.chrome"
            }

            // Check if Chrome can handle it, otherwise fallback to any browser
            val canHandle = intent.resolveActivity(context.packageManager) != null
            val finalIntent = if (canHandle) {
                intent
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            context.startActivity(finalIntent)
            Logger.i("BrowserExecutor", "Executed Google search for: $query")
            TaskResult.Success("Searching Google for '$query'")
        } catch (e: Exception) {
            Logger.e("BrowserExecutor", "Error opening browser search: ${e.message}")
            TaskResult.Failure("Failed to search web: ${e.message}")
        }
    }
}
