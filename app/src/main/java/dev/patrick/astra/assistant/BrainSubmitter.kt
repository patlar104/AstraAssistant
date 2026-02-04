package dev.patrick.astra.assistant

import dev.patrick.astra.brains.BrainResult

fun interface BrainSubmitter {
    fun submitUserMessage(
        text: String,
        onResult: (BrainResult) -> Unit,
        onError: (Throwable) -> Unit
    )
}
