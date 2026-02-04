package dev.patrick.astra.domain

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AssistantStateStoreTest {

    @Before
    fun setUp() {
        AssistantStateStore.set(
            phase = AssistantPhase.Idle,
            emotion = Emotion.Neutral
        )
    }

    @Test
    fun set_updatesPhaseAndDefaultsEmotion() {
        AssistantStateStore.set(phase = AssistantPhase.Listening)

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Listening, state.phase)
        assertEquals(Emotion.Neutral, state.emotion)
    }

    @Test
    fun update_allowsPartialUpdates() {
        AssistantStateStore.update(emotion = Emotion.Happy)

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Idle, state.phase)
        assertEquals(Emotion.Happy, state.emotion)
    }

    @Test
    fun setError_setsConcernedEmotion() {
        AssistantStateStore.setError("tts")

        val state = AssistantStateStore.visualState.value
        assertEquals(AssistantPhase.Error("tts"), state.phase)
        assertEquals(Emotion.Concerned, state.emotion)
    }
}
