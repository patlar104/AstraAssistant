package dev.patrick.astra.assistant

import android.app.Application
import android.content.Intent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.patrick.astra.actions.AccessibilityActionExecutor
import dev.patrick.astra.actions.ActionConfirmationPolicy
import dev.patrick.astra.actions.ActionExecutor
import dev.patrick.astra.actions.ActionResult
import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.SkillRouter
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.llm.BrainContext
import dev.patrick.astra.brains.llm.FakeLlmClient
import dev.patrick.astra.data.AstraDatabase
import dev.patrick.astra.data.ConversationRepository
import dev.patrick.astra.diagnostics.DiagnosticsLog
import dev.patrick.astra.domain.AssistantEvent
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.AssistantVisualState
import dev.patrick.astra.domain.HealthState
import dev.patrick.astra.legacy.AccessibilityBridge
import dev.patrick.astra.overlay.OverlayService
import dev.patrick.astra.services.voice.CloudSttEngineStub
import dev.patrick.astra.services.voice.LocalWhisperEngineStub
import dev.patrick.astra.services.voice.SttBackend
import dev.patrick.astra.services.voice.SystemSpeechEngine
import dev.patrick.astra.services.voice.TranscriptionEngine
import dev.patrick.astra.services.voice.TranscriptionError
import dev.patrick.astra.services.voice.TtsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "AstraViewModel"


data class AstraUiState(
    val messages: List<AstraMessage> = emptyList(),
    val isThinking: Boolean = false,
    val isListening: Boolean = false,
    val pendingActionPlan: ActionPlan.ExecuteDeviceActions? = null,
    val confirmationRequired: Boolean = false
)

class AstraViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        AstraUiState(
            messages = emptyList(),
            isThinking = false,
            isListening = false
        )
    )
    val uiState: StateFlow<AstraUiState> = _uiState.asStateFlow()
    val visualState: StateFlow<AssistantVisualState> = AssistantStateStore.visualState

    private val _healthState = MutableStateFlow(
        HealthState(
            overlayPermissionGranted = false,
            voiceAvailable = false,
            voiceError = null,
            accessibilityEnabled = false
        )
    )
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()

    val snackbarEvents = MutableSharedFlow<SnackbarEvent>()

    private val context get() = getApplication<Application>()

    private val database = AstraDatabase.getInstance(context)
    private val repository = ConversationRepository(database.conversationDao())

    private val brain = Brain(
        llmClient = FakeLlmClient(),
        skillRouter = SkillRouter()
    )

    private val brainController = BrainController(
        brain = brain,
        scope = viewModelScope
    )

    private val actionExecutor: ActionExecutor = AccessibilityActionExecutor(context)
    private val actionPolicy = ActionConfirmationPolicy(context)

    private var transcriptionEngine: TranscriptionEngine? = null
    private val currentSttBackend: SttBackend = SttBackend.SYSTEM

    private var sttRetryCount = 0
    private var sttRetryJob: Job? = null
    private var currentSttSessionId: Long = 0L

    private val sessionId: Long = System.currentTimeMillis()

    init {
        AssistantStateStore.dispatch(AssistantEvent.ResetToIdle)
        loadInitialMessages()
        refreshHealth()
    }

    fun refreshHealth() {
        val overlayGranted = OverlayService.canDrawOverlays(context)
        val voiceAvailable = when (currentSttBackend) {
            SttBackend.SYSTEM -> SpeechRecognizer.isRecognitionAvailable(context)
            SttBackend.LOCAL_WHISPER -> false
            SttBackend.CLOUD_STT -> false
        }
        val voiceError = transcriptionEngine?.lastError
        val accessibilityEnabled = AccessibilityBridge(context).isServiceReady()

        _healthState.value = HealthState(
            overlayPermissionGranted = overlayGranted,
            voiceAvailable = voiceAvailable,
            voiceError = voiceError,
            accessibilityEnabled = accessibilityEnabled
        )
    }

    private fun loadInitialMessages() {
        viewModelScope.launch {
            val storedMessages = repository.loadRecent(MAX_MESSAGES)
            val messages = if (storedMessages.isEmpty()) {
                val greeting = AstraMessage(
                    fromUser = false,
                    text = "Hi, I’m Astra. Ask me anything!"
                )
                repository.insertMessage(greeting, sessionId)
                listOf(greeting)
            } else {
                storedMessages
            }

            _uiState.update { state ->
                state.copy(messages = messages)
            }

            val memory = repository.buildMemory(messages)
            brain.setContext(
                BrainContext(memory = memory)
            )
        }
    }

    private fun ensureTranscriptionEngine() {
        if (transcriptionEngine != null) return

        transcriptionEngine = when (currentSttBackend) {
            SttBackend.SYSTEM -> SystemSpeechEngine(context)
            SttBackend.LOCAL_WHISPER -> LocalWhisperEngineStub()
            SttBackend.CLOUD_STT -> CloudSttEngineStub()
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = AstraMessage(fromUser = true, text = text)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage,
                isThinking = true
            )
        }
        AssistantStateStore.dispatch(AssistantEvent.ThinkingStarted)
        viewModelScope.launch { repository.insertMessage(userMessage, sessionId) }

        brainController.submitUserMessage(
            text = text,
            onResult = { result ->
                when (result) {
                    is BrainResult.DirectReply -> handleDirectReply(result)
                    is BrainResult.ActionRequired -> handleActionRequired(result.plan)
                    is BrainResult.Ignored -> handleIgnored()
                }
            },
            onError = {
                _uiState.update { state ->
                    state.copy(isThinking = false)
                }
                AssistantStateStore.dispatch(AssistantEvent.Error(reason = "brain_error"))
                viewModelScope.launch {
                    snackbarEvents.emit(SnackbarEvent("Brain error. Please try again."))
                }
            }
        )
    }

    private fun handleDirectReply(result: BrainResult.DirectReply) {
        val replyMessage = AstraMessage(fromUser = false, text = result.text)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + replyMessage,
                isThinking = false
            )
        }
        AssistantStateStore.dispatch(AssistantEvent.ThinkingStopped)
        viewModelScope.launch { repository.insertMessage(replyMessage, sessionId) }
        speak(result.text)
    }

    private fun handleActionRequired(plan: ActionPlan.ExecuteDeviceActions) {
        val summary = plan.summary ?: "Executing ${plan.steps.size} step(s)"
        val actionMessage = AstraMessage(
            fromUser = false,
            text = "Plan: $summary (${plan.steps.size} step(s))"
        )
        val requiresConfirmation = actionPolicy.requiresConfirmation(plan.steps)
        _uiState.update { state ->
            state.copy(
                messages = state.messages + actionMessage,
                isThinking = false,
                pendingActionPlan = if (requiresConfirmation) plan else null,
                confirmationRequired = requiresConfirmation
            )
        }
        AssistantStateStore.dispatch(AssistantEvent.ThinkingStopped)
        viewModelScope.launch { repository.insertMessage(actionMessage, sessionId) }

        if (!requiresConfirmation) {
            executeActionPlan(plan)
        }
    }

    private fun handleIgnored() {
        _uiState.update { state ->
            state.copy(isThinking = false)
        }
        AssistantStateStore.dispatch(AssistantEvent.ThinkingStopped)
    }

    fun confirmPendingAction(skipConfirmation: Boolean) {
        val plan = _uiState.value.pendingActionPlan ?: return
        _uiState.update { state ->
            state.copy(
                pendingActionPlan = null,
                confirmationRequired = false
            )
        }
        if (skipConfirmation) {
            actionPolicy.setSkipConfirmation(plan.steps, true)
        }
        executeActionPlan(plan)
    }

    fun cancelPendingAction() {
        _uiState.update { state ->
            state.copy(
                pendingActionPlan = null,
                confirmationRequired = false
            )
        }
        viewModelScope.launch {
            snackbarEvents.emit(SnackbarEvent("Action canceled"))
        }
    }

    private fun executeActionPlan(plan: ActionPlan.ExecuteDeviceActions) {
        AssistantStateStore.dispatch(AssistantEvent.ThinkingStarted)
        viewModelScope.launch {
            val result = actionExecutor.execute(plan)
            handleActionResult(result)
            AssistantStateStore.dispatch(AssistantEvent.ThinkingStopped)
        }
    }

    private suspend fun handleActionResult(result: ActionResult) {
        if (result.success) {
            val messageText = result.message ?: "Action completed"
            val message = AstraMessage(fromUser = false, text = messageText)
            _uiState.update { state ->
                state.copy(messages = state.messages + message)
            }
            repository.insertMessage(message, sessionId)
            result.undoPlan?.let { undoPlan ->
                snackbarEvents.emit(
                    SnackbarEvent(
                        message = "Action completed",
                        actionLabel = "Undo",
                        onAction = { executeActionPlan(undoPlan) }
                    )
                )
            }
        } else {
            val errorText = result.error?.message ?: "Action failed"
            DiagnosticsLog.w(TAG, "Action failed: ${result.error?.code} $errorText")
            val message = AstraMessage(fromUser = false, text = "Action failed: $errorText")
            _uiState.update { state ->
                state.copy(messages = state.messages + message)
            }
            repository.insertMessage(message, sessionId)
            snackbarEvents.emit(SnackbarEvent(errorText))
        }
    }

    private fun speak(text: String) {
        val intent = Intent(context, TtsService::class.java).apply {
            putExtra(TtsService.EXTRA_TEXT, text)
        }
        context.startService(intent)
    }

    fun startVoiceInput() {
        ensureTranscriptionEngine()
        val engine = transcriptionEngine
        if (engine == null || !engine.isAvailable()) {
            DiagnosticsLog.w(TAG, "Voice input unavailable")
            refreshHealth()
            viewModelScope.launch {
                snackbarEvents.emit(SnackbarEvent("Voice input is unavailable"))
            }
            return
        }

        sttRetryJob?.cancel()
        sttRetryCount = 0
        currentSttSessionId = System.currentTimeMillis()

        AssistantStateStore.dispatch(AssistantEvent.ListeningStarted)
        _uiState.update { state -> state.copy(isListening = true) }
        startListeningInternal(currentSttSessionId)
    }

    private fun startListeningInternal(sessionId: Long) {
        transcriptionEngine?.startListening(
            onFinalResult = { recognizedText ->
                if (sessionId != currentSttSessionId) return@startListening
                _uiState.update { state ->
                    state.copy(isListening = false)
                }
                AssistantStateStore.dispatch(AssistantEvent.ListeningStopped)
                sendUserMessage(recognizedText)
            },
            onError = { error ->
                if (sessionId != currentSttSessionId) return@startListening
                handleTranscriptionError(error)
            },
            onListeningChanged = { listening ->
                if (sessionId != currentSttSessionId) return@startListening
                _uiState.update { state ->
                    state.copy(isListening = listening)
                }
                if (listening) {
                    AssistantStateStore.dispatch(AssistantEvent.ListeningStarted)
                } else {
                    AssistantStateStore.dispatch(AssistantEvent.ListeningStopped)
                }
            }
        )
    }

    private fun handleTranscriptionError(error: TranscriptionError) {
        _uiState.update { state ->
            state.copy(isListening = false)
        }
        AssistantStateStore.dispatch(AssistantEvent.ListeningStopped)
        DiagnosticsLog.w(TAG, "STT error: ${error.message}")

        if (error.isTransient && sttRetryCount < MAX_STT_RETRIES) {
            val delayMs = BASE_STT_RETRY_DELAY_MS * (1 shl sttRetryCount)
            sttRetryCount += 1
            sttRetryJob = viewModelScope.launch {
                delay(delayMs)
                if (currentSttSessionId != 0L) {
                    startListeningInternal(currentSttSessionId)
                }
            }
            return
        }

        AssistantStateStore.dispatch(AssistantEvent.Error(reason = error.message))
        refreshHealth()
        viewModelScope.launch {
            snackbarEvents.emit(SnackbarEvent("Voice error: ${error.message}"))
        }
    }

    fun stopVoiceInput() {
        sttRetryJob?.cancel()
        sttRetryCount = 0
        currentSttSessionId = 0L
        transcriptionEngine?.stopListening()
        _uiState.update { state ->
            state.copy(isListening = false)
        }
        AssistantStateStore.dispatch(AssistantEvent.ListeningStopped)
        AssistantStateStore.dispatch(AssistantEvent.ResetToIdle)
    }

    override fun onCleared() {
        sttRetryJob?.cancel()
        transcriptionEngine?.release()
        transcriptionEngine = null
        super.onCleared()
    }

    companion object {
        private const val MAX_MESSAGES = 50
        private const val MAX_STT_RETRIES = 3
        private const val BASE_STT_RETRY_DELAY_MS = 500L
    }
}
