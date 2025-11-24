package dev.patrick.astra.services.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Helper class around Android's SpeechRecognizer.
 * It notifies the caller about final results, errors,
 * and listening state changes.
 */
class SpeechRecognizerManager(
    context: Context,
    private val onFinalResult: (String) -> Unit,
    private val onError: (Int) -> Unit = {},
    private val onListeningChanged: (Boolean) -> Unit = {}
) : RecognitionListener {

    private val speechRecognizer: SpeechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context.applicationContext)

    private val recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    init {
        speechRecognizer.setRecognitionListener(this)
    }

    fun startListening() {
        onListeningChanged(true)
        speechRecognizer.startListening(recognizerIntent)
    }

    fun stopListening() {
        onListeningChanged(false)
        speechRecognizer.stopListening()
    }

    fun destroy() {
        speechRecognizer.destroy()
    }

    // RecognitionListener implementations

    override fun onReadyForSpeech(params: Bundle?) {
        // no-op
    }

    override fun onBeginningOfSpeech() {
        // no-op
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Could be used to animate input level, left as no-op for now.
    }

    override fun onBufferReceived(buffer: ByteArray?) {
        // no-op
    }

    override fun onEndOfSpeech() {
        // no-op
    }

    override fun onError(error: Int) {
        onListeningChanged(false)
        onError.invoke(error)
    }

    override fun onResults(results: Bundle?) {
        onListeningChanged(false)
        val matches = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull()
        if (!text.isNullOrBlank()) {
            onFinalResult.invoke(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        // For now, we ignore partial results.
    }

    override fun onEvent(eventType: Int, params: Bundle?) {
        // no-op
    }
}
