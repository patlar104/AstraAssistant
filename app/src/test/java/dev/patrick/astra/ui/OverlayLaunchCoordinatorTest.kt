package dev.patrick.astra.ui

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.patrick.astra.overlay.OverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayLaunchCoordinatorTest {

    @Test
    fun nextAction_requestsPermissionWhenNeeded() {
        val coordinator = OverlayLaunchCoordinator(
            permissionChecker = OverlayPermissionChecker { false }
        )
        val context = ApplicationProvider.getApplicationContext<Context>()

        val action = coordinator.nextAction(context)

        assertTrue(action is OverlayLaunchAction.RequestPermission)
        val intent = (action as OverlayLaunchAction.RequestPermission).intent
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
    }

    @Test
    fun nextAction_startsServiceWhenPermissionGranted() {
        val coordinator = OverlayLaunchCoordinator(
            permissionChecker = OverlayPermissionChecker { true }
        )
        val context = ApplicationProvider.getApplicationContext<Context>()

        val action = coordinator.nextAction(context)

        assertTrue(action is OverlayLaunchAction.StartService)
        val intent = (action as OverlayLaunchAction.StartService).intent
        assertEquals(OverlayService::class.java.name, intent.component?.className)
    }
}
