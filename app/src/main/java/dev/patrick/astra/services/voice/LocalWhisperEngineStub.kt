package dev.patrick.astra.services.voice

/**
 * Stub implementation for a future local Whisper-based STT engine.
 *
 * Possible future design:
 * - Capture audio from the microphone using AudioRecord.
 * - Stream raw PCM audio over LAN/Wi-Fi to a local server running Whisper
 *   (e.g., on a MacBook, desktop, or another device).
 * - Alternatively, invoke a native Whisper library directly on-device.
 *
 * For now, this is only a TODO placeholder and does nothing at runtime.
 */
class LocalWhisperEngineStub : TranscriptionEngine {
    override var lastError: String? = null
        private set

    override fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (TranscriptionError) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        onListeningChanged(false)
        val message = "LocalWhisperEngineStub is not implemented yet."
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
