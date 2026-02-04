package dev.patrick.astra.services.voice

import android.content.Context
import android.speech.SpeechRecognizer

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
    override var lastError: String? = null
        private set

    private fun ensureManager(
        onFinalResult: (String) -> Unit,
        onError: (TranscriptionError) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        if (manager == null) {
            manager = SpeechRecognizerManager(
                context = appContext,
                onFinalResult = { text ->
                    onFinalResult(text)
                },
                onError = { errorCode ->
                    val isTransient = errorCode in TRANSIENT_ERROR_CODES
                    val message = "System STT error code: $errorCode"
                    lastError = message
                    onError(
                        TranscriptionError(
                            code = errorCode,
                            message = message,
                            isTransient = isTransient
                        )
                    )
                },
                onListeningChanged = { listening ->
                    onListeningChanged(listening)
                }
            )
        }
    }

    override fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (TranscriptionError) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        if (!isAvailable()) {
            val message = "System STT is not available on this device."
            lastError = message
            onError(
                TranscriptionError(
                    code = null,
                    message = message,
                    isTransient = false
                )
            )
            return
        }
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

    override fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    companion object {
        private val TRANSIENT_ERROR_CODES = setOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS
        )
    }
}
