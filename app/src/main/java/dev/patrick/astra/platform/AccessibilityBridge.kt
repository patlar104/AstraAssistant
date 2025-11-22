package dev.patrick.astra.platform

import android.content.Context
import android.content.Intent
import android.provider.Settings

class AccessibilityBridge(private val context: Context) {

    fun openServiceSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isServiceReady(): Boolean = false
}
