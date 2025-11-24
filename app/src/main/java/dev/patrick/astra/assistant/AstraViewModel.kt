package dev.patrick.astra.assistant

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.SkillRouter
import dev.patrick.astra.brains.llm.FakeLlmClient
import dev.patrick.astra.ui.AstraState
import dev.patrick.astra.ui.Emotion
import dev.patrick.astra.stt.CloudSttEngineStub
import dev.patrick.astra.stt.LocalWhisperEngineStub
import dev.patrick.astra.stt.SttBackend
import dev.patrick.astra.stt.SystemSpeechEngine
import dev.patrick.astra.stt.TranscriptionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AstraUiState(
    val messages: List<AstraMessage> = emptyList(),
    val isThinking: Boolean = false,
    val isListening: Boolean = false
)

/**
 * ViewModel that holds the conversation state and talks to the brain controller.
 */
class AstraViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        AstraUiState(
            messages = listOf(
                AstraMessage(
                    fromUser = false,
                    text = "Hi, I’m Astra. Ask me anything!"
                )
            ),
            isThinking = false,
            isListening = false
        )
    )
    val uiState: StateFlow<AstraUiState> = _uiState

    private val context get() = getApplication<Application>()

    private val brain = Brain(
        llmClient = FakeLlmClient(),
        skillRouter = SkillRouter()
    )

    private val brainController = BrainController(
        brain = brain,
        overlayUiStateStore = OverlayUiStateStore,
        scope = viewModelScope
    )

    private var transcriptionEngine: TranscriptionEngine? = null

    // Future: this could be user-configurable (e.g., from settings screen).
    private val currentSttBackend: SttBackend = SttBackend.SYSTEM

    init {
        setIdle()
    }

    private fun ensureTranscriptionEngine() {
        if (transcriptionEngine != null) return

        // For now, we always use the SystemSpeechEngine, which wraps SpeechRecognizerManager.
        // In the future, we can branch here based on currentSttBackend and create:
        // - LocalWhisperEngineStub
        // - CloudSttEngineStub
        transcriptionEngine = when (currentSttBackend) {
            SttBackend.SYSTEM -> SystemSpeechEngine(context)
            SttBackend.LOCAL_WHISPER -> LocalWhisperEngineStub() // TODO: implement real local Whisper
            SttBackend.CLOUD_STT -> CloudSttEngineStub()         // TODO: implement real cloud STT
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        // Add the user message and show thinking state
        _uiState.update { state ->
            state.copy(
                messages = state.messages + AstraMessage(fromUser = true, text = text),
                isThinking = true
            )
        }

        brainController.submitUserMessage(
            text = text,
            onResult = { result ->
                when (result) {
                    is BrainResult.DirectReply -> {
                        val replyMessage = AstraMessage(fromUser = false, text = result.text)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + replyMessage,
                                isThinking = false
                            )
                        }
                        speak(result.text)
                    }

                    is BrainResult.ActionRequired -> {
                        val plan = result.plan
                        val summary = plan.summary ?: "Executing ${plan.steps.size} step(s)"
                        val actionMessage = AstraMessage(
                            fromUser = false,
                            text = "Plan: $summary (${plan.steps.size} step(s))"
                        )
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages + actionMessage,
                                isThinking = false
                            )
                        }
                    }

                    is BrainResult.Ignored -> {
                        _uiState.update { state ->
                            state.copy(isThinking = false)
                        }
                    }
                }
            },
            onError = {
                _uiState.update { state ->
                    state.copy(isThinking = false)
                }
                setError()
            }
        )
    }

    private fun speak(text: String) {
        setSpeaking()
        val intent = Intent(context, TtsService::class.java).apply {
            putExtra(TtsService.EXTRA_TEXT, text)
        }
        context.startService(intent)
    }

    fun startVoiceInput() {
        ensureTranscriptionEngine()
        setListening()
        transcriptionEngine?.startListening(
            onFinalResult = { recognizedText ->
                _uiState.update { state ->
                    state.copy(isListening = false)
                }
                // When we get a final result, treat it like a user message.
                sendUserMessage(recognizedText)
            },
            onError = { _ ->
                _uiState.update { state ->
                    state.copy(isListening = false)
                }
                setError()
                // TODO: Optionally log or surface the error message.
            },
            onListeningChanged = { listening ->
                _uiState.update { state ->
                    state.copy(isListening = listening)
                }
                if (listening) {
                    setListening()
                }
            }
        )
    }

    fun stopVoiceInput() {
        transcriptionEngine?.stopListening()
        _uiState.update { state ->
            state.copy(isListening = false)
        }
        setIdle()
    }

    override fun onCleared() {
        transcriptionEngine?.release()
        transcriptionEngine = null
        super.onCleared()
    }

    private fun setIdle() {
        OverlayUiStateStore.set(
            state = AstraState.Idle,
            emotion = Emotion.Neutral
        )
    }

    private fun setListening() {
        OverlayUiStateStore.set(
            state = AstraState.Listening(intensity = 1f),
            emotion = Emotion.Focused
        )
    }

    private fun setSpeaking() {
        OverlayUiStateStore.set(
            state = AstraState.Speaking(mood = Emotion.Happy),
            emotion = Emotion.Happy
        )
    }

    private fun setError() {
        OverlayUiStateStore.set(
            state = AstraState.Error(),
            emotion = Emotion.Concerned
        )
    }
}
