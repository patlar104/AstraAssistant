package dev.patrick.astra.brains.llm

import dev.patrick.astra.brains.memory.ConversationMemory

data class BrainContext(
    val memory: ConversationMemory = ConversationMemory(),
    val lastUserIntent: String? = null,
    val lastAssistantReply: String? = null
) {

    fun withUpdatedUserMessage(message: String): BrainContext {
        val nextMemory = memory.withUserMessage(message)
        return this.copy(
            memory = nextMemory,
            lastUserIntent = message
        )
    }

    fun withAssistantReply(reply: String): BrainContext {
        val nextMemory = memory.withAssistantReply(reply)
        return this.copy(
            memory = nextMemory,
            lastAssistantReply = reply
        )
    }
}
