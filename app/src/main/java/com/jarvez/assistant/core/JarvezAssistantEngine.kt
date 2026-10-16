package com.jarvez.assistant.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.jarvez.assistant.accessibility.ForegroundAppTracker
import com.jarvez.assistant.accessibility.JarvezAccessibilityService
import com.jarvez.assistant.ai.AIRepository
import com.jarvez.assistant.apps.AppResolver
import com.jarvez.assistant.apps.ContactResolver
import com.jarvez.assistant.command.AssistantState
import com.jarvez.assistant.command.ChatMessage
import com.jarvez.assistant.command.CommandContextManager
import com.jarvez.assistant.command.CommandNormalizer
import com.jarvez.assistant.command.CommandParser
import com.jarvez.assistant.command.ConfirmationType
import com.jarvez.assistant.command.ExecutionContext
import com.jarvez.assistant.command.IntentType
import com.jarvez.assistant.command.MessageSender
import com.jarvez.assistant.command.ParsedCommand
import com.jarvez.assistant.data.ConversationRepository
import com.jarvez.assistant.data.SecureStorage
import com.jarvez.assistant.data.SettingsRepository
import com.jarvez.assistant.executor.AppLauncher
import com.jarvez.assistant.executor.BrowserExecutor
import com.jarvez.assistant.executor.SystemActionExecutor
import com.jarvez.assistant.executor.TaskExecutor
import com.jarvez.assistant.executor.WhatsAppExecutor
import com.jarvez.assistant.executor.YouTubeExecutor
import com.jarvez.assistant.task.TaskPlanner
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.util.Logger
import com.jarvez.assistant.util.NetworkManager
import com.jarvez.assistant.util.PermissionManager
import com.jarvez.assistant.verification.VerificationManager
import com.jarvez.assistant.voice.SpeechRecognizerManager
import com.jarvez.assistant.voice.TtsManager
import com.jarvez.assistant.voice.VoiceController
import com.jarvez.assistant.voice.WakeWordManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JarvezAssistantEngine private constructor(context: Context) {

    val appContext: Context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Infrastructure & Data Managers
    val permissionManager = PermissionManager(appContext)
    val networkManager = NetworkManager(appContext)
    val secureStorage = SecureStorage(appContext)
    val settingsRepository = SettingsRepository(appContext, secureStorage)
    val conversationRepository = ConversationRepository()
    val contextManager = CommandContextManager()
    val appResolver = AppResolver(appContext)
    val contactResolver = ContactResolver(appContext)
    val foregroundAppTracker = ForegroundAppTracker(appContext)

    // Action Executors
    val appLauncher = AppLauncher(appContext, appResolver)
    val browserExecutor = BrowserExecutor(appContext)
    val youTubeExecutor = YouTubeExecutor(appContext)
    val whatsAppExecutor = WhatsAppExecutor(appContext, contactResolver)
    val systemActionExecutor = SystemActionExecutor(appContext)
    val verificationManager = VerificationManager(appContext)
    val aiRepository = AIRepository(settingsRepository)

    // Planning & Execution
    val taskPlanner = TaskPlanner(
        appLauncher = appLauncher,
        appResolver = appResolver,
        browserExecutor = browserExecutor,
        youTubeExecutor = youTubeExecutor,
        whatsAppExecutor = whatsAppExecutor,
        systemActionExecutor = systemActionExecutor,
        verificationManager = verificationManager,
        aiRepository = aiRepository
    )
    val taskExecutor = TaskExecutor()

    // Reactive State
    private val _assistantState = MutableStateFlow(AssistantState.READY)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _stateStatusText = MutableStateFlow("JARVEZ ONLINE // READY")
    val stateStatusText: StateFlow<String> = _stateStatusText.asStateFlow()

    val soundLevelRms: StateFlow<Float> get() = voiceController.soundLevelRms

    // Listeners for external components (ForegroundService, Overlay, UI)
    var onStateChangedListener: ((AssistantState, String) -> Unit)? = null

    // Single unified VoiceController coordinating all speech recognition & wake-word cycles
    val voiceController: VoiceController = VoiceController(
        context = appContext,
        onCommandRecognized = { text ->
            Logger.i(TAG, "[VOICE_INPUT_RECEIVED] \"$text\" -> Entering unified pipeline")
            processUserCommand(text, isVoice = true)
        },
        onWakeWordDetected = { directCommand ->
            mainHandler.post {
                if (!directCommand.isNullOrBlank()) {
                    Logger.i(TAG, "[WAKE_WORD_DIRECT_COMMAND] \"$directCommand\"")
                    processUserCommand(directCommand, isVoice = true)
                } else {
                    Logger.i(TAG, "[WAKE_WORD_TRIGGERED] Acknowledging wake-word...")
                    val ack = if (settingsRepository.settingsState.value.preferredLanguage == "ur") "جی فرمائیں؟" else "Yes, sir?"
                    voiceController.requestCommandListeningAfterTts(settingsRepository.settingsState.value.preferredLanguage)
                    ttsManager.speak(ack, settingsRepository.settingsState.value.preferredLanguage)
                }
            }
        },
        onStateChanged = { state, statusText ->
            updateState(state, statusText)
        }
    )

    // Legacy wrappers for compatibility with existing UI / references
    val speechRecognizerManager: SpeechRecognizerManager = SpeechRecognizerManager(
        context = appContext,
        onCommandRecognized = { text -> processUserCommand(text, isVoice = true) },
        onListeningStateChanged = {},
        onErrorOccurred = {}
    )

    val wakeWordManager: WakeWordManager = WakeWordManager(appContext) { cmd ->
        if (cmd != null) processUserCommand(cmd, isVoice = true)
    }

    // TTS Voice Synthesizer
    val ttsManager: TtsManager = TtsManager(appContext) { isSpeaking ->
        if (isSpeaking) {
            voiceController.onTtsStarted()
            updateState(AssistantState.SPEAKING, "TRANSMITTING VOICE SYNTHESIS...")
        } else {
            // When TTS finishes speaking:
            val isHandsFree = settingsRepository.settingsState.value.isHandsFreeEnabled
            voiceController.onTtsFinished(isHandsFree)
            if (isHandsFree) {
                updateState(AssistantState.WAKE_LISTENING, "HANDS-FREE ACTIVE // AWAITING WAKE WORD")
            } else {
                updateState(AssistantState.READY, "JARVEZ ONLINE // READY")
            }
        }
    }

    init {
        // Wire accessibility updates into tracker and context
        JarvezAccessibilityService.onPackageChangedListener = { pkg, cls ->
            foregroundAppTracker.updateForegroundApp(pkg, cls)
        }

        // Add greeting if empty
        if (conversationRepository.messages.value.isEmpty()) {
            conversationRepository.addMessage(
                ChatMessage(
                    text = "JARVEZ initialized and standing by. Say 'Hey Jarvez' (or 'جارویز'), tap the microphone for manual listen, or type any command.",
                    sender = MessageSender.JARVEZ
                )
            )
        }
    }

    fun updateState(state: AssistantState, statusText: String) {
        _assistantState.value = state
        _stateStatusText.value = statusText
        onStateChangedListener?.invoke(state, statusText)
    }

    /**
     * Start continuous background hands-free listening
     */
    fun startHandsFree() {
        if (!permissionManager.hasRecordAudioPermission()) {
            updateState(AssistantState.ERROR, "MICROPHONE PERMISSION MISSING")
            return
        }
        ttsManager.stop()
        voiceController.startHandsFree(settingsRepository.settingsState.value.preferredLanguage)
    }

    /**
     * Stop continuous background hands-free listening
     */
    fun stopHandsFree() {
        voiceController.stopHandsFree()
    }

    /**
     * Manual "Listen Now" action triggered by the UI or notification mic button
     */
    fun startManualVoiceListening() {
        if (!permissionManager.hasRecordAudioPermission()) {
            Toast.makeText(appContext, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
            updateState(AssistantState.ERROR, "MICROPHONE PERMISSION MISSING")
            return
        }
        ttsManager.stop()
        voiceController.startManualListening(settingsRepository.settingsState.value.preferredLanguage)
    }

    // Facade for startVoiceListening
    fun startVoiceListening() {
        startManualVoiceListening()
    }

    fun stopVoiceListening() {
        if (settingsRepository.settingsState.value.isHandsFreeEnabled) {
            voiceController.startHandsFree(settingsRepository.settingsState.value.preferredLanguage)
        } else {
            voiceController.stopHandsFree()
        }
    }

    fun startWakeWord() {
        startHandsFree()
    }

    fun stopWakeWord() {
        stopHandsFree()
    }

    /**
     * UNIFIED COMMAND PIPELINE
     * Typed text, voice, wake word, notification action, or floating overlay
     * MUST pass through the exact same processing pipeline:
     *
     * Text -> CommandNormalizer -> CommandParser -> TaskPlanner -> TaskExecutor -> VerificationManager -> Response -> TTS
     */
    fun processUserCommand(
        rawCommandText: String,
        isVoice: Boolean = false,
        onFinished: ((TaskResult?) -> Unit)? = null
    ) {
        val trimmed = rawCommandText.trim()
        if (trimmed.isEmpty()) return

        engineScope.launch {
            // Step 1: Record user message in conversation history
            conversationRepository.addMessage(
                ChatMessage(
                    text = trimmed,
                    sender = MessageSender.USER
                )
            )

            // Step 2: Set Thinking state
            updateState(AssistantState.THINKING, "PARSING COMMAND // ANALYZING INTENT...")

            // Step 3: Command Normalization
            val normalized = CommandNormalizer.normalize(trimmed)
            Logger.i(TAG, "Normalized: \"${normalized.cleaned}\" (Original: \"$trimmed\")")

            // Step 4: Include active foreground package into context
            val currentPkg = JarvezAccessibilityService.currentPackageName.value
                ?: foregroundAppTracker.currentPackageName.value
            val currentApp = foregroundAppTracker.currentAppName.value

            val currentContext = contextManager.getSnapshot().copy(
                activeForegroundPackage = currentPkg,
                activeForegroundAppName = currentApp
            )

            // Step 5: Check if answering pending confirmation (e.g. WhatsApp send)
            val pending = currentContext.pendingConfirmationCommand
            if (pending != null) {
                val parsed = CommandParser.parse(normalized.cleaned, trimmed, currentContext)
                if (parsed.intentType == IntentType.CONFIRMATION_RESPONSE) {
                    contextManager.clearPendingConfirmation()
                    conversationRepository.removePendingConfirmation()

                    if (parsed.confirmationType == ConfirmationType.AFFIRMATIVE) {
                        updateState(AssistantState.EXECUTING, "CONFIRMED // EXECUTING TASK...")
                        executeCommandPlan(pending, isExplicitlyConfirmed = true, onFinished = onFinished)
                    } else {
                        val cancelMsg = "Action cancelled, sir."
                        updateState(AssistantState.READY, "ACTION CANCELLED")
                        conversationRepository.addMessage(
                            ChatMessage(text = cancelMsg, sender = MessageSender.JARVEZ)
                        )
                        ttsManager.speak(cancelMsg, settingsRepository.settingsState.value.preferredLanguage)
                        onFinished?.invoke(TaskResult.Cancelled)
                    }
                    return@launch
                }
            }

            // Step 6: Command Parsing (Offline rule-based + contextual + AI)
            val parsed = CommandParser.parse(normalized.cleaned, trimmed, currentContext)
            contextManager.updateLastIntent(parsed.intentType, parsed.query)

            // Step 7: Task Planning & Execution
            executeCommandPlan(parsed, isExplicitlyConfirmed = false, onFinished = onFinished)
        }
    }

    private suspend fun executeCommandPlan(
        parsed: ParsedCommand,
        isExplicitlyConfirmed: Boolean,
        onFinished: ((TaskResult?) -> Unit)?
    ) {
        val history = conversationRepository.getRecentHistory()
        val plan = taskPlanner.planTask(parsed, history, isExplicitlyConfirmed)

        // WhatsApp Confirmation Guard
        if (plan.requiresConfirmation) {
            val prompt = plan.confirmationPrompt ?: "Confirmation required to proceed, sir."
            contextManager.setPendingConfirmation(plan.command)
            updateState(AssistantState.READY, "AWAITING CONFIRMATION")

            conversationRepository.addMessage(
                ChatMessage(
                    text = prompt,
                    sender = MessageSender.JARVEZ,
                    isPendingConfirmation = true,
                    pendingCommand = plan.command
                )
            )
            ttsManager.speak(prompt, settingsRepository.settingsState.value.preferredLanguage)
            onFinished?.invoke(TaskResult.RequiresConfirmation(prompt, plan.command))
            return
        }

        // Execute Plan
        updateState(AssistantState.EXECUTING, "EXECUTING: ${plan.steps.firstOrNull()?.description ?: "Task in progress..."}")

        val taskResult = taskExecutor.executePlan(plan) { stepDesc ->
            updateState(AssistantState.EXECUTING, "EXECUTING: $stepDesc")
        }

        // Response formulation
        val responseText = when (taskResult) {
            is TaskResult.Success -> {
                taskResult.message
            }
            is TaskResult.Failure -> {
                updateState(AssistantState.ERROR, "TASK FAILED // ERROR")
                "I was unable to complete that action, sir. ${taskResult.reason}"
            }
            is TaskResult.RequiresConfirmation -> {
                updateState(AssistantState.READY, "AWAITING CONFIRMATION")
                taskResult.prompt
            }
            is TaskResult.RequiresPermission -> {
                updateState(AssistantState.ERROR, "PERMISSION REQUIRED")
                "Permission required: ${taskResult.explanation}"
            }
            is TaskResult.RequiresAccessibility -> {
                updateState(AssistantState.ERROR, "ACCESSIBILITY SERVICE REQUIRED")
                taskResult.message
            }
            is TaskResult.Cancelled -> {
                "Task cancelled, sir."
            }
        }

        // Record in conversation history
        conversationRepository.addMessage(
            ChatMessage(
                text = responseText,
                sender = MessageSender.JARVEZ,
                intentType = parsed.intentType
            )
        )

        // Synthesize response via TTS (ttsManager completion automatically re-arms hands-free)
        ttsManager.speak(responseText, settingsRepository.settingsState.value.preferredLanguage)

        onFinished?.invoke(taskResult)
    }

    companion object {
        private const val TAG = "AssistantEngine"

        @Volatile
        private var INSTANCE: JarvezAssistantEngine? = null

        fun getInstance(context: Context): JarvezAssistantEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JarvezAssistantEngine(context).also { INSTANCE = it }
            }
        }
    }
}
