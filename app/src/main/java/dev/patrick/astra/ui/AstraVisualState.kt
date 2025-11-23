package dev.patrick.astra.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

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
    val core: Color,
    val auraInner: Color,
    val auraOuter: Color,
    val eye: Color
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
            core = Color(0xFF3FD1A0),       // minty teal
            auraInner = Color(0x663FD1A0),
            auraOuter = Color(0x1A3FD1A0),
            eye = Color(0xFFF4FFF9)
        )
        Emotion.Excited -> EmotionPalette(
            core = Color(0xFF9C6BFF),       // violet neon
            auraInner = Color(0x669C6BFF),
            auraOuter = Color(0x1A9C6BFF),
            eye = Color(0xFFF7F2FF)
        )
        Emotion.Curious -> EmotionPalette(
            core = Color(0xFF38D4FF),       // cyan/teal
            auraInner = Color(0x6638D4FF),
            auraOuter = Color(0x1A38D4FF),
            eye = Color(0xFFE7FBFF)
        )
        Emotion.Focused -> EmotionPalette(
            core = Color(0xFF1AA0FF),       // deep blue/teal
            auraInner = Color(0x661AA0FF),
            auraOuter = Color(0x1A1AA0FF),
            eye = Color(0xFFE3F2FF)
        )
        Emotion.Concerned -> EmotionPalette(
            core = Color(0xFFFFA04D),       // warm amber
            auraInner = Color(0x66FFA04D),
            auraOuter = Color(0x1AFFA04D),
            eye = Color(0xFFFFF6E5)
        )
        Emotion.Neutral -> EmotionPalette(
            core = Color(0xFF8DA1B3),       // cool gray-blue
            auraInner = Color(0x668DA1B3),
            auraOuter = Color(0x1A8DA1B3),
            eye = Color(0xFFEFF4F7)
        )
    }
}
