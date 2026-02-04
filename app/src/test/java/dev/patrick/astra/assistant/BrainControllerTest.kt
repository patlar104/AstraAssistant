package dev.patrick.astra.assistant

import dev.patrick.astra.brains.Brain
import dev.patrick.astra.brains.BrainResult
import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent
import dev.patrick.astra.brains.llm.BrainContext
import dev.patrick.astra.brains.llm.LlmClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainControllerTest {

    @Test
    fun submitUserMessage_blankTextSkipsBrain() = runBlocking {
        val llm = CountingLlmClient()
        val brain = Brain(llmClient = llm, dispatcher = Dispatchers.Unconfined)
        val controller = BrainController(brain, this)
        var resultCalled = false

        controller.submitUserMessage("   ", onResult = { resultCalled = true })

        assertFalse(resultCalled)
        assertEquals(0, llm.classifyCalls)
    }

    @Test
    fun submitUserMessage_deliversResult() = runBlocking {
        val llm = CountingLlmClient()
        val brain = Brain(llmClient = llm, dispatcher = Dispatchers.Unconfined)
        val controller = BrainController(brain, this)
        val resultDeferred = CompletableDeferred<BrainResult>()

        controller.submitUserMessage("Hello", onResult = { resultDeferred.complete(it) })

        val result = resultDeferred.await()
        assertTrue(result is BrainResult.DirectReply)
        val reply = result as BrainResult.DirectReply
        assertEquals("Hello", reply.text)
        assertEquals(1, llm.classifyCalls)
    }

    @Test
    fun submitUserMessage_routesErrors() = runBlocking {
        val llm = CountingLlmClient(throwOnClassify = true)
        val brain = Brain(llmClient = llm, dispatcher = Dispatchers.Unconfined)
        val controller = BrainController(brain, this)
        val errorDeferred = CompletableDeferred<Throwable>()

        controller.submitUserMessage(
            "Hello",
            onResult = {},
            onError = { errorDeferred.complete(it) }
        )

        val error = errorDeferred.await()
        assertTrue(error is IllegalStateException)
    }

    private class CountingLlmClient(
        private val throwOnClassify: Boolean = false
    ) : LlmClient {
        var classifyCalls = 0

        override suspend fun classifyIntent(text: String, context: BrainContext): ParsedIntent {
            classifyCalls += 1
            if (throwOnClassify) {
                throw IllegalStateException("boom")
            }
            return ParsedIntent(
                type = IntentType.SMALL_TALK,
                arguments = emptyMap(),
                confidence = 0.9f,
                rawText = text
            )
        }

        override suspend fun generateReply(text: String, context: BrainContext): String {
            return "reply"
        }
    }
}
