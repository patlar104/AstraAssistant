package dev.patrick.astra.services.voice

/**
 * Enumerates possible STT backends.
 *
 * At runtime we currently always use SYSTEM.
 * In the future we might allow user selection or dynamic fallback.
 */
enum class SttBackend {
    SYSTEM,
    LOCAL_WHISPER,
    CLOUD_STT
}
