package dev.patrick.astra.actions

import dev.patrick.astra.brains.intent.ActionPlan

interface ActionExecutor {
    suspend fun execute(plan: ActionPlan.ExecuteDeviceActions): ActionResult
}
