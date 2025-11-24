package dev.patrick.astra.assistant

import android.util.Log
import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BrainController"

/**
 * Simple bridge between the UI layer and the brain.
 * Delegates work to [Brain] and surfaces the result.
 */
class BrainController(
    private val brain: Brain,
    private val scope: CoroutineScope
) {

    fun submitUserMessage(
        text: String,
        onResult: (BrainResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        if (text.isBlank()) return

        scope.launch {
            try {
                val result = brain.handleUserUtterance(text)
                logResult(result)

                onResult(result)
            } catch (t: Throwable) {
                Log.e(TAG, "Error while handling message", t)
                onError(t)
            }
        }
    }

    private fun logResult(result: BrainResult) {
        when (result) {
            is BrainResult.DirectReply -> {
                Log.d(
                    TAG,
                    "Result=DirectReply intent=${result.parsedIntent?.type} text=${result.text}"
                )
            }

            is BrainResult.ActionRequired -> {
                val plan = result.plan
                val stepCount = plan.steps.size
                val summary = plan.summary.orEmpty()
                Log.d(
                    TAG,
                    "Result=ActionRequired intent=${result.parsedIntent.type} steps=$stepCount summary=$summary"
                )
            }

            is BrainResult.Ignored -> {
                val intentType = result.parsedIntent?.type
                Log.d(TAG, "Result=Ignored intent=$intentType")
            }
        }
    }
}
