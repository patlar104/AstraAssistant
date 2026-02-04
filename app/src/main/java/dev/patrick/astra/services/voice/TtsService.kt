package dev.patrick.astra.services.voice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.ArrayDeque
import dev.patrick.astra.diagnostics.DiagnosticsLog
import dev.patrick.astra.domain.AssistantEvent
import dev.patrick.astra.domain.AssistantStateStore

class TtsService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var initTimeoutPosted = false

    // Queue for texts received before initialization
    private val pendingQueue = ArrayDeque<String>()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        postInitTimeout()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_TEXT)?.let { text ->
            enqueueOrSpeak(text)
        }
        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            cancelInitTimeout()
            tts?.language = Locale.US
            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        // no-op
                    }

                    override fun onDone(utteranceId: String?) {
                        AssistantStateStore.dispatch(AssistantEvent.SpeakingStopped)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        DiagnosticsLog.e(TAG, "TTS error code=$errorCode")
                        AssistantStateStore.dispatch(
                            AssistantEvent.Error(reason = "tts_error_$errorCode")
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
        AssistantStateStore.dispatch(AssistantEvent.SpeakingStarted)
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
        cancelInitTimeout()
        AssistantStateStore.dispatch(AssistantEvent.ResetToIdle)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEXT = "tts_text"
        private const val TAG = "TtsService"
        private const val INIT_TIMEOUT_MS = 5_000L
    }

    private fun postInitTimeout() {
        if (initTimeoutPosted) return
        initTimeoutPosted = true
        handler.postDelayed({
            if (!isReady) {
                DiagnosticsLog.e(TAG, "TTS initialization timeout")
                AssistantStateStore.dispatch(AssistantEvent.Error(reason = "tts_init_timeout"))
            }
        }, INIT_TIMEOUT_MS)
    }

    private fun cancelInitTimeout() {
        if (!initTimeoutPosted) return
        handler.removeCallbacksAndMessages(null)
        initTimeoutPosted = false
    }
}
