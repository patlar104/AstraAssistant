package dev.patrick.astra.brains.actions

import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.DeviceActionStep
import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.intent.SystemControlType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionInterpreterTest {

    @Test
    fun fromIntent_returnsNoOpWhenConfidenceLow() {
        val intent = ParsedIntent(
            type = IntentType.OPEN_APP,
            arguments = mapOf("appName" to "Maps"),
            confidence = 0.2f,
            rawText = "open maps"
        )

        val plan = ActionInterpreter.fromIntent(intent)

        assertEquals(ActionPlan.NoOp, plan)
    }

    @Test
    fun fromIntent_answersDirectlyForSmallTalk() {
        val intent = ParsedIntent(
            type = IntentType.SMALL_TALK,
            arguments = emptyMap(),
            confidence = 0.9f,
            rawText = "Hello there"
        )

        val plan = ActionInterpreter.fromIntent(intent)

        assertTrue(plan is ActionPlan.AnswerDirectly)
        val answer = plan as ActionPlan.AnswerDirectly
        assertEquals("Hello there", answer.responseText)
    }

    @Test
    fun fromIntent_buildsOpenAppPlan() {
        val intent = ParsedIntent(
            type = IntentType.OPEN_APP,
            arguments = mapOf("appName" to "Clock"),
            confidence = 0.8f,
            rawText = "open clock"
        )

        val plan = ActionInterpreter.fromIntent(intent)

        assertTrue(plan is ActionPlan.ExecuteDeviceActions)
        val execute = plan as ActionPlan.ExecuteDeviceActions
        assertEquals("Open app Clock", execute.summary)
        assertEquals(1, execute.steps.size)
        val step = execute.steps.first() as DeviceActionStep.OpenApp
        assertEquals("Clock", step.appNameHint)
    }

    @Test
    fun fromIntent_missingMessageReturnsNoOp() {
        val intent = ParsedIntent(
            type = IntentType.SEND_MESSAGE,
            arguments = mapOf("recipient" to "Sam"),
            confidence = 0.8f,
            rawText = "message Sam"
        )

        val plan = ActionInterpreter.fromIntent(intent)

        assertEquals(ActionPlan.NoOp, plan)
    }

    @Test
    fun fromIntent_buildsSystemControlPlan() {
        val intent = ParsedIntent(
            type = IntentType.CONTROL_DEVICE,
            arguments = mapOf("control" to "wifi"),
            confidence = 0.8f,
            rawText = "turn off wifi"
        )

        val plan = ActionInterpreter.fromIntent(intent)

        assertTrue(plan is ActionPlan.ExecuteDeviceActions)
        val execute = plan as ActionPlan.ExecuteDeviceActions
        val step = execute.steps.first() as DeviceActionStep.SystemControl
        assertEquals(SystemControlType.TOGGLE_WIFI, step.controlType)
    }
}
