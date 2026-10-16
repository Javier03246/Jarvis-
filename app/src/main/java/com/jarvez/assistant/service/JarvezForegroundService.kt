package com.jarvez.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.R
import com.jarvez.assistant.MainActivity
import com.jarvez.assistant.command.AssistantState
import com.jarvez.assistant.core.JarvezAssistantEngine
import com.jarvez.assistant.util.Logger

class JarvezForegroundService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var engine: JarvezAssistantEngine

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        engine = JarvezAssistantEngine.getInstance(this)

        // Real-time notification updates based on assistant engine state
        engine.onStateChangedListener = { state, statusText ->
            updateNotificationForState(state, statusText)
        }

        Logger.i(TAG, "JarvezForegroundService created and attached to JarvezAssistantEngine")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        when (action) {
            ACTION_STOP_SERVICE -> {
                Logger.i(TAG, "Stopping foreground service requested")
                engine.stopHandsFree()
                engine.updateState(AssistantState.DISABLED, "JARVEZ SERVICE STOPPED")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_LISTEN_COMMAND -> {
                Logger.i(TAG, "Manual 'Listen Now' action triggered from notification")
                engine.startManualVoiceListening()
            }

            ACTION_TOGGLE_LISTENING -> {
                if (engine.assistantState.value == AssistantState.LISTENING) {
                    if (engine.settingsRepository.settingsState.value.isHandsFreeEnabled) {
                        engine.startHandsFree()
                    } else {
                        engine.stopHandsFree()
                    }
                } else {
                    engine.startManualVoiceListening()
                }
            }

            ACTION_START_SERVICE -> {
                if (!engine.permissionManager.hasRecordAudioPermission()) {
                    Logger.w(TAG, "Cannot start foreground service with microphone type: RECORD_AUDIO not granted")
                    stopSelf()
                    return START_NOT_STICKY
                }

                val initialTitle = "JARVEZ Background Assistant"
                val initialText = if (engine.settingsRepository.settingsState.value.isHandsFreeEnabled) {
                    "Hands-Free active. Listening for 'Hey Jarvez' / 'جارویز'"
                } else {
                    "Ready. Tap 🎤 Listen Now"
                }

                val notification = buildNotification(initialTitle, initialText)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        } else {
                            0
                        }
                        startForeground(NOTIFICATION_ID, notification, serviceType)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    Logger.i(TAG, "Started in foreground with microphone foreground service type")
                } catch (e: SecurityException) {
                    Logger.e(TAG, "SecurityException starting foreground service: ${e.message}", e)
                    stopSelf()
                    return START_NOT_STICKY
                } catch (e: Exception) {
                    Logger.e(TAG, "Unexpected error starting foreground service: ${e.message}", e)
                    stopSelf()
                    return START_NOT_STICKY
                }

                // Auto-start hands-free wake-word listening if enabled
                if (engine.permissionManager.hasRecordAudioPermission() &&
                    engine.settingsRepository.settingsState.value.isHandsFreeEnabled) {
                    engine.startHandsFree()
                } else {
                    engine.updateState(AssistantState.READY, "JARVEZ ONLINE // READY")
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Logger.i(TAG, "JarvezForegroundService destroyed")
    }

    private fun updateNotificationForState(state: AssistantState, statusText: String) {
        val (title, body) = when (state) {
            AssistantState.DISABLED -> Pair("JARVEZ Offline", "Background service inactive")
            AssistantState.STARTING -> Pair("JARVEZ Starting...", "Initializing voice sensors...")
            AssistantState.READY -> Pair("JARVEZ Ready", "Tap 🎤 Listen Now or open app")
            AssistantState.WAKE_LISTENING -> Pair("JARVEZ Hands-Free: ACTIVE", "Say 'Hey Jarvez' or 'جارویز'")
            AssistantState.LISTENING -> Pair("🎤 JARVEZ Listening...", "Speak your command now...")
            AssistantState.THINKING -> Pair("🧠 JARVEZ Analyzing...", statusText)
            AssistantState.EXECUTING -> Pair("⚙ JARVEZ Executing...", statusText)
            AssistantState.SPEAKING -> Pair("🔊 JARVEZ Responding...", "Transmitting voice response...")
            AssistantState.ERROR -> Pair("⚠️ JARVEZ Notice", statusText)
            AssistantState.STOPPING -> Pair("JARVEZ Stopping...", "Shutting down...")
        }
        val notification = buildNotification(title, body)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        // Tap on notification body: Opens MainActivity
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button 1: 🎤 Listen Now (Manual trigger over any app)
        val speakIntent = Intent(this, JarvezForegroundService::class.java).apply {
            action = ACTION_LISTEN_COMMAND
        }
        val pendingSpeakIntent = PendingIntent.getService(
            this,
            1,
            speakIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button 2: ⏹ Stop Service
        val stopIntent = Intent(this, JarvezForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingContentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "🎤 Listen Now", pendingSpeakIntent)
            .addAction(0, "📱 Open", pendingContentIntent)
            .addAction(0, "⏹ Stop", pendingStopIntent)
            .build()
    }

    companion object {
        const val TAG = "ForegroundService"
        const val CHANNEL_ID = "jarvez_assistant_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.jarvez.assistant.action.START"
        const val ACTION_STOP_SERVICE = "com.jarvez.assistant.action.STOP"
        const val ACTION_LISTEN_COMMAND = "com.jarvez.assistant.action.LISTEN_COMMAND"
        const val ACTION_TOGGLE_LISTENING = "com.jarvez.assistant.action.TOGGLE_LISTENING"

        fun start(context: Context) {
            val pm = com.jarvez.assistant.util.PermissionManager(context)
            if (!pm.hasRecordAudioPermission()) {
                Logger.w(TAG, "Cannot start JarvezForegroundService: RECORD_AUDIO permission not granted yet")
                return
            }
            val intent = Intent(context, JarvezForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to start foreground service: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvezForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun triggerManualListen(context: Context) {
            val intent = Intent(context, JarvezForegroundService::class.java).apply {
                action = ACTION_LISTEN_COMMAND
            }
            context.startService(intent)
        }
    }
}
