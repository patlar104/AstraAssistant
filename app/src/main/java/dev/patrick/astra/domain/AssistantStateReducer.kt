package dev.patrick.astra.domain

object AssistantStateReducer {

    fun reduce(
        current: AssistantVisualState,
        event: AssistantEvent
    ): AssistantVisualState {
        return when (event) {
            is AssistantEvent.Error -> AssistantVisualState(
                phase = AssistantPhase.Error(event.reason),
                emotion = Emotion.Concerned
            )

            AssistantEvent.SpeakingStarted -> AssistantVisualState(
                phase = AssistantPhase.Speaking,
                emotion = Emotion.Happy
            )

            AssistantEvent.SpeakingStopped -> when (current.phase) {
                is AssistantPhase.Speaking -> idleState()
                else -> current
            }

            AssistantEvent.ListeningStarted -> {
                if (current.phase is AssistantPhase.Speaking) {
                    current
                } else {
                    AssistantVisualState(
                        phase = AssistantPhase.Listening,
                        emotion = Emotion.Focused
                    )
                }
            }

            AssistantEvent.ListeningStopped -> when (current.phase) {
                is AssistantPhase.Listening -> idleState()
                else -> current
            }

            AssistantEvent.ThinkingStarted -> {
                if (current.phase is AssistantPhase.Speaking || current.phase is AssistantPhase.Listening) {
                    current
                } else {
                    AssistantVisualState(
                        phase = AssistantPhase.Thinking,
                        emotion = Emotion.Focused
                    )
                }
            }

            AssistantEvent.ThinkingStopped -> when (current.phase) {
                is AssistantPhase.Thinking -> idleState()
                else -> current
            }

            AssistantEvent.ResetToIdle -> idleState()
        }
    }

    private fun idleState(): AssistantVisualState {
        return AssistantVisualState(
            phase = AssistantPhase.Idle,
            emotion = Emotion.Neutral
        )
    }
}
