package dev.patrick.astra.brains.intent

/**
 * High-level intent categories predicted by the LLM.
 */
enum class IntentType {
    OPEN_APP,
    SEND_MESSAGE,
    ASK_QUESTION,
    TRANSLATE_TEXT,
    CONTROL_DEVICE,
    SEARCH_WEB,
    GET_WEATHER,
    SMALL_TALK,
    UNKNOWN
}
