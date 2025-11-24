package dev.patrick.astra.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simple debug toggles for runtime logging.
 */
object DebugFlags {
    private val _overlayLogsEnabled = MutableStateFlow(false)
    val overlayLogsEnabled: StateFlow<Boolean> = _overlayLogsEnabled.asStateFlow()

    fun setOverlayLogsEnabled(enabled: Boolean) {
        _overlayLogsEnabled.value = enabled
    }
}
