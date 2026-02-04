package dev.patrick.astra.legacy

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure

class AccessibilityBridge(private val context: Context) {

    fun openServiceSettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun isServiceReady(): Boolean {
        val enabledServices = Secure.getString(
            context.contentResolver,
            Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expected = "${context.packageName}/${AstraAccessibilityService::class.java.name}"
        return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
