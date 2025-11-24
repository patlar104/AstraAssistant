package dev.patrick.astra.brains

import dev.patrick.astra.brains.actions.ActionInterpreter
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Routes intents to the correct skill handler.
 */
class SkillRouter(
    private val actionInterpreter: ActionInterpreter = ActionInterpreter
) {
    fun buildPlan(intent: ParsedIntent): ActionPlan {
        return actionInterpreter.fromIntent(intent)
    }
}
