package dev.patrick.astra.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    @Test
    fun isInDismissZone_matchesExpectedBounds() {
        val zone = computeDismissZone(
            screenWidth = 1000,
            screenHeight = 2000,
            bubbleMarginPx = 20
        )

        assertTrue(isInDismissZone(centerX = 500, centerY = 1700, zone = zone))
        assertTrue(isInDismissZone(centerX = 350, centerY = 1900, zone = zone))
        assertFalse(isInDismissZone(centerX = 100, centerY = 1700, zone = zone))
        assertFalse(isInDismissZone(centerX = 500, centerY = 1200, zone = zone))
    }
}
