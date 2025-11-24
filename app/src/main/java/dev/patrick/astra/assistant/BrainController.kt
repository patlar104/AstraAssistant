package dev.patrick.astra.assistant

import android.util.Log
import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.ui.AstraState
import dev.patrick.astra.ui.Emotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BrainController"

/**
 * Simple bridge between the UI layer and the brain.
 * Updates the overlay state store while delegating actual work to [Brain].
 */
class BrainController(
    private val brain: Brain,
    private val overlayUiStateStore: OverlayUiStateStore,
    private val scope: CoroutineScope
) {

    fun submitUserMessage(
        text: String,
        onResult: (BrainResult) -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        if (text.isBlank()) return

        overlayUiStateStore.set(
            state = AstraState.Listening(intensity = 1f),
            emotion = Emotion.Focused
        )

        scope.launch {
            overlayUiStateStore.set(
                state = AstraState.Thinking(mood = Emotion.Focused),
                emotion = Emotion.Focused
            )

            try {
                val result = brain.handleUserUtterance(text)
                logResult(result)

                when (result) {
                    is BrainResult.DirectReply -> overlayUiStateStore.set(
                        state = AstraState.Speaking(mood = Emotion.Happy),
                        emotion = Emotion.Happy
                    )

                    is BrainResult.ActionRequired -> overlayUiStateStore.set(
                        state = AstraState.Thinking(mood = Emotion.Curious),
                        emotion = Emotion.Curious
                    )

                    is BrainResult.Ignored -> overlayUiStateStore.set(
                        state = AstraState.Idle,
                        emotion = Emotion.Neutral
                    )
                }

                onResult(result)
            } catch (t: Throwable) {
                Log.e(TAG, "Error while handling message", t)
                onError(t)
                overlayUiStateStore.set(
                    state = AstraState.Error(reason = t.message),
                    emotion = Emotion.Concerned
                )
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
