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

    fun snapshot(): List<MemoryEntry> = entries.toList()

    private fun trim() {
        while (entries.size > maxEntries) {
            entries.removeFirst()
        }
    }
}
