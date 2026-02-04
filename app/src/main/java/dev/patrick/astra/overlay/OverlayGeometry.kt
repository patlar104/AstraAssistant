package dev.patrick.astra.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

internal data class DismissZone(
    val top: Int,
    val bottom: Int,
    val centerX: Int,
    val halfWidth: Int
)

internal fun computeDismissZone(
    screenWidth: Int,
    screenHeight: Int,
    bubbleMarginPx: Int
): DismissZone {
    return DismissZone(
        top = (screenHeight * 0.75f).roundToInt(),
        bottom = screenHeight - bubbleMarginPx,
        centerX = screenWidth / 2,
        halfWidth = (screenWidth * 0.35f).roundToInt()
    )
}

internal fun isInDismissZone(centerX: Int, centerY: Int, zone: DismissZone): Boolean {
    val inHorizontalBand = abs(centerX - zone.centerX) <= zone.halfWidth
    val inVerticalBand = centerY in zone.top..zone.bottom
    return inHorizontalBand && inVerticalBand
}
