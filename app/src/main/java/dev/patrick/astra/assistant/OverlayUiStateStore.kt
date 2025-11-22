package dev.patrick.astra.assistant

import dev.patrick.astra.ui.AstraState
import dev.patrick.astra.ui.Emotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central UI state for the Astra overlay orb.
 *
 * This is intentionally simple and static for now:
 * - OverlayService observes this and renders the orb.
 * - ViewModel / TTS / STT / brain update it as the assistant changes activity.
 *
 * In the future, this could be refactored into DI, but for now a small singleton
 * keeps the architecture straightforward.
 */
data class OverlayUiState(
    val state: AstraState = AstraState.Idle,
    val emotion: Emotion = Emotion.Neutral
)

object OverlayUiStateStore {

    private val _overlayUiState = MutableStateFlow(OverlayUiState())
    val overlayUiState: StateFlow<OverlayUiState> = _overlayUiState.asStateFlow()

    /**
     * Hard-set state + emotion.
     */
    fun set(
        state: AstraState,
        emotion: Emotion = Emotion.Neutral
    ) {
        _overlayUiState.value = OverlayUiState(
            state = state,
            emotion = emotion
        )
    }

    /**
     * Incremental update, keeps other fields intact.
     */
    fun update(
        state: AstraState? = null,
        emotion: Emotion? = null
    ) {
        _overlayUiState.value = _overlayUiState.value.copy(
            state = state ?: _overlayUiState.value.state,
            emotion = emotion ?: _overlayUiState.value.emotion
        )
    }
}
