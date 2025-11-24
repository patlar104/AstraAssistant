package dev.patrick.astra.brains.actions

import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.DeviceActionStep
import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.intent.SystemControlType

/**
 * Converts parsed intents into structured action plans.
 */
object ActionInterpreter {

    fun fromIntent(intent: ParsedIntent): ActionPlan {
        if (intent.confidence < 0.3f) {
            return ActionPlan.NoOp
        }

        return when (intent.type) {
            IntentType.ASK_QUESTION,
            IntentType.SMALL_TALK -> {
                ActionPlan.AnswerDirectly(
                    responseText = intent.rawText
                )
            }

            IntentType.OPEN_APP -> buildOpenAppPlan(intent)
            IntentType.SEND_MESSAGE -> buildSendMessagePlan(intent)
            IntentType.TRANSLATE_TEXT -> buildTranslatePlan(intent)
            IntentType.CONTROL_DEVICE -> buildControlPlan(intent)
            IntentType.SEARCH_WEB -> buildSearchPlan(intent)
            IntentType.GET_WEATHER -> buildWeatherPlan(intent)

            IntentType.UNKNOWN -> ActionPlan.NoOp
        }
    }

    private fun buildOpenAppPlan(intent: ParsedIntent): ActionPlan {
        val appNameHint = intent.arguments["appName"] ?: intent.arguments["target"]
        val packageName = intent.arguments["package"]

        if (appNameHint == null && packageName == null) {
            return ActionPlan.NoOp
        }

        val step = DeviceActionStep.OpenApp(
            appNameHint = appNameHint,
            packageName = packageName
        )
        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Open app ${packageName ?: appNameHint}"
        )
    }

    private fun buildSendMessagePlan(intent: ParsedIntent): ActionPlan {
        val recipient = intent.arguments["recipient"] ?: intent.arguments["target"]
        val message = intent.arguments["text"] ?: intent.arguments["message"]

        if (recipient.isNullOrBlank() || message.isNullOrBlank()) {
            return ActionPlan.NoOp
        }

        val step = DeviceActionStep.SendMessage(
            recipientHint = recipient,
            message = message
        )

        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Send message to $recipient"
        )
    }

    private fun buildTranslatePlan(intent: ParsedIntent): ActionPlan {
        val text = intent.arguments["text"] ?: intent.rawText
        val targetLang = intent.arguments["targetLang"] ?: "en"

        val displayText = "Translate to $targetLang:\n$text"

        val step = DeviceActionStep.ShowText(displayText)

        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Show translation request"
        )
    }

    private fun buildControlPlan(intent: ParsedIntent): ActionPlan {
        val controlKey = intent.arguments["control"]?.lowercase() ?: return ActionPlan.NoOp

        val controlType = when {
            "wifi" in controlKey -> SystemControlType.TOGGLE_WIFI
            "bluetooth" in controlKey -> SystemControlType.TOGGLE_BLUETOOTH
            "dnd" in controlKey || "do not disturb" in controlKey -> SystemControlType.TOGGLE_DND
            "brightness" in controlKey -> SystemControlType.ADJUST_BRIGHTNESS
            "volume" in controlKey -> SystemControlType.ADJUST_VOLUME
            else -> return ActionPlan.NoOp
        }

        val value = intent.arguments["value"]

        val step = DeviceActionStep.SystemControl(
            controlType = controlType,
            value = value
        )

        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Control system: $controlType"
        )
    }

    private fun buildSearchPlan(intent: ParsedIntent): ActionPlan {
        val query = intent.arguments["query"] ?: intent.rawText
        val step = DeviceActionStep.ShowText("Search for: $query")

        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Search the web"
        )
    }

    private fun buildWeatherPlan(intent: ParsedIntent): ActionPlan {
        val location = intent.arguments["location"] ?: "current location"
        val step = DeviceActionStep.ShowText("Get weather for $location")

        return ActionPlan.ExecuteDeviceActions(
            steps = listOf(step),
            summary = "Get weather"
        )
    }
}
