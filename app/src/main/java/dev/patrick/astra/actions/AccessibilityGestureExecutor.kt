package dev.patrick.astra.actions

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.DeviceActionStep
import dev.patrick.astra.brains.intent.ScrollDirection
import dev.patrick.astra.diagnostics.DiagnosticsLog
import dev.patrick.astra.legacy.AccessibilityBridge
import java.util.ArrayDeque

class AccessibilityGestureExecutor(
    context: Context
) : ActionExecutor {

    private val bridge = AccessibilityBridge(context.applicationContext)

    override suspend fun execute(plan: ActionPlan.ExecuteDeviceActions): ActionResult {
        for (step in plan.steps) {
            val result = executeStep(step)
            if (!result.success) {
                return result
            }
        }
        return ActionResult(
            success = true,
            message = plan.summary ?: "Actions executed"
        )
    }

    private fun executeStep(step: DeviceActionStep): ActionResult {
        return when (step) {
            is DeviceActionStep.TapByText -> tapByText(step.text)
            is DeviceActionStep.TapById -> tapById(step.resId)
            is DeviceActionStep.Scroll -> scroll(step.direction)
            DeviceActionStep.NavigateBack -> performGlobalAction(AccessibilityBridge.GLOBAL_ACTION_BACK)
            DeviceActionStep.NavigateHome -> performGlobalAction(AccessibilityBridge.GLOBAL_ACTION_HOME)
            DeviceActionStep.NavigateRecents -> performGlobalAction(AccessibilityBridge.GLOBAL_ACTION_RECENTS)
            else -> ActionResult(
                success = false,
                error = ActionError(
                    code = "automation_unsupported",
                    message = "Automation step not supported by gesture executor.",
                    recoverable = true
                )
            )
        }
    }

    private fun tapByText(text: String): ActionResult {
        val root = bridge.getRootNode()
            ?: return error("automation_root_null", "No active window to search.")
        val target = findNodeByText(root, text)
            ?: return error("automation_node_not_found", "Can't find \"$text\" on screen.")
        return if (performClick(target)) {
            ActionResult(success = true, message = "Tapped \"$text\"")
        } else {
            error("automation_action_failed", "Couldn't tap \"$text\".")
        }
    }

    private fun tapById(resId: String): ActionResult {
        val root = bridge.getRootNode()
            ?: return error("automation_root_null", "No active window to search.")
        val target = try {
            root.findAccessibilityNodeInfosByViewId(resId).firstOrNull()
        } catch (e: Throwable) {
            DiagnosticsLog.w(TAG, "ViewId lookup failed: $resId ${e.message}")
            return error("automation_view_id_error", "Unable to resolve view id $resId.")
        }

        if (target == null) {
            return error("automation_node_not_found", "Can't find view id $resId.")
        }
        return if (performClick(target)) {
            ActionResult(success = true, message = "Tapped $resId")
        } else {
            error("automation_action_failed", "Couldn't tap view id $resId.")
        }
    }

    private fun scroll(direction: ScrollDirection): ActionResult {
        val root = bridge.getRootNode()
            ?: return error("automation_root_null", "No active window to scroll.")
        val scrollNode = findScrollableNode(root)
            ?: return error("automation_node_not_found", "No scrollable view found.")
        val action = when (direction) {
            ScrollDirection.FORWARD -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            ScrollDirection.BACKWARD -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return if (scrollNode.performAction(action)) {
            ActionResult(success = true, message = "Scrolled ${direction.name.lowercase()}")
        } else {
            error("automation_action_failed", "Scroll action failed.")
        }
    }

    private fun performGlobalAction(action: Int): ActionResult {
        val success = bridge.performGlobalAction(action)
        return if (success) {
            ActionResult(success = true, message = "Global action executed")
        } else {
            error("automation_action_failed", "Global action failed.")
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val nodeText = node.text?.toString()?.lowercase()
            val nodeDesc = node.contentDescription?.toString()?.lowercase()
            if (
                (!nodeText.isNullOrBlank() && nodeText.contains(normalized)) ||
                (!nodeDesc.isNullOrBlank() && nodeDesc.contains(normalized))
            ) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isScrollable) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    private fun error(code: String, message: String): ActionResult {
        DiagnosticsLog.w(TAG, "$code: $message")
        return ActionResult(
            success = false,
            error = ActionError(
                code = code,
                message = message,
                recoverable = true
            )
        )
    }

    companion object {
        private const val TAG = "GestureExecutor"
    }
}
