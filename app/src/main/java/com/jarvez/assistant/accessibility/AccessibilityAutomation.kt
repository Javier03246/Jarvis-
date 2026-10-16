package com.jarvez.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.delay

object AccessibilityAutomation {

    private const val TAG = "Automation"

    fun click(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // Try direct click
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Logger.i(TAG, "Successfully clicked node: ${node.viewIdResourceName ?: node.text ?: node.contentDescription}")
                return true
            }
        }

        // Try parent click
        val clickableParent = AccessibilityNodeFinder.findClickableAncestor(node)
        if (clickableParent != null) {
            val success = clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) {
                Logger.i(TAG, "Successfully clicked parent of node: ${node.viewIdResourceName}")
                return true
            }
        }

        return false
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    suspend fun attemptYouTubeVideoPlayback(maxWaitMs: Long = 3000): Boolean {
        val service = JarvezAccessibilityService.instance ?: return false
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val root = service.getActiveRootNode()
            val videoNode = AccessibilityNodeFinder.findYouTubeVideoToPlay(root)
            if (videoNode != null) {
                val clicked = click(videoNode)
                if (clicked) {
                    Logger.i(TAG, "YouTube video clicked successfully via Accessibility")
                    return true
                }
            }
            delay(400)
        }

        Logger.w(TAG, "Could not find or click YouTube video within $maxWaitMs ms")
        return false
    }

    suspend fun attemptWhatsAppSend(maxWaitMs: Long = 2500): Boolean {
        val service = JarvezAccessibilityService.instance ?: return false
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            val root = service.getActiveRootNode()
            val sendButton = AccessibilityNodeFinder.findWhatsAppSendButton(root)
            if (sendButton != null) {
                val clicked = click(sendButton)
                if (clicked) {
                    Logger.i(TAG, "WhatsApp Send button clicked successfully")
                    return true
                }
            }
            delay(300)
        }

        Logger.w(TAG, "Could not find or click WhatsApp send button within $maxWaitMs ms")
        return false
    }

    fun performBack(): Boolean {
        return JarvezAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false
    }

    fun performHome(): Boolean {
        return JarvezAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false
    }
}
