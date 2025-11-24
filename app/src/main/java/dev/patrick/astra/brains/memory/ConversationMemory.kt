package dev.patrick.astra.brains.memory

import androidx.compose.runtime.Immutable

@Immutable
data class MemoryEntry(
    val user: String,
    val assistant: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Lightweight conversation memory for short-term context.
 */
class ConversationMemory(
    private val maxEntries: Int = 20
) {
    private val entries = ArrayDeque<MemoryEntry>()

    fun copy(): ConversationMemory {
        val copy = ConversationMemory(maxEntries)
        copy.entries.addAll(entries)
        return copy
    }

    fun addUserMessage(text: String) {
        entries.addLast(MemoryEntry(user = text))
        trim()
    }

    fun addAssistantReply(text: String) {
        if (entries.isEmpty()) {
            entries.addLast(MemoryEntry(user = "", assistant = text))
        } else {
            val last = entries.removeLast()
            entries.addLast(last.copy(assistant = text))
        }
        trim()
    }

    fun withUserMessage(text: String): ConversationMemory {
        return copy().also { it.addUserMessage(text) }
    }

    fun withAssistantReply(text: String): ConversationMemory {
        return copy().also { it.addAssistantReply(text) }
    }

    fun snapshot(): List<MemoryEntry> = entries.toList()

    private fun trim() {
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
    }
}
