package dev.patrick.astra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * Compact floating Astra bubble used for the system overlay.
 *
 * Reuses AstraCharacter for the animated orb, but wraps it in a subtle
 * glow + shadow to feel like a proper assistant avatar.
 */
@Composable
fun OverlayBubble(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(8.dp),
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Inner animated Astra orb
        AstraCharacter()
    }
}
