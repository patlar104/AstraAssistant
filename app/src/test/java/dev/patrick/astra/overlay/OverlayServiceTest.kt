package dev.patrick.astra.overlay

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayServiceTest {

    @Before
    fun setUp() {
        ShadowSettings.setCanDrawOverlays(false)
    }

    @After
    fun tearDown() {
        ShadowSettings.setCanDrawOverlays(true)
    }

    @Test
    fun onCreate_stopsSelfWhenOverlayPermissionMissing() {
        val controller = Robolectric.buildService(OverlayService::class.java).create()
        val service = controller.get()

        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
