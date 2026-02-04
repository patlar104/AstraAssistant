package dev.patrick.astra.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.patrick.astra.overlay.OverlayService

sealed class OverlayLaunchAction {
    data class RequestPermission(val intent: Intent) : OverlayLaunchAction()
    data class StartService(val intent: Intent) : OverlayLaunchAction()
}

fun interface OverlayPermissionChecker {
    fun canDrawOverlays(context: Context): Boolean
}

object DefaultOverlayPermissionChecker : OverlayPermissionChecker {
    override fun canDrawOverlays(context: Context): Boolean =
        OverlayService.canDrawOverlays(context)
}

class OverlayLaunchCoordinator(
    private val permissionChecker: OverlayPermissionChecker = DefaultOverlayPermissionChecker
) {
    fun nextAction(context: Context): OverlayLaunchAction {
        return if (permissionChecker.canDrawOverlays(context)) {
            OverlayLaunchAction.StartService(Intent(context, OverlayService::class.java))
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            OverlayLaunchAction.RequestPermission(intent)
        }
    }
}
