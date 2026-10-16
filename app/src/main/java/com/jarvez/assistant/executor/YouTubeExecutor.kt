package com.jarvez.assistant.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvez.assistant.accessibility.AccessibilityAutomation
import com.jarvez.assistant.accessibility.JarvezAccessibilityService
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class YouTubeExecutor(private val context: Context) {

    private val youtubePackage = "com.google.android.youtube"

    fun searchYouTube(query: String): TaskResult {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(youtubePackage)
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val canHandle = intent.resolveActivity(context.packageManager) != null
            if (canHandle) {
                context.startActivity(intent)
            } else {
                // Fallback to browser URL
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$encoded")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }

            Logger.i("YouTubeExecutor", "Searching YouTube for: $query")
            TaskResult.Success("Searching YouTube for '$query'")
        } catch (e: Exception) {
            Logger.e("YouTubeExecutor", "Error opening YouTube search: ${e.message}")
            TaskResult.Failure("Failed to search YouTube: ${e.message}")
        }
    }

    suspend fun playOnYouTube(query: String): TaskResult {
        // Step 1: Open YouTube search
        val searchResult = searchYouTube(query)
        if (searchResult !is TaskResult.Success) {
            return searchResult
        }

        // Step 2: If accessibility service is enabled, attempt automated playback
        if (JarvezAccessibilityService.isConnected()) {
            Logger.i("YouTubeExecutor", "Accessibility active. Attempting automatic playback click...")
            // Give YouTube a moment to load search results
            delay(1200)
            val clicked = AccessibilityAutomation.attemptYouTubeVideoPlayback(maxWaitMs = 3500)
            return if (clicked) {
                TaskResult.Success("Playing '$query' on YouTube")
            } else {
                TaskResult.Success("Opened YouTube search for '$query'. Tap any video to play.")
            }
        } else {
            return TaskResult.Success("Opened YouTube search for '$query'. Enable Accessibility in Settings for automatic playback.")
        }
    }
}
