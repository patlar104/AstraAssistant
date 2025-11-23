package dev.patrick.astra.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

enum class Emotion {
    Neutral,
    Curious,
    Happy,
    Concerned,
    Focused,
    Excited
}

sealed class AstraState {
    data object Idle : AstraState()
    data class Listening(val intensity: Float = 1f) : AstraState()
    data class Thinking(val mood: Emotion = Emotion.Focused) : AstraState()
    data class Speaking(val mood: Emotion = Emotion.Happy) : AstraState()
    data class Error(val reason: String? = null) : AstraState()
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
    val squashX: Float,      // 1.0 = neutral width, >1.0 = wider, <1.0 = narrower
    val squashY: Float,      // 1.0 = neutral height, >1.0 = taller, <1.0 = shorter
    val tiltDegrees: Float,  // orb rotation (positive = curious/engaged, negative = skeptical/concern)
    val wobbleFactor: Float, // softness when animating; higher = more organic wobble
    val eyeSeparationFactor: Float, // horizontal eye spacing multiplier
    val eyeTiltDegrees: Float,      // rotation of the eye-line (positive = lifted, negative = droopy)
    val mouthCurveAmount: Float     // -1f = frown tension, 0f = neutral, 1f = soft smile
)

fun AstraState.toEnergy(): StateEnergy =
    when (this) {
        is AstraState.Idle -> StateEnergy(energy = 0.25f, pulseSpeedScale = 1f, auraBoost = 1f)
        is AstraState.Listening -> StateEnergy(energy = 0.6f, pulseSpeedScale = 1.2f, auraBoost = 1.1f)
        is AstraState.Thinking -> StateEnergy(energy = 0.5f, pulseSpeedScale = 1.1f, auraBoost = 1.05f)
        is AstraState.Speaking -> StateEnergy(energy = 0.9f, pulseSpeedScale = 1.35f, auraBoost = 1.15f)
        is AstraState.Error -> StateEnergy(energy = 0.8f, pulseSpeedScale = 1.5f, auraBoost = 1.2f)
    }

