package dev.patrick.astra.ui

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

data class EmotionPalette(
    val core: Color,
    val auraInner: Color,
    val auraOuter: Color,
    val eye: Color
)

fun Emotion.toPalette(): EmotionPalette {
    return when (this) {
        Emotion.Happy -> EmotionPalette(
            core = Color(0xFF4CAF50),
            auraInner = Color(0x804CAF50),
            auraOuter = Color(0x204CAF50),
            eye = Color.White
        )
        Emotion.Excited -> EmotionPalette(
            core = Color(0xFF7C4DFF),
            auraInner = Color(0x807C4DFF),
            auraOuter = Color(0x207C4DFF),
            eye = Color(0xFFFFF59D)
        )
        Emotion.Curious -> EmotionPalette(
            core = Color(0xFF03A9F4),
            auraInner = Color(0x8003A9F4),
            auraOuter = Color(0x2003A9F4),
            eye = Color.White
        )
        Emotion.Focused -> EmotionPalette(
            core = Color(0xFF009688),
            auraInner = Color(0x80009688),
            auraOuter = Color(0x20009688),
            eye = Color(0xFFE0F2F1)
        )
        Emotion.Concerned -> EmotionPalette(
            core = Color(0xFFFF7043),
            auraInner = Color(0x80FF7043),
            auraOuter = Color(0x20FF7043),
            eye = Color.White
        )
        Emotion.Neutral -> EmotionPalette(
            core = Color(0xFF9E9E9E),
            auraInner = Color(0x809E9E9E),
            auraOuter = Color(0x209E9E9E),
            eye = Color.White
        )
    }
}
