package dev.patrick.astra.stt

import android.content.Context
import dev.patrick.astra.assistant.SpeechRecognizerManager

/**
 * TranscriptionEngine implementation that uses the existing
 * SpeechRecognizerManager, which wraps Android's built-in SpeechRecognizer.
 *
 * This preserves the current behavior but routes it through the new abstraction.
 */
class SystemSpeechEngine(
    context: Context
) : TranscriptionEngine {

    private val appContext = context.applicationContext

    private var manager: SpeechRecognizerManager? = null

    private fun ensureManager(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        if (manager == null) {
            manager = SpeechRecognizerManager(
                context = appContext,
                onFinalResult = { text ->
                    onFinalResult(text)
                },
                onError = { errorCode ->
                    onError("System STT error code: $errorCode")
                },
                onListeningChanged = { listening ->
                    onListeningChanged(listening)
                }
            )
        }
    }

    override fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        ensureManager(onFinalResult, onError, onListeningChanged)
        manager?.startListening()
    }

    override fun stopListening() {
        manager?.stopListening()
    }

    override fun release() {
        manager?.destroy()
        manager = null
    }
}
