package dev.patrick.astra.assistant

class SnackbarEvent(
    val message: String,
    val actionLabel: String? = null,
    val onAction: (suspend () -> Unit)? = null
)
