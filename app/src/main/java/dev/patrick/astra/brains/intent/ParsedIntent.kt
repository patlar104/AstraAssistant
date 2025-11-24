package dev.patrick.astra.brains.intent

/**
 * Result of intent classification.
 */
data class ParsedIntent(
    val type: IntentType,
    val arguments: Map<String, String>,
    val confidence: Float,
    val rawText: String
)
