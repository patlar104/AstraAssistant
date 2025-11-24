package dev.patrick.astra.brains.llm

import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Abstraction for any LLM-backed classifier.
 */
interface LlmClient {
    suspend fun classifyIntent(
        text: String,
        context: BrainContext
    ): ParsedIntent

    suspend fun generateReply(
        text: String,
        context: BrainContext
    ): String
}
