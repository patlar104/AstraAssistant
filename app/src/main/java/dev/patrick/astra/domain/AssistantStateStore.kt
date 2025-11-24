package dev.patrick.astra.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for Astra's visual/phase state.
 */
object AssistantStateStore {

    private val _visualState = MutableStateFlow(
        AssistantVisualState(
            phase = AssistantPhase.Idle,
            emotion = Emotion.Neutral
        )
    )
    val visualState: StateFlow<AssistantVisualState> = _visualState.asStateFlow()

    fun set(
        phase: AssistantPhase,
        emotion: Emotion = _visualState.value.emotion
    ) {
        _visualState.value = AssistantVisualState(
            phase = phase,
            emotion = emotion
        )
    }

    fun update(
        phase: AssistantPhase? = null,
        emotion: Emotion? = null
    ) {
        _visualState.value = AssistantVisualState(
            phase = phase ?: _visualState.value.phase,
            emotion = emotion ?: _visualState.value.emotion
        )
    }

    fun setError(reason: String?) {
        set(
            phase = AssistantPhase.Error(reason),
            emotion = Emotion.Concerned
        )
    }
}
