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

fun AstraState.toEnergy(): StateEnergy =
    when (this) {
        is AstraState.Idle -> StateEnergy(energy = 0.25f, pulseSpeedScale = 1f, auraBoost = 1f)
        is AstraState.Listening -> StateEnergy(energy = 0.6f, pulseSpeedScale = 1.2f, auraBoost = 1.1f)
        is AstraState.Thinking -> StateEnergy(energy = 0.5f, pulseSpeedScale = 1.1f, auraBoost = 1.05f)
        is AstraState.Speaking -> StateEnergy(energy = 0.9f, pulseSpeedScale = 1.35f, auraBoost = 1.15f)
        is AstraState.Error -> StateEnergy(energy = 0.8f, pulseSpeedScale = 1.5f, auraBoost = 1.2f)
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
