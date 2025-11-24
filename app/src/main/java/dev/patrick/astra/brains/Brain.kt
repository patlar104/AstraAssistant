package dev.patrick.astra.brains

import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.llm.BrainContext
import dev.patrick.astra.brains.llm.FakeLlmClient
import dev.patrick.astra.brains.llm.LlmClient

/**
 * Coordinates intent classification and routing.
 */
class Brain(
    private val llmClient: LlmClient = FakeLlmClient()
    // Later: inject persistence, metrics, or real LLM clients.
) {
    private var brainContext = BrainContext()

    suspend fun processEvent(event: BrainEvent): BrainResult {
        return when (event) {
            is BrainEvent.UserUtterance -> {
                brainContext = brainContext.withUpdatedUserMessage(event.text)
                val intent = llmClient.classifyIntent(
                    event.text,
                    brainContext
                )
                val result = SkillRouter.route(intent)
                handleResult(intent, result)
                result
            }
        }
    }

    private fun handleResult(intent: ParsedIntent, result: BrainResult) {
        when (result) {
            is BrainResult.DirectReply -> {
                brainContext = brainContext.withAssistantReply(result.text)
                println("Brain direct reply: ${result.text}")
            }
            is BrainResult.ActionRequired -> {
                val summary = when (val plan = result.plan) {
                    is ActionPlan.ExecuteDeviceActions -> {
                        "steps=${plan.steps.size} summary=${plan.summary}"
                    }
                    is ActionPlan.AnswerDirectly -> {
                        "answer=${plan.responseText}"
                    }
                    ActionPlan.NoOp -> "noop"
                }
                val assistantReply = when (val plan = result.plan) {
                    is ActionPlan.ExecuteDeviceActions -> plan.summary.orEmpty()
                    is ActionPlan.AnswerDirectly -> plan.responseText
                    ActionPlan.NoOp -> ""
                }
                brainContext = brainContext.withAssistantReply(assistantReply)
                println("Brain action required intent=${intent.type} confidence=${intent.confidence} $summary")
            }
            BrainResult.Ignored -> {
                println("Brain ignored intent=${intent.type} confidence=${intent.confidence}")
            }
        }
    }
}

sealed class BrainEvent {
    data class UserUtterance(val text: String) : BrainEvent()
}
