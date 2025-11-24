package dev.patrick.astra.brains

import dev.patrick.astra.brains.actions.ActionInterpreter
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Routes intents to the correct skill handler.
 */
object SkillRouter {
    fun route(intent: ParsedIntent): BrainResult {
        return when (intent.type) {
            IntentType.ASK_QUESTION -> {
                BrainResult.DirectReply("Let me think about that...")
            }
            IntentType.SMALL_TALK -> {
                BrainResult.DirectReply("I'm here.")
            }
            else -> {
                val plan = ActionInterpreter.fromIntent(intent)
                when (plan) {
                    is ActionPlan.NoOp -> BrainResult.Ignored
                    else -> BrainResult.ActionRequired(
                        intent = intent,
                        plan = plan
                    )
                }
            }
        }
    }
}
