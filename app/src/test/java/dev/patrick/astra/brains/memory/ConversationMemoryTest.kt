package dev.patrick.astra.brains.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMemoryTest {

    @Test
    fun addAssistantReply_createsEntryWhenEmpty() {
        val memory = ConversationMemory()

        memory.addAssistantReply("Hello")

        val snapshot = memory.snapshot()
        assertEquals(1, snapshot.size)
        assertEquals("", snapshot.first().user)
        assertEquals("Hello", snapshot.first().assistant)
    }

    @Test
    fun trim_limitsMaxEntries() {
        val memory = ConversationMemory(maxEntries = 2)

        memory.addUserMessage("one")
        memory.addAssistantReply("first")
        memory.addUserMessage("two")
        memory.addAssistantReply("second")
        memory.addUserMessage("three")

        val snapshot = memory.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("two", snapshot[0].user)
        assertEquals("second", snapshot[0].assistant)
        assertEquals("three", snapshot[1].user)
    }

    @Test
    fun withUserMessage_returnsCopy() {
        val memory = ConversationMemory()
        val updated = memory.withUserMessage("new message")

        assertEquals(0, memory.snapshot().size)
        assertEquals(1, updated.snapshot().size)
        assertEquals("new message", updated.snapshot().first().user)
    }
}
