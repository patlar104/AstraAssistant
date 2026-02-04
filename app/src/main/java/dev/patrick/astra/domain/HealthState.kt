package dev.patrick.astra.domain

data class HealthState(
    val overlayPermissionGranted: Boolean,
    val voiceAvailable: Boolean,
    val voiceError: String? = null,
    val accessibilityEnabled: Boolean = false
)
