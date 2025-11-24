package dev.patrick.astra.legacy

/**
 * Represents a message in the Astra conversation.
 */
data class AstraMessage(
    val fromUser: Boolean,
    val text: String
)

/**
 * Simple brain stub. Later this will route to local models or cloud LLMs.
 */
class AstraBrain(
    private val speakCallback: SpeakCallback? = null
) {

    /**
     * For now this just echoes the user's text with a placeholder reply.
     * Later: plug in local LLM, Azure OpenAI, Gemini, etc.
     */
    suspend fun handleUserInput(input: String): AstraMessage {
        val reply = "I heard: \"$input\" (Astra’s brain isn’t wired yet.)"
        speakCallback?.speak(reply)
        return AstraMessage(fromUser = false, text = reply)
    }
}

fun interface SpeakCallback {
    fun speak(text: String)
}
