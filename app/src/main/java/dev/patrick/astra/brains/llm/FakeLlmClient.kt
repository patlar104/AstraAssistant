package dev.patrick.astra.brains.llm

import dev.patrick.astra.brains.intent.IntentType
import dev.patrick.astra.brains.intent.ParsedIntent

/**
 * Simple rule-based intent classifier used for offline testing.
 */
class FakeLlmClient : LlmClient {
    override suspend fun classifyIntent(
        text: String,
        context: BrainContext
    ): ParsedIntent {
        val last = context.lastUserIntent.orEmpty()
        val combined = "$last\n$text".lowercase()

        return classifyFromRules(combined, text)
    }

    override suspend fun generateReply(
        text: String,
        context: BrainContext
    ): String {
        val entries = context.memory.snapshot()
        val lastThree = entries.takeLast(3)
            .joinToString("\n") { "User: ${it.user}\nAstra: ${it.assistant.orEmpty()}" }

        return "Based on convo:\n$lastThree\n\nReplying to: $text"
    }

    private fun classifyFromRules(
        normalizedInput: String,
        rawText: String
    ): ParsedIntent {
        val (type, isRuleHit) = when {
            normalizedInput.contains("open") || normalizedInput.contains("launch") -> IntentType.OPEN_APP to true
            normalizedInput.contains("translate") -> IntentType.TRANSLATE_TEXT to true
            normalizedInput.contains("message") || normalizedInput.contains("text") -> IntentType.SEND_MESSAGE to true
            normalizedInput.trim().endsWith("?") -> IntentType.ASK_QUESTION to true
            else -> IntentType.SMALL_TALK to false
        }

        val arguments = when (type) {
            IntentType.OPEN_APP -> mapOf("target" to rawText)
            IntentType.TRANSLATE_TEXT -> mapOf("text" to rawText)
            IntentType.SEND_MESSAGE -> mapOf("text" to rawText)
            IntentType.ASK_QUESTION -> mapOf("question" to rawText)
            IntentType.SMALL_TALK -> mapOf("text" to rawText)
            else -> emptyMap()
        }

        val confidence = if (isRuleHit) 0.75f else 0.4f

        return ParsedIntent(
            type = type,
            arguments = arguments,
            confidence = confidence,
            rawText = rawText
        )
    }
}
