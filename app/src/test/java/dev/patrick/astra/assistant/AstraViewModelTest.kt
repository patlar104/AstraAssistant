package dev.patrick.astra.assistant

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.DeviceActionStep
import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.Emotion
import dev.patrick.astra.services.voice.TranscriptionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AstraViewModelTest {

    @Before
    fun setUp() {
        AssistantStateStore.set(phase = AssistantPhase.Idle, emotion = Emotion.Neutral)
    }

    @Test
    fun sendUserMessage_directReply_updatesUiAndStartsTts() {
        val reply = BrainResult.DirectReply(
            text = "Hi",
            parsedIntent = ParsedIntent(
                type = IntentType.SMALL_TALK,
                arguments = emptyMap(),
                confidence = 0.9f,
                rawText = "Hi"
            ),
            plan = ActionPlan.AnswerDirectly("Hi")
        )
        val brain = FakeBrainSubmitter(result = reply)
        var spokenText: String? = null

        val viewModel = AstraViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            brainSubmitterOverride = brain,
            ttsStarterOverride = { _, text -> spokenText = text }
        )

        viewModel.sendUserMessage("Hello")

        val uiState = viewModel.uiState.value
        assertEquals(3, uiState.messages.size)
        assertEquals("Hello", uiState.messages[1].text)
        assertEquals("Hi", uiState.messages[2].text)
        assertFalse(uiState.isThinking)
        assertEquals("Hi", spokenText)

        val visualState = viewModel.visualState.value
        assertEquals(AssistantPhase.Speaking, visualState.phase)
        assertEquals(Emotion.Happy, visualState.emotion)
    }

    @Test
    fun sendUserMessage_actionRequired_setsIdleAfterPlan() {
        val plan = ActionPlan.ExecuteDeviceActions(
            steps = listOf(DeviceActionStep.ShowText("do it")),
            summary = "Do thing"
        )
        val result = BrainResult.ActionRequired(
            parsedIntent = ParsedIntent(
                type = IntentType.SEARCH_WEB,
                arguments = emptyMap(),
                confidence = 0.9f,
                rawText = "search"
            ),
            plan = plan
        )
        val brain = FakeBrainSubmitter(result = result)

        val viewModel = AstraViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            brainSubmitterOverride = brain,
            ttsStarterOverride = { _, _ -> }
        )

        viewModel.sendUserMessage("Search")

        val uiState = viewModel.uiState.value
        assertFalse(uiState.isThinking)
        assertTrue(uiState.messages.last().text.contains("Plan: Do thing"))

        val visualState = viewModel.visualState.value
        assertEquals(AssistantPhase.Idle, visualState.phase)
        assertEquals(Emotion.Neutral, visualState.emotion)
    }

    @Test
    fun startAndStopVoiceInput_updatesVisualState() {
        val fakeEngine = FakeTranscriptionEngine()
        val viewModel = AstraViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            brainSubmitterOverride = FakeBrainSubmitter(result = BrainResult.Ignored()),
            transcriptionEngineFactory = { _, _ -> fakeEngine },
            ttsStarterOverride = { _, _ -> }
        )

        viewModel.startVoiceInput()
        fakeEngine.triggerListening(true)

        assertTrue(viewModel.uiState.value.isListening)
        assertEquals(AssistantPhase.Listening, viewModel.visualState.value.phase)
        assertEquals(Emotion.Focused, viewModel.visualState.value.emotion)

        viewModel.stopVoiceInput()

        assertTrue(fakeEngine.stopCalled)
        assertFalse(viewModel.uiState.value.isListening)
        assertEquals(AssistantPhase.Idle, viewModel.visualState.value.phase)
        assertEquals(Emotion.Neutral, viewModel.visualState.value.emotion)
    }

    private class FakeBrainSubmitter(
        private val result: BrainResult? = null,
        private val error: Throwable? = null
    ) : BrainSubmitter {
        var lastText: String? = null

        override fun submitUserMessage(
            text: String,
            onResult: (BrainResult) -> Unit,
            onError: (Throwable) -> Unit
        ) {
            lastText = text
            error?.let {
                onError(it)
                return
            }
            result?.let { onResult(it) }
        }
    }

    private class FakeTranscriptionEngine : TranscriptionEngine {
        var stopCalled = false
        private var onFinalResult: ((String) -> Unit)? = null
        private var onError: ((String) -> Unit)? = null
        private var onListeningChanged: ((Boolean) -> Unit)? = null

        override fun startListening(
            onFinalResult: (String) -> Unit,
            onError: (String) -> Unit,
            onListeningChanged: (Boolean) -> Unit
        ) {
            this.onFinalResult = onFinalResult
            this.onError = onError
            this.onListeningChanged = onListeningChanged
        }

        override fun stopListening() {
            stopCalled = true
        }

        override fun release() = Unit

        fun triggerListening(listening: Boolean) {
            onListeningChanged?.invoke(listening)
        }

        fun triggerFinal(text: String) {
            onFinalResult?.invoke(text)
        }

        fun triggerError(message: String) {
            onError?.invoke(message)
        }
    }
}
