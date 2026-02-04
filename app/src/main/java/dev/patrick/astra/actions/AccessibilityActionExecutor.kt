package dev.patrick.astra.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import dev.patrick.astra.brains.intent.ActionPlan
import dev.patrick.astra.brains.intent.DeviceActionStep
import dev.patrick.astra.brains.intent.SystemControlType
import dev.patrick.astra.diagnostics.DiagnosticsLog
import kotlin.math.roundToInt

class AccessibilityActionExecutor(
    private val context: Context
) : ActionExecutor {

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
            is DeviceActionStep.OpenApp -> openApp(step)
            is DeviceActionStep.SendMessage -> sendMessage(step)
            is DeviceActionStep.ShowText -> showText(step)
            is DeviceActionStep.SystemControl -> systemControl(step)
            is DeviceActionStep.NavigateToSettings -> openSettings(step)
        }
    }

    private fun openApp(step: DeviceActionStep.OpenApp): ActionResult {
        val packageManager = context.packageManager
        val packageName = step.packageName ?: findPackageByLabel(step.appNameHint)

        if (packageName == null) {
            return ActionResult(
                success = false,
                error = ActionError(
                    code = "app_not_found",
                    message = "Unable to find the app to open.",
                    recoverable = true
                )
            )
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(
                success = false,
                error = ActionError(
                    code = "launch_intent_missing",
                    message = "No launch intent for $packageName.",
                    recoverable = true
                )
            )

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionResult(success = true, message = "Opened app")
    }

    private fun findPackageByLabel(appNameHint: String?): String? {
        if (appNameHint.isNullOrBlank()) return null
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = pm.queryIntentActivities(intent, 0)
        val normalizedHint = appNameHint.trim().lowercase()
        return matches.firstOrNull { resolveInfo ->
            val label = resolveInfo.loadLabel(pm)?.toString()?.lowercase().orEmpty()
            label.contains(normalizedHint)
        }?.activityInfo?.packageName
    }

    private fun sendMessage(step: DeviceActionStep.SendMessage): ActionResult {
        val uri = Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", step.message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = context.packageManager
        if (intent.resolveActivity(pm) == null) {
            return ActionResult(
                success = false,
                error = ActionError(
                    code = "sms_unavailable",
                    message = "No SMS app available to send the message.",
                    recoverable = true
                )
            )
        }
        context.startActivity(intent)
        return ActionResult(success = true, message = "Opened messaging")
    }

    private fun showText(step: DeviceActionStep.ShowText): ActionResult {
        DiagnosticsLog.i(TAG, "ShowText: ${step.text}")
        return ActionResult(success = true, message = step.text)
    }

    private fun systemControl(step: DeviceActionStep.SystemControl): ActionResult {
        return when (step.controlType) {
            SystemControlType.ADJUST_VOLUME -> adjustVolume(step.value)
            SystemControlType.ADJUST_BRIGHTNESS -> unsupported("brightness_control")
            SystemControlType.TOGGLE_WIFI -> unsupported("wifi_toggle")
            SystemControlType.TOGGLE_BLUETOOTH -> unsupported("bluetooth_toggle")
            SystemControlType.TOGGLE_DND -> unsupported("dnd_toggle")
        }
    }

    private fun adjustVolume(value: String?): ActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream)
        val current = audioManager.getStreamVolume(stream)
        val target = parseVolumeTarget(value, current, max) ?: return ActionResult(
            success = false,
            error = ActionError(
                code = "volume_invalid",
                message = "Invalid volume value.",
                recoverable = true
            )
        )

        audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
        val undoPercent = if (max > 0) ((current.toFloat() / max) * 100).roundToInt() else null
        val undoPlan = undoPercent?.let {
            ActionPlan.ExecuteDeviceActions(
                steps = listOf(
                    DeviceActionStep.SystemControl(
                        controlType = SystemControlType.ADJUST_VOLUME,
                        value = it.toString()
                    )
                ),
                summary = "Undo volume"
            )
        }
        return ActionResult(
            success = true,
            message = "Volume adjusted",
            undoPlan = undoPlan
        )
    }

    private fun parseVolumeTarget(value: String?, current: Int, max: Int): Int? {
        if (max <= 0) return null
        val trimmed = value?.trim()?.lowercase()
        if (trimmed.isNullOrBlank()) return null

        return when (trimmed) {
            "up" -> (current + 1).coerceAtMost(max)
            "down" -> (current - 1).coerceAtLeast(0)
            else -> {
                val percent = trimmed.toIntOrNull() ?: return null
                val clamped = percent.coerceIn(0, 100)
                ((clamped / 100f) * max).roundToInt()
            }
        }
    }

    private fun openSettings(step: DeviceActionStep.NavigateToSettings): ActionResult {
        val intent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult(success = true, message = "Opened settings")
    }

    private fun unsupported(code: String): ActionResult {
        return ActionResult(
            success = false,
            error = ActionError(
                code = code,
                message = "This action is not supported yet.",
                recoverable = true
            )
        )
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}
