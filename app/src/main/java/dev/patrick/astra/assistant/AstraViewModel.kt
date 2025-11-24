package dev.patrick.astra.assistant

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.SkillRouter
import dev.patrick.astra.brains.llm.FakeLlmClient
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.AssistantVisualState
import dev.patrick.astra.domain.Emotion
import dev.patrick.astra.services.voice.CloudSttEngineStub
import dev.patrick.astra.services.voice.LocalWhisperEngineStub
import dev.patrick.astra.services.voice.SttBackend
import dev.patrick.astra.services.voice.SystemSpeechEngine
import dev.patrick.astra.services.voice.TranscriptionEngine
import dev.patrick.astra.services.voice.TtsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AstraUiState(
    val messages: List<AstraMessage> = emptyList(),
    val isThinking: Boolean = false,
    val isListening: Boolean = false
)

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
    val visualState: StateFlow<AssistantVisualState> = AssistantStateStore.visualState

    private val context get() = getApplication<Application>()

    private val brain = Brain(
        llmClient = FakeLlmClient(),
        skillRouter = SkillRouter()
    )

    private val brainController = BrainController(
        brain = brain,
        scope = viewModelScope
    )

    private var transcriptionEngine: TranscriptionEngine? = null

    private val currentSttBackend: SttBackend = SttBackend.SYSTEM

    init {
        setIdle()
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

        _uiState.update { state ->
            state.copy(
                messages = state.messages + AstraMessage(fromUser = true, text = text),
                isThinking = true
            )
        }
        AssistantStateStore.set(
            phase = AssistantPhase.Thinking,
            emotion = Emotion.Focused
        )

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
                        AssistantStateStore.set(
                            phase = AssistantPhase.Thinking,
                            emotion = Emotion.Curious
                        )
                        AssistantStateStore.update(
                            phase = AssistantPhase.Idle,
                            emotion = Emotion.Neutral
                        )
                    }

                    is BrainResult.Ignored -> {
                        _uiState.update { state ->
                            state.copy(isThinking = false)
                        }
                        AssistantStateStore.set(
                            phase = AssistantPhase.Idle,
                            emotion = Emotion.Neutral
                        )
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
                AssistantStateStore.set(
                    phase = AssistantPhase.Thinking,
                    emotion = Emotion.Focused
                )
                sendUserMessage(recognizedText)
            },
            onError = { _ ->
                _uiState.update { state ->
                    state.copy(isListening = false)
                }
                setError()
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
        AssistantStateStore.set(
            phase = AssistantPhase.Idle,
            emotion = Emotion.Neutral
        )
    }

    private fun setListening() {
        AssistantStateStore.set(
            phase = AssistantPhase.Listening,
            emotion = Emotion.Focused
        )
    }

    private fun setSpeaking() {
        AssistantStateStore.set(
            phase = AssistantPhase.Speaking,
            emotion = Emotion.Happy
        )
    }

    private fun setError() {
        AssistantStateStore.set(
            phase = AssistantPhase.Error(reason = null),
            emotion = Emotion.Concerned
        )
    }
}
