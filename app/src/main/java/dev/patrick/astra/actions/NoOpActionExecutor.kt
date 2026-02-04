package dev.patrick.astra.actions

import dev.patrick.astra.brains.intent.ActionPlan

class NoOpActionExecutor : ActionExecutor {
    override suspend fun execute(plan: ActionPlan.ExecuteDeviceActions): ActionResult {
        return ActionResult(
            success = false,
            error = ActionError(
                code = "no_executor",
                message = "No action executor is configured.",
                recoverable = false
            )
        )
    }
}
