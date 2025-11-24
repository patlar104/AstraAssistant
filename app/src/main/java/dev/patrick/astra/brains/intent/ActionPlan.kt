package dev.patrick.astra.brains.intent

/**
 * Planned response based on the user's intent.
 */
sealed class ActionPlan {

    data class AnswerDirectly(
        val responseText: String
    ) : ActionPlan()

    data class ExecuteDeviceActions(
        val steps: List<DeviceActionStep>,
        val summary: String? = null
    ) : ActionPlan()

    data object NoOp : ActionPlan()
}

/**
 * Device-level actions that can be executed by a downstream engine.
 */
sealed class DeviceActionStep {

    data class OpenApp(
        val appNameHint: String? = null,
        val packageName: String? = null
    ) : DeviceActionStep()

    data class SendMessage(
        val recipientHint: String,
        val message: String
    ) : DeviceActionStep()

    data class ShowText(
        val text: String
    ) : DeviceActionStep()

    data class NavigateToSettings(
        val sectionHint: String
    ) : DeviceActionStep()

    data class SystemControl(
        val controlType: SystemControlType,
        val value: String? = null
    ) : DeviceActionStep()
}

enum class SystemControlType {
    TOGGLE_WIFI,
    TOGGLE_BLUETOOTH,
    TOGGLE_DND,
    ADJUST_BRIGHTNESS,
    ADJUST_VOLUME
}
