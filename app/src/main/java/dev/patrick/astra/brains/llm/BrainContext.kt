package dev.patrick.astra.brains.llm

import dev.patrick.astra.brains.memory.ConversationMemory

data class BrainContext(
    val memory: ConversationMemory = ConversationMemory(),
    val lastUserIntent: String? = null,
    val lastAssistantReply: String? = null
) {

    fun withUpdatedUserMessage(message: String): BrainContext {
        memory.addUserMessage(message)
        return this.copy(lastUserIntent = message)
    }

    fun withAssistantReply(reply: String): BrainContext {
        memory.addAssistantReply(reply)
        return this.copy(lastAssistantReply = reply)
    }
}
