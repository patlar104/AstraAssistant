package dev.patrick.astra.actions

import dev.patrick.astra.brains.intent.ActionPlan



data class ActionError(
    val code: String,
    val message: String,
    val recoverable: Boolean = false
)


data class ActionResult(
    val success: Boolean,
    val message: String? = null,
    val error: ActionError? = null,
    val undoPlan: ActionPlan.ExecuteDeviceActions? = null
)
