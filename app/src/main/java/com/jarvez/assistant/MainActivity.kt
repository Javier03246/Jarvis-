package com.jarvez.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JarvezAmber
import com.example.ui.theme.JarvezBg
import com.example.ui.theme.JarvezBlue
import com.example.ui.theme.JarvezBorder
import com.example.ui.theme.JarvezCrimson
import com.example.ui.theme.JarvezCyan
import com.example.ui.theme.JarvezGreen
import com.example.ui.theme.JarvezSubtext
import com.example.ui.theme.JarvezSurface
import com.example.ui.theme.JarvezSurfaceVariant
import com.example.ui.theme.JarvezText
import com.example.ui.theme.JarvezTheme
import com.jarvez.assistant.ai.AIRepository
import com.jarvez.assistant.apps.AppResolver
import com.jarvez.assistant.apps.ContactResolver
import com.jarvez.assistant.command.AssistantState
import com.jarvez.assistant.command.ChatMessage
import com.jarvez.assistant.command.CommandContextManager
import com.jarvez.assistant.command.CommandNormalizer
import com.jarvez.assistant.command.CommandParser
import com.jarvez.assistant.command.ConfirmationType
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
import com.jarvez.assistant.service.JarvezForegroundService
import com.jarvez.assistant.task.TaskPlanner
import com.jarvez.assistant.task.TaskResult
import com.jarvez.assistant.ui.JarvezVisualizer
import com.jarvez.assistant.util.Logger
import com.jarvez.assistant.util.NetworkManager
import com.jarvez.assistant.util.PermissionManager
import com.jarvez.assistant.verification.VerificationManager
import com.jarvez.assistant.voice.SpeechRecognizerManager
import com.jarvez.assistant.voice.TtsManager
import com.jarvez.assistant.voice.WakeWordManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Core Managers & Repositories
    private lateinit var permissionManager: PermissionManager
    private lateinit var networkManager: NetworkManager
    private lateinit var secureStorage: SecureStorage
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var contextManager: CommandContextManager
    private lateinit var appResolver: AppResolver
    private lateinit var contactResolver: ContactResolver
    private lateinit var appLauncher: AppLauncher
    private lateinit var browserExecutor: BrowserExecutor
    private lateinit var youTubeExecutor: YouTubeExecutor
    private lateinit var whatsAppExecutor: WhatsAppExecutor
    private lateinit var systemActionExecutor: SystemActionExecutor
    private lateinit var verificationManager: VerificationManager
    private lateinit var aiRepository: AIRepository
    private lateinit var taskPlanner: TaskPlanner
    private lateinit var taskExecutor: TaskExecutor
    private lateinit var ttsManager: TtsManager
    private lateinit var speechRecognizerManager: SpeechRecognizerManager
    private lateinit var wakeWordManager: WakeWordManager

    // Reactive State
    private val assistantState = MutableStateFlow(AssistantState.READY)
    private val stateStatusText = MutableStateFlow("JARVEZ ONLINE // READY")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize dependencies
        permissionManager = PermissionManager(this)
        networkManager = NetworkManager(this)
        secureStorage = SecureStorage(this)
        settingsRepository = SettingsRepository(this, secureStorage)
        conversationRepository = ConversationRepository()
        contextManager = CommandContextManager()
        appResolver = AppResolver(this)
        contactResolver = ContactResolver(this)
        appLauncher = AppLauncher(this, appResolver)
        browserExecutor = BrowserExecutor(this)
        youTubeExecutor = YouTubeExecutor(this)
        whatsAppExecutor = WhatsAppExecutor(this, contactResolver)
        systemActionExecutor = SystemActionExecutor(this)
        verificationManager = VerificationManager(this)
        aiRepository = AIRepository(settingsRepository)

        taskPlanner = TaskPlanner(
            appLauncher = appLauncher,
            appResolver = appResolver,
            browserExecutor = browserExecutor,
            youTubeExecutor = youTubeExecutor,
            whatsAppExecutor = whatsAppExecutor,
            systemActionExecutor = systemActionExecutor,
            verificationManager = verificationManager,
            aiRepository = aiRepository
        )

        taskExecutor = TaskExecutor()

        // TTS setup
        ttsManager = TtsManager(this) { isSpeaking ->
            if (isSpeaking) {
                assistantState.value = AssistantState.SPEAKING
            } else if (assistantState.value == AssistantState.SPEAKING) {
                assistantState.value = AssistantState.READY
            }
        }

        // Voice Speech Recognizer setup
        speechRecognizerManager = SpeechRecognizerManager(
            context = this,
            onCommandRecognized = { text ->
                // Voice input enters the EXACT SAME pipeline
                processUserCommand(text, isVoice = true)
            },
            onListeningStateChanged = { listening ->
                if (listening) {
                    assistantState.value = AssistantState.LISTENING
                    stateStatusText.value = "AUDIO SENSORS ACTIVE // LISTENING..."
                } else if (assistantState.value == AssistantState.LISTENING) {
                    assistantState.value = AssistantState.READY
                    stateStatusText.value = "JARVEZ ONLINE // READY"
                }
            },
            onErrorOccurred = { error ->
                assistantState.value = AssistantState.ERROR
                stateStatusText.value = "AUDIO ERROR: $error"
            }
        )

        // Wake word setup
        wakeWordManager = WakeWordManager(this) {
            runOnUiThread {
                Toast.makeText(this, "JARVEZ Wake Word Triggered", Toast.LENGTH_SHORT).show()
                startVoiceListening()
            }
        }

        // Add initial system greeting
        conversationRepository.addMessage(
            ChatMessage(
                text = "JARVEZ initialized and standing by. Say 'Hey Jarvez', tap the microphone, or type any command in English, Urdu, or Roman Urdu.",
                sender = MessageSender.JARVEZ
            )
        )

        setContent {
            JarvezTheme {
                JarvezMainScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizerManager.destroy()
        wakeWordManager.stopWakeWordListening()
        ttsManager.shutdown()
    }

    /**
     * UNIFIED COMMAND PIPELINE
     * Typed text and microphone voice input pass through the EXACT SAME PIPELINE:
     * Text -> CommandNormalizer -> CommandParser -> TaskPlanner -> TaskExecutor -> VerificationManager -> Response -> TTS
     */
    fun processUserCommand(rawCommandText: String, isVoice: Boolean = false) {
        val trimmed = rawCommandText.trim()
        if (trimmed.isEmpty()) return

        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
        scope.launch {
            // Step 1: Add user message to conversation history
            conversationRepository.addMessage(
                ChatMessage(
                    text = trimmed,
                    sender = MessageSender.USER
                )
            )

            // Step 2: Set Thinking state
            assistantState.value = AssistantState.THINKING
            stateStatusText.value = "PARSING COMMAND // ANALYZING INTENT..."

            // Step 3: Command Normalization
            val normalized = CommandNormalizer.normalize(trimmed)
            Logger.i("MainActivity", "Normalized: \"${normalized.cleaned}\" (Original: \"$trimmed\")")

            // Step 4: Check if answering pending confirmation
            val currentContext = contextManager.getSnapshot()
            val pending = currentContext.pendingConfirmationCommand

            if (pending != null) {
                val parsed = CommandParser.parse(normalized.cleaned, trimmed, currentContext)
                if (parsed.intentType == IntentType.CONFIRMATION_RESPONSE) {
                    contextManager.clearPendingConfirmation()
                    conversationRepository.removePendingConfirmation()

                    if (parsed.confirmationType == ConfirmationType.AFFIRMATIVE) {
                        stateStatusText.value = "CONFIRMED // EXECUTING TASK..."
                        executeCommandPlan(pending, isExplicitlyConfirmed = true)
                    } else {
                        val cancelMsg = "Action cancelled, sir."
                        stateStatusText.value = "ACTION CANCELLED"
                        assistantState.value = AssistantState.READY
                        conversationRepository.addMessage(
                            ChatMessage(text = cancelMsg, sender = MessageSender.JARVEZ)
                        )
                        ttsManager.speak(cancelMsg, settingsRepository.settingsState.value.preferredLanguage)
                    }
                    return@launch
                }
            }

            // Step 5: Command Parsing (Offline rule-based first, with AI fallback)
            val parsed = CommandParser.parse(normalized.cleaned, trimmed, currentContext)
            contextManager.updateLastIntent(parsed.intentType, parsed.query)

            // Step 6: Task Planning & Execution
            executeCommandPlan(parsed, isExplicitlyConfirmed = false)
        }
    }

    private suspend fun executeCommandPlan(parsed: ParsedCommand, isExplicitlyConfirmed: Boolean) {
        val history = conversationRepository.getRecentHistory()
        val plan = taskPlanner.planTask(parsed, history, isExplicitlyConfirmed)

        // If confirmation is required before proceeding (e.g. WhatsApp message confirmation)
        if (plan.requiresConfirmation) {
            val prompt = plan.confirmationPrompt ?: "Confirmation required to proceed, sir."
            contextManager.setPendingConfirmation(plan.command)
            assistantState.value = AssistantState.READY
            stateStatusText.value = "AWAITING CONFIRMATION"

            conversationRepository.addMessage(
                ChatMessage(
                    text = prompt,
                    sender = MessageSender.JARVEZ,
                    isPendingConfirmation = true,
                    pendingCommand = plan.command
                )
            )
            ttsManager.speak(prompt, settingsRepository.settingsState.value.preferredLanguage)
            return
        }

        // Step 7: Execute Plan
        assistantState.value = AssistantState.EXECUTING
        stateStatusText.value = "EXECUTING: ${plan.steps.firstOrNull()?.description ?: "Task in progress..."}"

        val taskResult = taskExecutor.executePlan(plan) { stepDesc ->
            stateStatusText.value = "EXECUTING: $stepDesc"
        }

        // Step 8: Response Handling and Verification Output
        val responseText = when (taskResult) {
            is TaskResult.Success -> {
                assistantState.value = AssistantState.READY
                stateStatusText.value = "TASK COMPLETED // READY"
                taskResult.message
            }
            is TaskResult.Failure -> {
                assistantState.value = AssistantState.ERROR
                stateStatusText.value = "TASK FAILED // ERROR"
                "I was unable to complete that action, sir. ${taskResult.reason}"
            }
            is TaskResult.RequiresConfirmation -> {
                assistantState.value = AssistantState.READY
                stateStatusText.value = "AWAITING CONFIRMATION"
                taskResult.prompt
            }
            is TaskResult.RequiresPermission -> {
                assistantState.value = AssistantState.ERROR
                stateStatusText.value = "PERMISSION REQUIRED"
                "Permission required: ${taskResult.explanation}"
            }
            is TaskResult.RequiresAccessibility -> {
                assistantState.value = AssistantState.ERROR
                stateStatusText.value = "ACCESSIBILITY SERVICE REQUIRED"
                taskResult.message
            }
            is TaskResult.Cancelled -> {
                assistantState.value = AssistantState.READY
                stateStatusText.value = "TASK CANCELLED"
                "Task cancelled, sir."
            }
        }

        // Step 9: Add Response to transcript and synthesize via TTS
        conversationRepository.addMessage(
            ChatMessage(
                text = responseText,
                sender = MessageSender.JARVEZ,
                intentType = parsed.intentType
            )
        )

        ttsManager.speak(responseText, settingsRepository.settingsState.value.preferredLanguage)
    }

    private fun startVoiceListening() {
        if (!permissionManager.hasRecordAudioPermission()) {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
            return
        }
        ttsManager.stop()
        speechRecognizerManager.startListening(settingsRepository.settingsState.value.preferredLanguage)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun JarvezMainScreen() {
        val state by assistantState.collectAsState()
        val statusText by stateStatusText.collectAsState()
        val messages by conversationRepository.messages.collectAsState()
        val rmsLevel by speechRecognizerManager.soundLevelRms.collectAsState()
        val isExecuting by taskExecutor.isExecuting.collectAsState()
        val settings by settingsRepository.settingsState.collectAsState()

        var typedInput by remember { mutableStateOf("") }
        var showSettingsDialog by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        // Permissions launcher
        val audioPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                startVoiceListening()
            } else {
                Toast.makeText(this, "Microphone permission is needed for speech", Toast.LENGTH_SHORT).show()
            }
        }

        val notificationsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* handled */ }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionManager.hasNotificationPermission()) {
                notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Auto-scroll chat to bottom on new message
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(JarvezBg),
            containerColor = JarvezBg,
            topBar = {
                // Top Holographic Header Bar
                JarvezTopBar(
                    onSettingsClick = { showSettingsDialog = true },
                    onClearHistoryClick = { conversationRepository.clearHistory() },
                    isAccessibilityActive = permissionManager.isAccessibilityEnabled()
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Telemetry & Status HUD Header
                JarvezTelemetryHeader(
                    state = state,
                    statusText = statusText,
                    isAccessibilityActive = permissionManager.isAccessibilityEnabled()
                )

                // Central Arc Reactor Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.42f),
                    contentAlignment = Alignment.Center
                ) {
                    JarvezVisualizer(
                        state = state,
                        rmsLevel = rmsLevel,
                        modifier = Modifier.clickable {
                            if (state == AssistantState.LISTENING) {
                                speechRecognizerManager.stopListening()
                            } else {
                                if (permissionManager.hasRecordAudioPermission()) {
                                    startVoiceListening()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    )
                }

                // Quick Action Chips
                JarvezQuickActionChips(
                    onActionSelected = { command ->
                        processUserCommand(command, isVoice = false)
                    }
                )

                // Conversation Log Stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.38f)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(
                            message = msg,
                            onConfirm = {
                                msg.pendingCommand?.let { cmd ->
                                    coroutineScope.launch {
                                        executeCommandPlan(cmd, isExplicitlyConfirmed = true)
                                        conversationRepository.removePendingConfirmation()
                                        contextManager.clearPendingConfirmation()
                                    }
                                }
                            },
                            onCancel = {
                                conversationRepository.removePendingConfirmation()
                                contextManager.clearPendingConfirmation()
                                val cancelMsg = "Cancelled by user."
                                conversationRepository.addMessage(
                                    ChatMessage(text = cancelMsg, sender = MessageSender.JARVEZ)
                                )
                                ttsManager.speak(cancelMsg)
                            }
                        )
                    }
                }

                // Bottom Input Console
                JarvezBottomBar(
                    textInput = typedInput,
                    onTextInputChanged = { typedInput = it },
                    onSendClicked = {
                        val text = typedInput.trim()
                        if (text.isNotEmpty()) {
                            typedInput = ""
                            processUserCommand(text, isVoice = false)
                        }
                    },
                    onMicClicked = {
                        if (state == AssistantState.LISTENING) {
                            speechRecognizerManager.stopListening()
                        } else {
                            if (permissionManager.hasRecordAudioPermission()) {
                                startVoiceListening()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    onCancelClicked = {
                        taskExecutor.cancelActiveTask()
                        speechRecognizerManager.stopListening()
                        ttsManager.stop()
                        assistantState.value = AssistantState.READY
                        stateStatusText.value = "TASK ABORTED // READY"
                    },
                    isListening = state == AssistantState.LISTENING,
                    isExecuting = isExecuting
                )
            }

            if (showSettingsDialog) {
                JarvezSettingsModal(
                    settings = settings,
                    permissionManager = permissionManager,
                    onDismiss = { showSettingsDialog = false },
                    onSaveApiKey = { key ->
                        settingsRepository.saveApiKey(key)
                        Toast.makeText(this@MainActivity, "API Key saved securely", Toast.LENGTH_SHORT).show()
                    },
                    onTestApiConnection = { key, onResult ->
                        coroutineScope.launch {
                            val res = aiRepository.testConnection(key)
                            withContext(Dispatchers.Main) {
                                onResult(res)
                            }
                        }
                    },
                    onWakeWordToggled = { enabled ->
                        settingsRepository.setWakeWordEnabled(enabled)
                        if (enabled) {
                            wakeWordManager.startWakeWordListening()
                        } else {
                            wakeWordManager.stopWakeWordListening()
                        }
                    },
                    onConfirmWhatsAppToggled = { enabled ->
                        settingsRepository.setConfirmWhatsAppEnabled(enabled)
                    },
                    onLanguageSelected = { lang ->
                        settingsRepository.setPreferredLanguage(lang)
                    },
                    onBackgroundServiceToggled = { active ->
                        settingsRepository.setBackgroundServiceActive(active)
                        if (active) {
                            JarvezForegroundService.start(this@MainActivity)
                        } else {
                            JarvezForegroundService.stop(this@MainActivity)
                        }
                    }
                )
            }
        }
    }

    @Composable
    fun JarvezTopBar(
        onSettingsClick: () -> Unit,
        onClearHistoryClick: () -> Unit,
        isAccessibilityActive: Boolean
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isAccessibilityActive) JarvezGreen else JarvezAmber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JARVEZ // AI CORE",
                    color = JarvezCyan,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.2.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClearHistoryClick,
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = JarvezSubtext
                    )
                }

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = JarvezCyan
                    )
                }
            }
        }
    }

    @Composable
    fun JarvezTelemetryHeader(
        state: AssistantState,
        statusText: String,
        isAccessibilityActive: Boolean
    ) {
        val stateColor by animateColorAsState(
            targetValue = when (state) {
                AssistantState.READY -> JarvezCyan
                AssistantState.LISTENING -> JarvezGreen
                AssistantState.THINKING -> JarvezAmber
                AssistantState.EXECUTING -> JarvezCyan
                AssistantState.SPEAKING -> JarvezBlue
                AssistantState.ERROR -> JarvezCrimson
            },
            label = "stateColor"
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = JarvezSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, JarvezBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stateColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = state.name,
                            color = stateColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = if (isAccessibilityActive) "ACCESSIBILITY: ACTIVE" else "ACCESSIBILITY: STANDBY",
                        color = if (isAccessibilityActive) JarvezGreen else JarvezAmber,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = statusText,
                    color = JarvezText,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }

    @Composable
    fun JarvezQuickActionChips(onActionSelected: (String) -> Unit) {
        val actions = listOf(
            Pair("Open YouTube", Icons.Default.PlayArrow),
            Pair("Search Google", Icons.Default.Search),
            Pair("WhatsApp Message", Icons.AutoMirrored.Filled.Send),
            Pair("Toggle Flashlight", Icons.Default.FlashlightOn),
            Pair("Open Chrome", Icons.Default.Search),
            Pair("What can you do?", Icons.Default.Settings)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(actions) { (label, icon) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = JarvezSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, JarvezBorder),
                    modifier = Modifier.clickable { onActionSelected(label) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = JarvezCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = JarvezText,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun ChatBubbleItem(
        message: ChatMessage,
        onConfirm: () -> Unit,
        onCancel: () -> Unit
    ) {
        val isUser = message.sender == MessageSender.USER
        val alignment = if (isUser) Alignment.End else Alignment.Start

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = alignment
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(if (isUser) JarvezBlue.copy(alpha = 0.35f) else JarvezSurfaceVariant)
                    .border(
                        1.dp,
                        if (isUser) JarvezBlue else JarvezBorder,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.text,
                    color = JarvezText,
                    fontSize = 14.sp
                )
            }

            // If confirmation is required for this message
            if (message.isPendingConfirmation) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvezGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Confirm & Send", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvezCrimson),
                        modifier = Modifier.testTag("cancel_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    @Composable
    fun JarvezBottomBar(
        textInput: String,
        onTextInputChanged: (String) -> Unit,
        onSendClicked: () -> Unit,
        onMicClicked: () -> Unit,
        onCancelClicked: () -> Unit,
        isListening: Boolean,
        isExecuting: Boolean
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
        val micScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isListening) 1.25f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "micScale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel Task Button if executing or listening
            if (isExecuting || isListening) {
                IconButton(
                    onClick = onCancelClicked,
                    modifier = Modifier
                        .size(44.dp)
                        .background(JarvezCrimson.copy(alpha = 0.2f), CircleShape)
                        .testTag("task_cancel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = JarvezCrimson
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Text Input Box
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChanged,
                placeholder = {
                    Text(
                        "Command JARVEZ (Urdu / English)...",
                        color = JarvezSubtext,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_text_input"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvezCyan,
                    unfocusedBorderColor = JarvezBorder,
                    focusedTextColor = JarvezText,
                    unfocusedTextColor = JarvezText,
                    focusedContainerColor = JarvezSurface,
                    unfocusedContainerColor = JarvezSurface
                ),
                maxLines = 2,
                trailingIcon = {
                    if (textInput.isNotBlank()) {
                        IconButton(
                            onClick = onSendClicked,
                            modifier = Modifier.testTag("send_command_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = JarvezCyan
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Large Microphone FAB
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(micScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isListening) JarvezGreen else JarvezCyan,
                                if (isListening) Color(0xFF00B4D8) else JarvezBlue
                            )
                        )
                    )
                    .clickable { onMicClicked() }
                    .testTag("microphone_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isListening) "Stop Listening" else "Voice Command",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }

    @Composable
    fun JarvezSettingsModal(
        settings: com.jarvez.assistant.data.JarvezSettings,
        permissionManager: PermissionManager,
        onDismiss: () -> Unit,
        onSaveApiKey: (String) -> Unit,
        onTestApiConnection: (String?, (Result<Long>) -> Unit) -> Unit,
        onWakeWordToggled: (Boolean) -> Unit,
        onConfirmWhatsAppToggled: (Boolean) -> Unit,
        onLanguageSelected: (String) -> Unit,
        onBackgroundServiceToggled: (Boolean) -> Unit
    ) {
        var apiKeyInput by remember { mutableStateOf(settingsRepository.getEffectiveApiKey()) }
        var isTestingConnection by remember { mutableStateOf(false) }
        var connectionTestResult by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = JarvezSurface,
            title = {
                Text(
                    text = "JARVEZ CONFIGURATION",
                    color = JarvezCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Gemini AI API Configuration
                    item {
                        Text(
                            text = "GEMINI AI INTELLIGENCE",
                            color = JarvezCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { apiKeyInput = it },
                            label = { Text("Gemini API Key", fontSize = 12.sp) },
                            placeholder = { Text("AI Studio API Key", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvezCyan,
                                unfocusedBorderColor = JarvezBorder,
                                focusedTextColor = JarvezText,
                                unfocusedTextColor = JarvezText
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSaveApiKey(apiKeyInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvezCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_api_key_button")
                            ) {
                                Text("Save Key", color = Color.Black, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    connectionTestResult = null
                                    onTestApiConnection(apiKeyInput) { result ->
                                        isTestingConnection = false
                                        connectionTestResult = result.fold(
                                            onSuccess = { latency -> "Connected! Latency: ${latency}ms" },
                                            onFailure = { err -> "Error: ${err.message}" }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvezSurfaceVariant),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_api_key_button")
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = JarvezCyan,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Ping AI", color = JarvezText, fontSize = 12.sp)
                                }
                            }
                        }

                        connectionTestResult?.let { msg ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg,
                                color = if (msg.startsWith("Connected")) JarvezGreen else JarvezCrimson,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Toggles
                    item {
                        Text(
                            text = "BEHAVIOR & CONTROLS",
                            color = JarvezCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Wake Word toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Wake-Word Detection", color = JarvezText, fontSize = 13.sp)
                                Text("Listen for 'Hey Jarvez'", color = JarvezSubtext, fontSize = 11.sp)
                            }
                            Switch(
                                checked = settings.isWakeWordEnabled,
                                onCheckedChange = onWakeWordToggled,
                                colors = SwitchDefaults.colors(checkedThumbColor = JarvezCyan, checkedTrackColor = JarvezBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // WhatsApp Confirmation toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Confirm WhatsApp Messages", color = JarvezText, fontSize = 13.sp)
                                Text("Require explicit approval before sending", color = JarvezSubtext, fontSize = 11.sp)
                            }
                            Switch(
                                checked = settings.isConfirmWhatsAppEnabled,
                                onCheckedChange = onConfirmWhatsAppToggled,
                                colors = SwitchDefaults.colors(checkedThumbColor = JarvezCyan, checkedTrackColor = JarvezBlue)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Background Foreground Service toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foreground Assistant Service", color = JarvezText, fontSize = 13.sp)
                                Text("Persistent notification and background mic", color = JarvezSubtext, fontSize = 11.sp)
                            }
                            Switch(
                                checked = settings.isBackgroundServiceActive,
                                onCheckedChange = onBackgroundServiceToggled,
                                colors = SwitchDefaults.colors(checkedThumbColor = JarvezCyan, checkedTrackColor = JarvezBlue)
                            )
                        }
                    }

                    // System Permissions & Accessibility
                    item {
                        Text(
                            text = "AUTOMATION & ACCESSIBILITY",
                            color = JarvezCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val isAccessActive = permissionManager.isAccessibilityEnabled()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = JarvezSurfaceVariant),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAccessActive) JarvezGreen else JarvezAmber)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isAccessActive) "Accessibility Service: ENABLED" else "Accessibility Service: DISABLED",
                                    color = if (isAccessActive) JarvezGreen else JarvezAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Required for automatic YouTube playback and WhatsApp automation.",
                                    color = JarvezSubtext,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { context.startActivity(permissionManager.openAccessibilitySettings()) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvezCyan),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Open Accessibility Settings", color = Color.Black, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Battery optimization exemption guidance
                        OutlinedButton(
                            onClick = { context.startActivity(permissionManager.openBatteryOptimizationSettings()) },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Battery Optimization Exemption", color = JarvezCyan, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvezCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = Color.Black)
                }
            }
        )
    }
}
