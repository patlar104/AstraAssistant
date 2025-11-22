package dev.patrick.astra.assistant

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.ArrayDeque

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
            isReady = true

            // Speak queued messages
            while (pendingQueue.isNotEmpty()) {
                val msg = pendingQueue.removeFirst()
                tts?.speak(
                    msg,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    System.currentTimeMillis().toString()
                )
            }
        }
    }

    private fun enqueueOrSpeak(text: String) {
        if (!isReady) {
            pendingQueue.addLast(text)
            return
        }

        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_TEXT = "tts_text"
    }
}