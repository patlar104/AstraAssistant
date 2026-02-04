package dev.patrick.astra.services.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

interface TtsEngine {
    fun setLanguage(locale: Locale)
    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener)
    fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String)
    val isSpeaking: Boolean
    fun stop()
    fun shutdown()
}

fun interface TtsEngineFactory {
    fun create(context: Context, onInit: (Int) -> Unit): TtsEngine
}

class AndroidTtsEngine(
    context: Context,
    onInit: (Int) -> Unit
) : TtsEngine {
    private val tts = TextToSpeech(context) { status -> onInit(status) }

    override fun setLanguage(locale: Locale) {
        tts.language = locale
    }

    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        tts.setOnUtteranceProgressListener(listener)
    }

    override fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String) {
        tts.speak(text, queueMode, params, utteranceId)
    }

    override val isSpeaking: Boolean
        get() = tts.isSpeaking

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() {
        tts.shutdown()
    }
}
