package com.jarvez.assistant.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import com.jarvez.assistant.util.Logger
import java.util.LinkedList
import java.util.Queue

object AccessibilityNodeFinder {

    private const val TAG = "NodeFinder"

    fun findNode(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (root == null) return null

        val queue: Queue<AccessibilityNodeInfo> = LinkedList()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.poll() ?: continue

            try {
                if (predicate(node)) {
                    return node
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Error inspecting accessibility node: ${e.message}")
            }
        }

        return null
    }

    fun findNodeByText(root: AccessibilityNodeInfo?, text: String, exact: Boolean = false): AccessibilityNodeInfo? {
        return findNode(root) { node ->
            val nodeText = node.text?.toString() ?: return@findNode false
            if (exact) {
                nodeText.equals(text, ignoreCase = true)
            } else {
                nodeText.contains(text, ignoreCase = true)
            }
        }
    }

    fun findNodeByContentDescription(root: AccessibilityNodeInfo?, desc: String): AccessibilityNodeInfo? {
        return findNode(root) { node ->
            val nodeDesc = node.contentDescription?.toString() ?: return@findNode false
            nodeDesc.contains(desc, ignoreCase = true)
        }
    }

    fun findNodeByViewId(root: AccessibilityNodeInfo?, viewId: String): AccessibilityNodeInfo? {
        return findNode(root) { node ->
            val resName = node.viewIdResourceName ?: return@findNode false
            resName.contains(viewId, ignoreCase = true)
        }
    }

    fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    fun findYouTubeVideoToPlay(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        // 1. Try finding video entry with video ID resource
        val byId = findNode(root) { node ->
            val id = node.viewIdResourceName ?: ""
            (id.contains("video_info_view") || id.contains("thumbnail") || id.contains("linear_layout")) &&
                    (node.isClickable || findClickableAncestor(node) != null)
        }
        if (byId != null) {
            return if (byId.isClickable) byId else findClickableAncestor(byId)
        }

        // 2. Try finding any clickable item with content description containing "minutes" or "seconds" or "views" (standard YouTube video desc)
        val byDesc = findNode(root) { node ->
            val desc = node.contentDescription?.toString() ?: ""
            (desc.contains("views") || desc.contains("ago") || desc.contains("watching") || desc.contains("minutes")) &&
                    (node.isClickable || findClickableAncestor(node) != null)
        }
        if (byDesc != null) {
            return if (byDesc.isClickable) byDesc else findClickableAncestor(byDesc)
        }

        // 3. Fallback: First clickable ViewGroup that isn't search bar or filter chip
        return findNode(root) { node ->
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val id = node.viewIdResourceName?.lowercase() ?: ""

            node.isClickable &&
                    !desc.contains("search") && !desc.contains("filter") && !desc.contains("cast") &&
                    !text.contains("all") && !text.contains("shorts") &&
                    !id.contains("search")
        }
    }

    fun findWhatsAppSendButton(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        // 1. Try by viewId
        val byId = findNodeByViewId(root, "send")
        if (byId != null) {
            return if (byId.isClickable) byId else findClickableAncestor(byId)
        }

        // 2. Try by contentDescription
        val byDesc = findNodeByContentDescription(root, "Send")
        if (byDesc != null) {
            return if (byDesc.isClickable) byDesc else findClickableAncestor(byDesc)
        }

        return null
    }
}
