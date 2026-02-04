package dev.patrick.astra.services.voice

/**
 * Stub implementation for a future cloud-based STT engine.
 *
 * Possible future design:
 * - Record audio to a temporary file (e.g., WAV/FLAC/OGG).
 * - Upload to a backend service that:
 *      - Calls a cloud STT API (OpenAI Whisper API, Google Cloud STT, Azure, etc.).
 *      - Returns a high-quality transcript (with punctuation and multi-lingual support).
 * - Replace or augment the SystemSpeechEngine with this implementation.
 *
 * This class is intentionally non-functional for now; it only reports a TODO error.
 */
class CloudSttEngineStub : TranscriptionEngine {
    override var lastError: String? = null
        private set

    override fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (TranscriptionError) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        onListeningChanged(false)
        val message = "CloudSttEngineStub is not implemented yet."
        lastError = message
        onError(
            TranscriptionError(
                code = null,
                message = message,
                isTransient = false
            )
        )
    }

    override fun stopListening() {
        // no-op
    }

    override fun release() {
        // no-op
    }

    override fun isAvailable(): Boolean = false
}
