package com.jarvez.assistant.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvez.assistant.accessibility.AccessibilityAutomation
import com.jarvez.assistant.accessibility.JarvezAccessibilityService
import com.jarvez.assistant.apps.ContactResolutionResult
import com.jarvez.assistant.apps.ContactResolver
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.delay
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class WhatsAppExecutor(
    private val context: Context,
    private val contactResolver: ContactResolver
) {

    private val whatsappPackage = "com.whatsapp"

    suspend fun sendMessage(
        contactName: String?,
        messageText: String?,
        autoSendWithAccessibility: Boolean = true
    ): TaskResult {
        if (contactName.isNullOrBlank()) {
            return TaskResult.Failure("Please specify a contact name for WhatsApp.")
        }

        val resolved = contactResolver.resolveContact(contactName)
        when (resolved) {
            is ContactResolutionResult.PermissionRequired -> {
                return TaskResult.RequiresPermission(
                    android.Manifest.permission.READ_CONTACTS,
                    "Contacts permission is needed to resolve '$contactName'."
                )
            }
            is ContactResolutionResult.NotFound -> {
                return TaskResult.Failure("Contact '$contactName' could not be found in your contacts.")
            }
            is ContactResolutionResult.Multiple -> {
                val names = resolved.contacts.take(3).joinToString(", ") { "${it.first} (${it.second})" }
                return TaskResult.Failure("Multiple contacts found for '$contactName': $names. Please be more specific.")
            }
            is ContactResolutionResult.Found -> {
                val phone = resolved.phoneNumber
                val body = messageText ?: ""
                return openAndSend(phone, resolved.name, body, autoSendWithAccessibility)
            }
        }
    }

    private suspend fun openAndSend(
        phoneNumber: String,
        displayName: String,
        message: String,
        autoSend: Boolean
    ): TaskResult {
        return try {
            val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8.toString())
            // WhatsApp direct URI
            val cleanPhone = phoneNumber.replace("+", "")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedMessage")

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(whatsappPackage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            val canHandle = intent.resolveActivity(context.packageManager) != null
            if (!canHandle) {
                // Fallback without package restriction or web
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } else {
                context.startActivity(intent)
            }

            Logger.i("WhatsAppExecutor", "Opened WhatsApp conversation for $displayName ($phoneNumber)")

            if (autoSend && JarvezAccessibilityService.isConnected() && message.isNotBlank()) {
                Logger.i("WhatsAppExecutor", "Accessibility active. Attempting to click Send button...")
                delay(1200)
                val sent = AccessibilityAutomation.attemptWhatsAppSend(maxWaitMs = 3000)
                if (sent) {
                    TaskResult.Success("WhatsApp message sent to $displayName.")
                } else {
                    TaskResult.Success("WhatsApp chat opened with message ready for $displayName. Tap send to deliver.")
                }
            } else {
                TaskResult.Success("WhatsApp chat opened with message ready for $displayName.")
            }
        } catch (e: Exception) {
            Logger.e("WhatsAppExecutor", "Error opening WhatsApp: ${e.message}")
            TaskResult.Failure("Failed to open WhatsApp: ${e.message}")
        }
    }
}