fun expressionFor(state: AstraState, emotion: Emotion): ExpressionShape {
    return when (state) {
        is AstraState.Error -> {
            // Concern: more vertical, negative tilt, eyes closer, subtle frown cue
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
        is AstraState.Speaking -> {
            when (emotion) {
                Emotion.Excited -> {
                    // Speaking excited: wider, upbeat tilt, energetic wobble, lifted smile
                    ExpressionShape(
                        squashX = 1.07f,
                        squashY = 0.94f,
                        tiltDegrees = 6f,
                        wobbleFactor = 0.12f,
                        eyeSeparationFactor = 1.1f,
                        eyeTiltDegrees = 6f,
                        mouthCurveAmount = 0.42f
                    )
                }
                Emotion.Happy -> {
                    // Speaking happy: slightly wide, open posture, soft smile
                    ExpressionShape(
                        squashX = 1.05f,
                        squashY = 0.95f,
                        tiltDegrees = 4f,
                        wobbleFactor = 0.1f,
                        eyeSeparationFactor = 1.08f,
                        eyeTiltDegrees = 4f,
                        mouthCurveAmount = 0.35f
                    )
                }
                Emotion.Concerned -> {
                    // Speaking but uneasy: narrower, forward lean, tense mouth
                    ExpressionShape(
                        squashX = 0.95f,
                        squashY = 1.05f,
                        tiltDegrees = -2f,
                        wobbleFactor = 0.1f,
                        eyeSeparationFactor = 0.95f,
                        eyeTiltDegrees = -3f,
                        mouthCurveAmount = -0.15f
                    )
                }
                else -> {
                    // Speaking neutral/focused: balanced stretch, attentive tilt
                    ExpressionShape(
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
        }
        is AstraState.Thinking -> {
            if (emotion == Emotion.Concerned) {
                // Thinking concerned: stretched upward, skeptical tilt, slight frown
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
                // Thinking focused: taller, left tilt, eyes closer, mild tension
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
        is AstraState.Listening -> {
            when (emotion) {
                Emotion.Focused, Emotion.Curious -> {
                    // Listening focused: slightly taller, leaning in, eyes a bit apart
                    ExpressionShape(
                        squashX = 0.96f,
                        squashY = 1.04f,
                        tiltDegrees = 2f,
                        wobbleFactor = 0.06f,
                        eyeSeparationFactor = 1.05f,
                        eyeTiltDegrees = 3f,
                        mouthCurveAmount = 0.0f
                    )
                }
                Emotion.Happy -> {
                    // Listening happy: relaxed width, upbeat tilt, soft smile
                    ExpressionShape(
                        squashX = 1.02f,
                        squashY = 0.99f,
                        tiltDegrees = 3f,
                        wobbleFactor = 0.07f,
                        eyeSeparationFactor = 1.06f,
                        eyeTiltDegrees = 4f,
                        mouthCurveAmount = 0.22f
                    )
                }
                Emotion.Concerned -> {
                    // Listening concerned: narrower, slight negative tilt, closer eyes
                    ExpressionShape(
                        squashX = 0.95f,
                        squashY = 1.05f,
                        tiltDegrees = -2.5f,
                        wobbleFactor = 0.08f,
                        eyeSeparationFactor = 0.94f,
                        eyeTiltDegrees = -3f,
                        mouthCurveAmount = -0.18f
                    )
                }
                else -> {
                    // Listening neutral: modest stretch upward, attentive but calm
                    ExpressionShape(
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
        }
        is AstraState.Idle -> {
            when (emotion) {
                Emotion.Happy -> {
                    // Idle happy: softly wider, slight tilt up, relaxed smile
                    ExpressionShape(
                        squashX = 1.03f,
                        squashY = 0.99f,
                        tiltDegrees = 2f,
                        wobbleFactor = 0.06f,
                        eyeSeparationFactor = 1.04f,
                        eyeTiltDegrees = 2f,
                        mouthCurveAmount = 0.18f
                    )
                }
                Emotion.Excited -> {
                    // Idle excited: lively width, upbeat tilt, more wobble energy
                    ExpressionShape(
                        squashX = 1.06f,
                        squashY = 0.97f,
                        tiltDegrees = 4f,
                        wobbleFactor = 0.08f,
                        eyeSeparationFactor = 1.07f,
                        eyeTiltDegrees = 3.5f,
                        mouthCurveAmount = 0.24f
                    )
                }
                Emotion.Curious -> {
                    // Idle curious: slight lean forward, narrower eyes, neutral mouth
                    ExpressionShape(
                        squashX = 0.99f,
                        squashY = 1.01f,
                        tiltDegrees = 1.5f,
                        wobbleFactor = 0.06f,
                        eyeSeparationFactor = 0.99f,
                        eyeTiltDegrees = 1f,
                        mouthCurveAmount = 0.1f
                    )
                }
                Emotion.Concerned -> {
                    // Idle concerned: subtle vertical stretch, negative tilt, lower eyes
                    ExpressionShape(
                        squashX = 0.93f,
                        squashY = 1.07f,
                        tiltDegrees = -3.5f,
                        wobbleFactor = 0.08f,
                        eyeSeparationFactor = 0.93f,
                        eyeTiltDegrees = -4f,
                        mouthCurveAmount = -0.22f
                    )
                }
                else -> {
                    // Idle neutral: baseline orb posture with faint smile
                    ExpressionShape(
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
}

fun Emotion.toPalette(): EmotionPalette {
    return when (this) {
        Emotion.Happy -> EmotionPalette(
            coreColor = Color(0xFF3FD1A0),       // minty teal
            auraColor = Color(0x663FD1A0),
            glowColor = Color(0x1A3FD1A0),
            eyeColor = Color(0xFFF4FFF9)
        )
        Emotion.Excited -> EmotionPalette(
            coreColor = Color(0xFF9C6BFF),       // violet neon
            auraColor = Color(0x669C6BFF),
            glowColor = Color(0x1A9C6BFF),
            eyeColor = Color(0xFFF7F2FF)
        )
        Emotion.Curious -> EmotionPalette(
            coreColor = Color(0xFF38D4FF),       // cyan/teal
            auraColor = Color(0x6638D4FF),
            glowColor = Color(0x1A38D4FF),
            eyeColor = Color(0xFFE7FBFF)
        )
        Emotion.Focused -> EmotionPalette(
            coreColor = Color(0xFF1AA0FF),       // deep blue/teal
            auraColor = Color(0x661AA0FF),
            glowColor = Color(0x1A1AA0FF),
            eyeColor = Color(0xFFE3F2FF)
        )
        Emotion.Concerned -> EmotionPalette(
            coreColor = Color(0xFFFFA04D),       // warm amber
            auraColor = Color(0x66FFA04D),
            glowColor = Color(0x1AFFA04D),
            eyeColor = Color(0xFFFFF6E5)
        )
        Emotion.Neutral -> EmotionPalette(
            coreColor = Color(0xFF8DA1B3),       // cool gray-blue
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
