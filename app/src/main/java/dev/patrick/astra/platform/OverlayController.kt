package dev.patrick.astra.platform

import android.content.Context
import android.provider.Settings

class OverlayController(private val context: Context) {

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun showAssistantOverlay() {
        // Placeholder: wire Compose overlay when permissions are granted.
    }

    fun removeAssistantOverlay() {
        // Placeholder: remove overlay window.
    }
}
