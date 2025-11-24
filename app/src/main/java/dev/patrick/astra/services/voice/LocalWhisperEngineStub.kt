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

    override fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit,
        onListeningChanged: (Boolean) -> Unit
    ) {
        onListeningChanged(false)
        onError("LocalWhisperEngineStub is not implemented yet.")
    }

    override fun stopListening() {
        // no-op
    }

    override fun release() {
        // no-op
    }
}
