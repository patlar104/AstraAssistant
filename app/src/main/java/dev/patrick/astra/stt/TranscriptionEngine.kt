package dev.patrick.astra.stt

/**
 * Abstraction for speech-to-text engines.
 *
 * Implementations might include:
 * - SystemSpeechEngine: wraps Android's SpeechRecognizer (current behavior)
 * - LocalWhisperEngine: streams audio to a local Whisper server / native library
 * - CloudSttEngine: sends audio to a cloud STT API (e.g., Whisper API, GCP, Azure)
 *
 * This is intentionally minimal for now; we can extend it later as we learn more
 * about the requirements (streaming, partial results, multi-language, etc.).
 */
interface TranscriptionEngine {

    /**
     * Start capturing the user's speech and transcribing it.
     *
     * @param onFinalResult callback invoked with the final recognized text
     * @param onError callback invoked with an error message or code
     * @param onListeningChanged callback invoked when listening state changes
     */
    fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    )

    /**
     * Stop listening for speech. Implementations may interpret this as
     * "finish current utterance" rather than an immediate hard stop.
     */
    fun stopListening()

    /**
     * Clean up any resources (e.g., microphones, native handles).
     */
    fun release()
}
