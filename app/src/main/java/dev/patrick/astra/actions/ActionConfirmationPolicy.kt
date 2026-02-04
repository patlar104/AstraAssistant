package dev.patrick.astra.actions

import android.content.Context
import dev.patrick.astra.brains.intent.DeviceActionStep

class ActionConfirmationPolicy(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun requiresConfirmation(steps: List<DeviceActionStep>): Boolean {
        return steps.any { step ->
            val key = keyFor(step) ?: return@any false
            !prefs.getBoolean(key, false)
        }
    }

    fun setSkipConfirmation(steps: List<DeviceActionStep>, skip: Boolean) {
        val editor = prefs.edit()
        steps.mapNotNull { keyFor(it) }
            .distinct()
            .forEach { key -> editor.putBoolean(key, skip) }
        editor.apply()
    }

    private fun keyFor(step: DeviceActionStep): String? {
        return when (step) {
            is DeviceActionStep.SystemControl -> KEY_SKIP_SYSTEM_CONTROL
            is DeviceActionStep.SendMessage -> KEY_SKIP_SEND_MESSAGE
            else -> null
        }
    }

    companion object {
        private const val PREFS_NAME = "action_confirmation"
        private const val KEY_SKIP_SYSTEM_CONTROL = "skip_confirm_system_control"
        private const val KEY_SKIP_SEND_MESSAGE = "skip_confirm_send_message"
    }
}
