package com.jarvez.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvezAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Logger.i(TAG, "JarvezAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()

        if (!pkg.isNullOrBlank()) {
            _currentPackageName.value = pkg
            onPackageChangedListener?.invoke(pkg, cls)
        }
        if (!cls.isNullOrBlank()) {
            _currentClassName.value = cls
        }
    }

    override fun onInterrupt() {
        Logger.w(TAG, "JarvezAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _isServiceActive.value = false
        Logger.i(TAG, "JarvezAccessibilityService destroyed")
    }

    fun getActiveRootNode(): AccessibilityNodeInfo? {
        return try {
            rootInActiveWindow
        } catch (e: Exception) {
            Logger.e(TAG, "Error fetching rootInActiveWindow: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "AccessibilityService"

        @Volatile
        var instance: JarvezAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentPackageName = MutableStateFlow<String?>(null)
        val currentPackageName: StateFlow<String?> = _currentPackageName.asStateFlow()

        private val _currentClassName = MutableStateFlow<String?>(null)
        val currentClassName: StateFlow<String?> = _currentClassName.asStateFlow()

        var onPackageChangedListener: ((String, String?) -> Unit)? = null

        fun isConnected(): Boolean = instance != null
    }
}
