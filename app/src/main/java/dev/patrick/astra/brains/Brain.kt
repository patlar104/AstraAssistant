package dev.patrick.astra.brains

import android.util.Log
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.llm.BrainContext
import dev.patrick.astra.brains.llm.FakeLlmClient
import dev.patrick.astra.brains.llm.LlmClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "Brain"

/**
 * Coordinates intent classification and routing.
 */
class Brain(
    private val llmClient: LlmClient = FakeLlmClient(),
    private val skillRouter: SkillRouter = SkillRouter(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private var brainContext = BrainContext()

    suspend fun handleUserUtterance(
        text: String,
        context: BrainContext = brainContext
    ): BrainResult = withContext(dispatcher) {
        Log.d(TAG, "Handling user utterance: $text")

        val updatedContext = context.withUpdatedUserMessage(text)

        val parsedIntent = llmClient.classifyIntent(
            text = text,
            context = updatedContext
        )
        val plan = skillRouter.buildPlan(parsedIntent)

        val result = when (plan) {
            is ActionPlan.AnswerDirectly -> {
                val replyText = plan.responseText.takeIf { it.isNotBlank() }
                    ?: llmClient.generateReply(text, updatedContext)
                brainContext = updatedContext.withAssistantReply(replyText)
                BrainResult.DirectReply(
                    text = replyText,
                    parsedIntent = parsedIntent,
                    plan = plan.copy(responseText = replyText)
                )
            }

            is ActionPlan.ExecuteDeviceActions -> {
                brainContext = updatedContext.withAssistantReply(plan.summary.orEmpty())
                BrainResult.ActionRequired(
                    parsedIntent = parsedIntent,
                    plan = plan
                )
            }

            ActionPlan.NoOp -> {
                brainContext = updatedContext
                BrainResult.Ignored(
                    parsedIntent = parsedIntent,
                    plan = plan
                )
            }
        }

        logOutcome(parsedIntent, result)
        result
    }

    private fun logOutcome(intent: ParsedIntent, result: BrainResult) {
        when (result) {
            is BrainResult.DirectReply -> {
                Log.d(
                    TAG,
                    "Direct reply intent=${intent.type} confidence=${intent.confidence} text=${result.text}"
                )
            }

            is BrainResult.ActionRequired -> {
                val plan = result.plan
                Log.d(
                    TAG,
                    "Action required intent=${intent.type} steps=${plan.steps.size} summary=${plan.summary}"
                )
            }

            is BrainResult.Ignored -> {
                Log.d(
                    TAG,
                    "Ignored intent=${intent.type} confidence=${intent.confidence}"
                )
            }
        }
    }
}
