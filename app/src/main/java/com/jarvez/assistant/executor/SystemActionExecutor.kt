package com.jarvez.assistant.executor

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import com.jarvez.assistant.command.SystemAction
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger

class SystemActionExecutor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun execute(action: SystemAction): TaskResult {
        return when (action) {
            SystemAction.FLASHLIGHT_ON -> setTorchMode(true)
            SystemAction.FLASHLIGHT_OFF -> setTorchMode(false)
            SystemAction.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE, "Volume increased")
            SystemAction.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER, "Volume decreased")
            SystemAction.VOLUME_MUTE -> muteVolume()
            SystemAction.OPEN_SETTINGS -> launchSettingIntent(Settings.ACTION_SETTINGS, "Settings")
            SystemAction.OPEN_BLUETOOTH -> launchSettingIntent(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
            SystemAction.OPEN_WIFI -> launchSettingIntent(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
            SystemAction.NAV_BACK -> performAccessibilityGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "Going back")
            SystemAction.NAV_HOME -> performAccessibilityGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "Navigating to Home")
            SystemAction.NAV_RECENTS -> performAccessibilityGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS, "Opening Recent Apps")
            SystemAction.OPEN_NOTIFICATIONS -> performAccessibilityGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Opening Notifications")
            SystemAction.TAKE_SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performAccessibilityGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Capturing screenshot")
                } else {
                    TaskResult.Failure("Screenshots via Accessibility require Android 9 or higher.")
                }
            }
            SystemAction.MEDIA_PLAY_PAUSE -> toggleMediaPlayPause()
        }
    }

    private fun performAccessibilityGlobal(globalAction: Int, successMessage: String): TaskResult {
        val service = com.jarvez.assistant.accessibility.JarvezAccessibilityService.instance
        if (service != null) {
            val performed = service.performGlobalAction(globalAction)
            return if (performed) {
                TaskResult.Success(successMessage)
            } else {
                TaskResult.Failure("System action could not be performed.")
            }
        }
        return TaskResult.RequiresAccessibility("Accessibility Service is required to perform system navigation actions.")
    }

    private fun toggleMediaPlayPause(): TaskResult {
        return try {
            val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            audioManager.dispatchMediaKeyEvent(eventDown)
            audioManager.dispatchMediaKeyEvent(eventUp)
            TaskResult.Success("Toggled media playback")
        } catch (e: Exception) {
            Logger.e("SystemActionExecutor", "Media toggle error: ${e.message}")
            TaskResult.Failure("Could not toggle media: ${e.message}")
        }
    }

    private fun setTorchMode(enabled: Boolean): TaskResult {
        return try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                        chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()

            if (cameraId == null) {
                return TaskResult.Failure("No camera flash available on this device.")
            }

            cameraManager.setTorchMode(cameraId, enabled)
            val msg = if (enabled) "Flashlight turned on" else "Flashlight turned off"
            Logger.i("SystemActionExecutor", msg)
            TaskResult.Success(msg)
        } catch (e: Exception) {
            Logger.e("SystemActionExecutor", "Torch error: ${e.message}")
            TaskResult.Failure("Could not control flashlight: ${e.message}")
        }
    }

    private fun adjustVolume(direction: Int, message: String): TaskResult {
        return try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            TaskResult.Success(message)
        } catch (e: Exception) {
            Logger.e("SystemActionExecutor", "Volume error: ${e.message}")
            TaskResult.Failure("Failed to adjust volume: ${e.message}")
        }
    }

    private fun muteVolume(): TaskResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true)
            }
            TaskResult.Success("Volume muted")
        } catch (e: Exception) {
            Logger.e("SystemActionExecutor", "Mute error: ${e.message}")
            TaskResult.Failure("Failed to mute volume: ${e.message}")
        }
    }

    private fun launchSettingIntent(action: String, name: String): TaskResult {
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            TaskResult.Success("Opened $name")
        } catch (e: Exception) {
            Logger.e("SystemActionExecutor", "Settings error: ${e.message}")
            TaskResult.Failure("Could not open $name: ${e.message}")
        }
    }
}
