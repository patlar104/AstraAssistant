package dev.patrick.astra.assistant

/**
 * Represents a message in the Astra conversation.
 */
data class AstraMessage(
    val fromUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)
