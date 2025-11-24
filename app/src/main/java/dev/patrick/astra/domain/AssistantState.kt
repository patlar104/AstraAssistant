package dev.patrick.astra.domain

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

sealed interface AssistantPhase {
    data object Idle : AssistantPhase
    data object Listening : AssistantPhase
    data object Thinking : AssistantPhase
    data object Speaking : AssistantPhase
    data class Error(val reason: String?) : AssistantPhase
}

data class AssistantVisualState(
    val phase: AssistantPhase,
    val emotion: Emotion
)

enum class Emotion {
    Neutral,
    Curious,
    Happy,
    Concerned,
    Focused,
    Excited
}

@Immutable
data class EmotionPalette(
    val coreColor: Color,
    val auraColor: Color,
    val glowColor: Color,
    val eyeColor: Color
)

@Immutable
data class StateEnergy(
    val energy: Float,
    val pulseSpeedScale: Float,
    val auraBoost: Float
)

@Immutable
data class ExpressionShape(
    val squashX: Float,
    val squashY: Float,
    val tiltDegrees: Float,
    val wobbleFactor: Float,
    val eyeSeparationFactor: Float,
    val eyeTiltDegrees: Float,
    val mouthCurveAmount: Float
)

fun AssistantVisualState.toEnergy(): StateEnergy =
    when (phase) {
        AssistantPhase.Idle -> StateEnergy(energy = 0.25f, pulseSpeedScale = 1f, auraBoost = 1f)
        AssistantPhase.Listening -> StateEnergy(energy = 0.6f, pulseSpeedScale = 1.2f, auraBoost = 1.1f)
        AssistantPhase.Thinking -> StateEnergy(energy = 0.5f, pulseSpeedScale = 1.1f, auraBoost = 1.05f)
        AssistantPhase.Speaking -> StateEnergy(energy = 0.9f, pulseSpeedScale = 1.35f, auraBoost = 1.15f)
        is AssistantPhase.Error -> StateEnergy(energy = 0.8f, pulseSpeedScale = 1.5f, auraBoost = 1.2f)
    }

fun expressionFor(phase: AssistantPhase, emotion: Emotion): ExpressionShape {
    return when (phase) {
        is AssistantPhase.Error -> {
            ExpressionShape(
                squashX = 0.9f,
                squashY = 1.1f,
                tiltDegrees = -6f,
                wobbleFactor = 0.12f,
                eyeSeparationFactor = 0.9f,
                eyeTiltDegrees = -8f,
                mouthCurveAmount = -0.4f
            )
        }

        AssistantPhase.Speaking -> {
            when (emotion) {
                Emotion.Excited -> ExpressionShape(
                    squashX = 1.07f,
                    squashY = 0.94f,
                    tiltDegrees = 6f,
                    wobbleFactor = 0.12f,
                    eyeSeparationFactor = 1.1f,
                    eyeTiltDegrees = 6f,
                    mouthCurveAmount = 0.42f
                )

                Emotion.Happy -> ExpressionShape(
                    squashX = 1.05f,
                    squashY = 0.95f,
                    tiltDegrees = 4f,
                    wobbleFactor = 0.1f,
                    eyeSeparationFactor = 1.08f,
                    eyeTiltDegrees = 4f,
                    mouthCurveAmount = 0.35f
                )

                Emotion.Concerned -> ExpressionShape(
                    squashX = 0.95f,
                    squashY = 1.05f,
                    tiltDegrees = -2f,
                    wobbleFactor = 0.1f,
                    eyeSeparationFactor = 0.95f,
                    eyeTiltDegrees = -3f,
                    mouthCurveAmount = -0.15f
                )

                else -> ExpressionShape(
                    squashX = 1.02f,
                    squashY = 0.98f,
                    tiltDegrees = 3f,
                    wobbleFactor = 0.1f,
                    eyeSeparationFactor = 1.05f,
                    eyeTiltDegrees = 2f,
                    mouthCurveAmount = 0.18f
                )
            }
        }

        AssistantPhase.Thinking -> {
            if (emotion == Emotion.Concerned) {
                ExpressionShape(
                    squashX = 0.92f,
                    squashY = 1.08f,
                    tiltDegrees = -5f,
                    wobbleFactor = 0.08f,
                    eyeSeparationFactor = 0.94f,
                    eyeTiltDegrees = -6f,
                    mouthCurveAmount = -0.2f
                )
            } else {
                ExpressionShape(
                    squashX = 0.94f,
                    squashY = 1.06f,
                    tiltDegrees = -4f,
                    wobbleFactor = 0.07f,
                    eyeSeparationFactor = 0.95f,
                    eyeTiltDegrees = -5f,
                    mouthCurveAmount = -0.1f
                )
            }
        }

        AssistantPhase.Listening -> {
            when (emotion) {
                Emotion.Focused, Emotion.Curious -> ExpressionShape(
                    squashX = 0.96f,
                    squashY = 1.04f,
                    tiltDegrees = 2f,
                    wobbleFactor = 0.06f,
                    eyeSeparationFactor = 1.05f,
                    eyeTiltDegrees = 3f,
                    mouthCurveAmount = 0.0f
                )

                Emotion.Happy -> ExpressionShape(
                    squashX = 1.02f,
                    squashY = 0.99f,
                    tiltDegrees = 3f,
                    wobbleFactor = 0.07f,
                    eyeSeparationFactor = 1.06f,
                    eyeTiltDegrees = 4f,
                    mouthCurveAmount = 0.22f
                )

                Emotion.Concerned -> ExpressionShape(
                    squashX = 0.95f,
                    squashY = 1.05f,
                    tiltDegrees = -2.5f,
                    wobbleFactor = 0.08f,
                    eyeSeparationFactor = 0.94f,
                    eyeTiltDegrees = -3f,
                    mouthCurveAmount = -0.18f
                )

                else -> ExpressionShape(
                    squashX = 0.98f,
                    squashY = 1.02f,
                    tiltDegrees = 1.5f,
                    wobbleFactor = 0.06f,
                    eyeSeparationFactor = 1.02f,
                    eyeTiltDegrees = 2f,
                    mouthCurveAmount = 0.08f
                )
            }
        }

        AssistantPhase.Idle -> {
            when (emotion) {
                Emotion.Happy -> ExpressionShape(
                    squashX = 1.03f,
                    squashY = 0.99f,
                    tiltDegrees = 2f,
                    wobbleFactor = 0.06f,
                    eyeSeparationFactor = 1.04f,
                    eyeTiltDegrees = 2f,
                    mouthCurveAmount = 0.18f
                )

                Emotion.Excited -> ExpressionShape(
                    squashX = 1.06f,
                    squashY = 0.97f,
                    tiltDegrees = 4f,
                    wobbleFactor = 0.08f,
                    eyeSeparationFactor = 1.07f,
                    eyeTiltDegrees = 3.5f,
                    mouthCurveAmount = 0.24f
                )

                Emotion.Curious -> ExpressionShape(
                    squashX = 0.99f,
                    squashY = 1.01f,
                    tiltDegrees = 1.5f,
                    wobbleFactor = 0.06f,
                    eyeSeparationFactor = 0.99f,
                    eyeTiltDegrees = 1f,
                    mouthCurveAmount = 0.1f
                )

                Emotion.Concerned -> ExpressionShape(
                    squashX = 0.93f,
                    squashY = 1.07f,
                    tiltDegrees = -3.5f,
                    wobbleFactor = 0.08f,
                    eyeSeparationFactor = 0.93f,
                    eyeTiltDegrees = -4f,
                    mouthCurveAmount = -0.22f
                )

                else -> ExpressionShape(
                    squashX = 1.0f,
                    squashY = 1.0f,
                    tiltDegrees = 0f,
                    wobbleFactor = 0.05f,
                    eyeSeparationFactor = 1.0f,
                    eyeTiltDegrees = 0f,
                    mouthCurveAmount = 0.1f
                )
            }
        }
    }
}

