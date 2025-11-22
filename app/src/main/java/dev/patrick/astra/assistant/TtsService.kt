package dev.patrick.astra.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.ArrayDeque
import dev.patrick.astra.ui.AstraState
import dev.patrick.astra.ui.Emotion

class TtsService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    // Queue for texts received before initialization
    private val pendingQueue = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_TEXT)?.let { text ->
            enqueueOrSpeak(text)
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // no-op
                    }

                    override fun onDone(utteranceId: String?) {
                        if (tts?.isSpeaking != true) {
                            OverlayUiStateStore.set(
                                state = AstraState.Idle,
                                emotion = Emotion.Neutral
                            )
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (tts?.isSpeaking != true) {
                            OverlayUiStateStore.set(
                                state = AstraState.Error(),
                                emotion = Emotion.Concerned
                            )
                        }
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
        OverlayUiStateStore.set(
            state = AstraState.Speaking(mood = Emotion.Happy),
            emotion = Emotion.Happy
        )
        if (!isReady) {
            pendingQueue.addLast(text)
            return
        }

        speakInternal(text)
    }

    private fun speakInternal(text: String) {
        tts?.speak(
            text,
            TextToSpeech.QUEUE_ADD,
            null,
            System.currentTimeMillis().toString()
        )
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        OverlayUiStateStore.set(
            state = AstraState.Idle,
            emotion = Emotion.Neutral
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEXT = "tts_text"
    }
}
