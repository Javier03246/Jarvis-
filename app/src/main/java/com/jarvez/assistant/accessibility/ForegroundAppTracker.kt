package com.jarvez.assistant.accessibility

import android.content.Context
import android.content.pm.PackageManager
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForegroundAppTracker(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    private val _currentPackageName = MutableStateFlow<String?>(null)
    val currentPackageName: StateFlow<String?> = _currentPackageName.asStateFlow()

    private val _currentAppName = MutableStateFlow<String?>(null)
    val currentAppName: StateFlow<String?> = _currentAppName.asStateFlow()

    private val _currentClassName = MutableStateFlow<String?>(null)
    val currentClassName: StateFlow<String?> = _currentClassName.asStateFlow()

    fun updateForegroundApp(packageName: String?, className: String?) {
        if (packageName.isNullOrBlank()) return
        val previous = _currentPackageName.value

        if (previous != packageName) {
            _currentPackageName.value = packageName
            val appLabel = try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName
            }
            _currentAppName.value = appLabel
            Logger.i("ForegroundAppTracker", "[CURRENT_APP_CHANGED] New App: $appLabel ($packageName)")
        }

        if (className != null && className != _currentClassName.value) {
            _currentClassName.value = className
        }
    }

    fun isYouTubeActive(): Boolean {
        val pkg = _currentPackageName.value ?: return false
        return pkg.equals("com.google.android.youtube", ignoreCase = true)
    }

    fun isChromeActive(): Boolean {
        val pkg = _currentPackageName.value ?: return false
        return pkg.equals("com.android.chrome", ignoreCase = true) ||
                pkg.contains("chrome", ignoreCase = true) ||
                pkg.contains("browser", ignoreCase = true)
    }

    fun isWhatsAppActive(): Boolean {
        val pkg = _currentPackageName.value ?: return false
        return pkg.equals("com.whatsapp", ignoreCase = true) ||
                pkg.equals("com.whatsapp.w4b", ignoreCase = true)
    }
}
