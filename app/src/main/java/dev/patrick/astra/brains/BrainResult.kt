package dev.patrick.astra.brains

import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Outcome of processing a brain event.
 */
sealed class BrainResult {

    data class DirectReply(
        val text: String
    ) : BrainResult()

    data class ActionRequired(
        val intent: ParsedIntent,
        val plan: ActionPlan
    ) : BrainResult()

    data object Ignored : BrainResult()
}
