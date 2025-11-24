package dev.patrick.astra.legacy

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Skeleton accessibility service. Later this will observe the UI,
 * perform gestures, and help Astra automate navigation on the phone.
 */
class AstraAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // TODO: Observe events and feed them into Astra's brain.
    }

    override fun onInterrupt() {
        // TODO: Clean up / cancel any in-progress actions if needed.
    }
}
