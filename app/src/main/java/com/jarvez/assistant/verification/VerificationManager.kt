package com.jarvez.assistant.verification

import android.app.ActivityManager
import android.content.Context
import com.jarvez.assistant.accessibility.JarvezAccessibilityService
import com.jarvez.assistant.util.Logger
import kotlinx.coroutines.delay

class VerificationManager(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    suspend fun verifyAppInForeground(
        expectedPackageName: String,
        timeoutMs: Long = 2500,
        intervalMs: Long = 300
    ): VerificationResult {
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check Accessibility current package
            val currentPkg = JarvezAccessibilityService.currentPackageName.value
            if (currentPkg != null && currentPkg.equals(expectedPackageName, ignoreCase = true)) {
                Logger.i("VerificationManager", "Verified $expectedPackageName is in foreground via Accessibility")
                return VerificationResult.Verified("Package $expectedPackageName verified in foreground.")
            }

            // Fallback check running app processes
            try {
                val runningProcesses = activityManager.runningAppProcesses
                val isForeground = runningProcesses?.any {
                    it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                            it.processName.startsWith(expectedPackageName)
                } ?: false

                if (isForeground) {
                    Logger.i("VerificationManager", "Verified $expectedPackageName is in foreground via ActivityManager")
                    return VerificationResult.Verified("Process $expectedPackageName verified active.")
                }
            } catch (e: Exception) {
                Logger.w("VerificationManager", "Error checking process status: ${e.message}")
            }

            delay(intervalMs)
        }

        // Honest verification: if accessibility is not enabled, we cannot definitively guarantee foreground state
        return if (!JarvezAccessibilityService.isConnected()) {
            VerificationResult.Skipped
        } else {
            VerificationResult.Unverified("Could not verify that $expectedPackageName opened within ${timeoutMs}ms.")
        }
    }
}
