package dev.patrick.astra.brains

import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.llm.BrainContext
import dev.patrick.astra.brains.llm.LlmClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrainIntegrationTest {

    @Test
    fun handleUserUtterance_updatesConversationMemory() = runBlocking {
        val llm = RecordingLlmClient()
        val brain = Brain(
            llmClient = llm,
            skillRouter = SkillRouter(),
            dispatcher = Dispatchers.Unconfined
        )

        brain.handleUserUtterance("Hello")
        brain.handleUserUtterance("Next")

        assertEquals(2, llm.seenContexts.size)
        val firstContext = llm.seenContexts.first()
        val secondContext = llm.seenContexts.last()

        assertEquals("Hello", firstContext.lastUserIntent)
        assertEquals("Hello", secondContext.lastAssistantReply)

        val snapshot = secondContext.memory.snapshot()
        assertEquals(2, snapshot.size)
        assertEquals("Hello", snapshot[0].user)
        assertEquals("Hello", snapshot[0].assistant)
        assertEquals("Next", snapshot[1].user)
        assertEquals(null, snapshot[1].assistant)
    }

    private class RecordingLlmClient : LlmClient {
        val seenContexts = mutableListOf<BrainContext>()

        override suspend fun classifyIntent(text: String, context: BrainContext): ParsedIntent {
            seenContexts += context
            return ParsedIntent(
                type = IntentType.SMALL_TALK,
                arguments = emptyMap(),
                confidence = 0.9f,
                rawText = text
            )
        }

        override suspend fun generateReply(text: String, context: BrainContext): String {
            assertTrue("generateReply should not be called for small talk", false)
            return ""
        }
    }
}
