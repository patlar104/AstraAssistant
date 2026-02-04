package dev.patrick.astra.legacy

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.Settings.Secure
import android.view.accessibility.AccessibilityNodeInfo
import dev.patrick.astra.diagnostics.DiagnosticsLog

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

    fun getRootNode(): AccessibilityNodeInfo? {
        val service = AstraAccessibilityService.getInstance()
        if (service == null) {
            DiagnosticsLog.w(TAG, "Accessibility service not connected")
            return null
        }
        val root = service.rootInActiveWindow
        if (root == null) {
            DiagnosticsLog.w(TAG, "Accessibility root is null")
        }
        return root
    }

    fun performGlobalAction(action: Int): Boolean {
        val service = AstraAccessibilityService.getInstance()
        if (service == null) {
            DiagnosticsLog.w(TAG, "Accessibility service not connected")
            return false
        }
        return service.performGlobalAction(action)
    }

    companion object {
        private const val TAG = "AccessibilityBridge"
        const val GLOBAL_ACTION_BACK = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
        const val GLOBAL_ACTION_HOME = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
        const val GLOBAL_ACTION_RECENTS =
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
    }
}
