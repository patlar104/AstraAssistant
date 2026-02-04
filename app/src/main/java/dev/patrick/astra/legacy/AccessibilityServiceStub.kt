package dev.patrick.astra.legacy

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dev.patrick.astra.diagnostics.DiagnosticsLog

/**
 * Skeleton accessibility service. Later this will observe the UI,
 * perform gestures, and help Astra automate navigation on the phone.
 */
class AstraAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Observe events and feed them into Astra's brain.
    }

    override fun onInterrupt() {
        DiagnosticsLog.w(TAG, "Accessibility service interrupted")
        instance = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        DiagnosticsLog.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        instance = null
        DiagnosticsLog.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AstraAccessibility"

        @Volatile
        private var instance: AstraAccessibilityService? = null

        fun getInstance(): AstraAccessibilityService? = instance
    }
}
