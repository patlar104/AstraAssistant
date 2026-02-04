package dev.patrick.astra.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DiagnosticLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

data class DiagnosticEntry(
    val timestamp: Long,
    val level: DiagnosticLevel,
    val tag: String,
    val message: String
)

/**
 * Simple in-memory diagnostics log with a bounded ring buffer.
 */
object DiagnosticsLog {
    private const val MAX_ENTRIES = 200

    private val buffer = ArrayDeque<DiagnosticEntry>()
    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    fun log(level: DiagnosticLevel, tag: String, message: String) {
        val entry = DiagnosticEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        buffer.addLast(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.removeFirst()
        }
        _entries.value = buffer.toList()
    }

    fun d(tag: String, message: String) = log(DiagnosticLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(DiagnosticLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(DiagnosticLevel.WARN, tag, message)
    fun e(tag: String, message: String) = log(DiagnosticLevel.ERROR, tag, message)

    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }
}
