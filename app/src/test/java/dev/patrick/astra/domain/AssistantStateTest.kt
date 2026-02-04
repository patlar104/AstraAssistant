package dev.patrick.astra.domain

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantStateTest {

    @Test
    fun toEnergy_mapsPhaseValues() {
        val idle = AssistantVisualState(AssistantPhase.Idle, Emotion.Neutral).toEnergy()
        val listening = AssistantVisualState(AssistantPhase.Listening, Emotion.Neutral).toEnergy()
        val speaking = AssistantVisualState(AssistantPhase.Speaking, Emotion.Neutral).toEnergy()
        val error = AssistantVisualState(AssistantPhase.Error(reason = null), Emotion.Neutral).toEnergy()

        assertEquals(0.25f, idle.energy, 0.0001f)
        assertEquals(0.6f, listening.energy, 0.0001f)
        assertEquals(0.9f, speaking.energy, 0.0001f)
        assertEquals(0.8f, error.energy, 0.0001f)
    }

    @Test
    fun expressionFor_appliesSpeakingOverrides() {
        val shape = expressionFor(AssistantPhase.Speaking, Emotion.Excited)

        assertEquals(1.07f, shape.squashX, 0.0001f)
        assertEquals(0.94f, shape.squashY, 0.0001f)
        assertEquals(6f, shape.tiltDegrees, 0.0001f)
        assertEquals(0.42f, shape.mouthCurveAmount, 0.0001f)
    }

    @Test
    fun expressionFor_handlesErrorShape() {
        val shape = expressionFor(AssistantPhase.Error(reason = "oops"), Emotion.Happy)

        assertEquals(0.9f, shape.squashX, 0.0001f)
        assertEquals(1.1f, shape.squashY, 0.0001f)
        assertEquals(-6f, shape.tiltDegrees, 0.0001f)
        assertEquals(-0.4f, shape.mouthCurveAmount, 0.0001f)
    }

    @Test
    fun expressionFor_handlesListeningConcerned() {
        val shape = expressionFor(AssistantPhase.Listening, Emotion.Concerned)

        assertEquals(0.95f, shape.squashX, 0.0001f)
        assertEquals(1.05f, shape.squashY, 0.0001f)
        assertEquals(-2.5f, shape.tiltDegrees, 0.0001f)
        assertEquals(-0.18f, shape.mouthCurveAmount, 0.0001f)
    }

    @Test
    fun toPalette_returnsExpectedColors() {
        val palette = Emotion.Curious.toPalette()

        assertEquals(Color(0xFF38D4FF), palette.coreColor)
        assertEquals(Color(0x6638D4FF), palette.auraColor)
        assertEquals(Color(0x1A38D4FF), palette.glowColor)
        assertEquals(Color(0xFFE7FBFF), palette.eyeColor)
    }

    @Test
    fun blendPalettes_clampsInputRange() {
        val p1 = Emotion.Neutral.toPalette()
        val p2 = Emotion.Happy.toPalette()

        val below = blendPalettes(p1, p2, -0.5f)
        val above = blendPalettes(p1, p2, 2.5f)

        assertEquals(p1, below)
        assertEquals(p2, above)
    }
}
