package dev.patrick.astra.services.voice

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.Emotion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsServiceTest {

    private lateinit var fakeEngine: FakeTtsEngine

    @Before
    fun setUp() {
        AssistantStateStore.set(phase = AssistantPhase.Idle, emotion = Emotion.Neutral)
        TtsService.ttsEngineFactory = TtsEngineFactory { _, onInit ->
            FakeTtsEngine(onInit).also { fakeEngine = it }
        }
        TtsService.timeProvider = { 1234L }
    }

    @After
    fun tearDown() {
        TtsService.ttsEngineFactory = TtsEngineFactory { context, onInit ->
            AndroidTtsEngine(context, onInit)
        }
        TtsService.timeProvider = { System.currentTimeMillis() }
    }

    @Test
    fun enqueueBeforeInit_speaksAfterInit() {
        val controller = Robolectric.buildService(TtsService::class.java).create()
        val service = controller.get()

        val intent = Intent().putExtra(TtsService.EXTRA_TEXT, "hello")
        service.onStartCommand(intent, 0, 0)

        assertEquals(AssistantPhase.Speaking, AssistantStateStore.visualState.value.phase)
        assertTrue(fakeEngine.spokenTexts.isEmpty())

        fakeEngine.triggerInit(TextToSpeech.SUCCESS)

        assertEquals(listOf("hello"), fakeEngine.spokenTexts)
    }

    @Test
    fun onDone_updatesAssistantState() {
        val controller = Robolectric.buildService(TtsService::class.java).create()
        val service = controller.get()
        fakeEngine.triggerInit(TextToSpeech.SUCCESS)

        val intent = Intent().putExtra(TtsService.EXTRA_TEXT, "hello")
        service.onStartCommand(intent, 0, 0)
        fakeEngine.listener?.onDone("1234")

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Idle, state.phase)
        assertEquals(Emotion.Neutral, state.emotion)
    }

    @Test
    fun onError_setsErrorState() {
        val controller = Robolectric.buildService(TtsService::class.java).create()
        val service = controller.get()
        fakeEngine.triggerInit(TextToSpeech.SUCCESS)

        val intent = Intent().putExtra(TtsService.EXTRA_TEXT, "hello")
        service.onStartCommand(intent, 0, 0)
        fakeEngine.listener?.onError("1234", 7)

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Error("tts_error_7"), state.phase)
        assertEquals(Emotion.Concerned, state.emotion)
    }

    @Test
    fun onDestroy_resetsAssistantState() {
        val controller = Robolectric.buildService(TtsService::class.java).create()
        fakeEngine.triggerInit(TextToSpeech.SUCCESS)

        controller.destroy()

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Idle, state.phase)
        assertEquals(Emotion.Neutral, state.emotion)
    }

    private class FakeTtsEngine(
        private val initCallback: (Int) -> Unit
    ) : TtsEngine {
        val spokenTexts = mutableListOf<String>()
        var listener: UtteranceProgressListener? = null
        var lastLocale: Locale? = null
        var speaking: Boolean = false

        override fun setLanguage(locale: Locale) {
            lastLocale = locale
        }

        override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
            this.listener = listener
        }

        override fun speak(text: String, queueMode: Int, params: android.os.Bundle?, utteranceId: String) {
            spokenTexts += text
        }

        override val isSpeaking: Boolean
            get() = speaking

        override fun stop() {
            speaking = false
        }

        override fun shutdown() {
            speaking = false
        }

        fun triggerInit(status: Int) {
            initCallback(status)
        }
    }
}
