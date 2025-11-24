package dev.patrick.astra.brains

import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Outcome of processing a brain event.
 */
sealed class BrainResult(
    open val parsedIntent: ParsedIntent?,
    open val plan: ActionPlan?
) {

    data class DirectReply(
        val text: String,
        override val parsedIntent: ParsedIntent? = null,
        override val plan: ActionPlan? = null
    ) : BrainResult(parsedIntent, plan)

    data class ActionRequired(
        override val parsedIntent: ParsedIntent,
        override val plan: ActionPlan.ExecuteDeviceActions
    ) : BrainResult(parsedIntent, plan)

    data class Ignored(
        override val parsedIntent: ParsedIntent? = null,
        override val plan: ActionPlan? = null
    ) : BrainResult(parsedIntent, plan)
}
