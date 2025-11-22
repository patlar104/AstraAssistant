package dev.patrick.astra.ui

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