fun Emotion.toPalette(): EmotionPalette {
    return when (this) {
        Emotion.Happy -> EmotionPalette(
            coreColor = Color(0xFF3FD1A0),
            auraColor = Color(0x663FD1A0),
            glowColor = Color(0x1A3FD1A0),
            eyeColor = Color(0xFFF4FFF9)
        )

        Emotion.Excited -> EmotionPalette(
            coreColor = Color(0xFF9C6BFF),
            auraColor = Color(0x669C6BFF),
            glowColor = Color(0x1A9C6BFF),
            eyeColor = Color(0xFFF7F2FF)
        )

        Emotion.Curious -> EmotionPalette(
            coreColor = Color(0xFF38D4FF),
            auraColor = Color(0x6638D4FF),
            glowColor = Color(0x1A38D4FF),
            eyeColor = Color(0xFFE7FBFF)
        )

        Emotion.Focused -> EmotionPalette(
            coreColor = Color(0xFF1AA0FF),
            auraColor = Color(0x661AA0FF),
            glowColor = Color(0x1A1AA0FF),
            eyeColor = Color(0xFFE3F2FF)
        )

        Emotion.Concerned -> EmotionPalette(
            coreColor = Color(0xFFFFA04D),
            auraColor = Color(0x66FFA04D),
            glowColor = Color(0x1AFFA04D),
            eyeColor = Color(0xFFFFF6E5)
        )

        Emotion.Neutral -> EmotionPalette(
            coreColor = Color(0xFF8DA1B3),
            auraColor = Color(0x668DA1B3),
            glowColor = Color(0x1A8DA1B3),
            eyeColor = Color(0xFFEFF4F7)
        )
    }
}

fun blendPalettes(p1: EmotionPalette, p2: EmotionPalette, t: Float): EmotionPalette {
    val clampedT = t.coerceIn(0f, 1f)
    return EmotionPalette(
        coreColor = lerp(p1.coreColor, p2.coreColor, clampedT),
        auraColor = lerp(p1.auraColor, p2.auraColor, clampedT),
        glowColor = lerp(p1.glowColor, p2.glowColor, clampedT),
        eyeColor = lerp(p1.eyeColor, p2.eyeColor, clampedT)
    )
}
