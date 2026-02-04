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

    fun dispatch(event: AssistantEvent) {
        _visualState.value = AssistantStateReducer.reduce(_visualState.value, event)
    }
}
