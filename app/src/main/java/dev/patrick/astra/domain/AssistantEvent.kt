package dev.patrick.astra.domain

sealed class AssistantEvent {
    data object ListeningStarted : AssistantEvent()
    data object ListeningStopped : AssistantEvent()
    data object ThinkingStarted : AssistantEvent()
    data object ThinkingStopped : AssistantEvent()
    data object SpeakingStarted : AssistantEvent()
    data object SpeakingStopped : AssistantEvent()
    data class Error(val reason: String?) : AssistantEvent()
    data object ResetToIdle : AssistantEvent()
}
