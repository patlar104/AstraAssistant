package dev.patrick.astra.services.voice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.ArrayDeque
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.Emotion
import android.speech.tts.TextToSpeech

class TtsService : Service() {

    private var ttsEngine: TtsEngine? = null
    private var isReady = false

    // Queue for texts received before initialization
    private val pendingQueue = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        ttsEngine = ttsEngineFactory.create(this) { status ->
            handleInit(status)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_TEXT)?.let { text ->
            enqueueOrSpeak(text)
        }
        return START_NOT_STICKY
    }

    private fun handleInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsEngine?.setLanguage(Locale.US)
            ttsEngine?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // no-op
                    }

                    override fun onDone(utteranceId: String?) {
                        updateAssistantState(
                            targetState = AssistantPhase.Idle,
                            targetEmotion = Emotion.Neutral
                        )
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        updateAssistantState(
                            targetState = AssistantPhase.Error(reason = "tts_error_$errorCode"),
                            targetEmotion = Emotion.Concerned
                        )
                    }

                    @Deprecated("Deprecated in TextToSpeech")
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) {
                        onError(utteranceId, TextToSpeech.ERROR)
                    }
                }
            )
            isReady = true

            // Speak queued messages
            while (pendingQueue.isNotEmpty()) {
                val msg = pendingQueue.removeFirst()
                speakInternal(msg)
            }
        }
    }

    private fun enqueueOrSpeak(text: String) {
        AssistantStateStore.set(
            phase = AssistantPhase.Speaking,
            emotion = Emotion.Happy
        )
        if (!isReady) {
            pendingQueue.addLast(text)
            return
        }

        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        ttsEngine?.speak(
            text,
            TextToSpeech.QUEUE_ADD,
            null,
            timeProvider().toString()
        )
    }

    private fun updateAssistantState(targetState: AssistantPhase, targetEmotion: Emotion) {
        if (ttsEngine?.isSpeaking != true) {
            AssistantStateStore.set(
                phase = targetState,
                emotion = targetEmotion
            )
        }
    }

    override fun onDestroy() {
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        AssistantStateStore.set(
            phase = AssistantPhase.Idle,
            emotion = Emotion.Neutral
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEXT = "tts_text"
        internal var ttsEngineFactory: TtsEngineFactory =
            TtsEngineFactory { context, onInit -> AndroidTtsEngine(context, onInit) }
        internal var timeProvider: () -> Long = { System.currentTimeMillis() }
    }
}
