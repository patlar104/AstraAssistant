package dev.patrick.astra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import dev.patrick.astra.ui.toPalette

// Worst-case visual scale of the orb considering pulse + stretch animations.
// These bounds are intentionally generous to ensure edge clamping is safe.
const val BUBBLE_MAX_VISUAL_SCALE_X: Float = 1.25f
const val BUBBLE_MAX_VISUAL_SCALE_Y: Float = 1.25f
private const val ORB_PADDING_DP: Float = 8f

/**
 * Premium draggable Astra orb bubble with:
 * - Stretchy "gel" effect while dragging (scale + squash).
 * - Idle pulse (subtle for OLED).
 * - State-driven aura/emotion.
 *
 * Drag is handled in Compose, but actual window movement is delegated via:
 * - onDrag(dx, dy)
 * - onDragEnd()
 *
 * OverlayService is responsible for translating dx/dy into WindowManager.LayoutParams updates.
 */
@Composable
fun OverlayBubble(
    modifier: Modifier = Modifier,
    state: AstraState = AstraState.Idle,
    emotion: Emotion = Emotion.Neutral,
    // Optional external gaze hint in logical -1f..+1f range
    gazeHintX: Float? = null,
    gazeHintY: Float? = null,
    onClick: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onLayoutChanged: ((widthPx: Int, heightPx: Int) -> Unit)? = null
) {
    // Is the user currently dragging?
    var isDragging by remember { mutableStateOf(false) }

    // Last drag vector magnitude (used to fake a velocity-ish feel)
    var lastDragMagnitude by remember { mutableStateOf(0f) }

    // Blinking state
    var isBlinking by remember { mutableStateOf(false) }

    // Gaze offsets in logical units, later converted to px in graphicsLayer
    var eyeOffsetX by remember { mutableStateOf(0f) }
    var eyeOffsetY by remember { mutableStateOf(0f) }

    // Emotional blink modulation: concerned/focused blink slightly more often
    val blinkBaseMin = if (emotion == Emotion.Concerned || emotion == Emotion.Focused) 2500L else 3200L
    val blinkBaseMax = if (emotion == Emotion.Concerned || emotion == Emotion.Focused) 5200L else 6800L

    val stateEnergy: Float = when (state) {
        is AstraState.Idle -> 0.25f
        is AstraState.Listening -> 0.6f
        is AstraState.Thinking -> 0.5f
        is AstraState.Speaking -> 0.9f
        is AstraState.Error -> 0.8f
    }

    val palette = emotion.toPalette()

    // Launch a coroutine that triggers blinks at pseudo-random intervals
    LaunchedEffect(emotion) {
        while (true) {
            val delayMillis = (blinkBaseMin..blinkBaseMax).random()
            delay(delayMillis)
            isBlinking = true
            delay(80L)
            isBlinking = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bubble_transition")

    val baseDuration = when (state) {
        is AstraState.Idle -> 2600
        is AstraState.Listening -> 1800
        is AstraState.Thinking -> 2200
        is AstraState.Speaking -> 1400
        is AstraState.Error -> 900
    }
    val pulseAmplitude = 0.02f + stateEnergy * 0.015f
    val idlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f - pulseAmplitude,
        targetValue = 1f + pulseAmplitude,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = baseDuration,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse_anim"
    )

    // Stretchy scale based on drag + state
    val targetStretchScale = when {
        isDragging -> 1.1f
        state is AstraState.Listening -> 1.06f
        state is AstraState.Speaking -> 1.04f
        else -> 1f
    }

    val stretchScale by animateFloatAsState(
        targetValue = targetStretchScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stretch_scale"
    )

    // We can subtly squash the orb opposite the drag direction using lastDragMagnitude,
    // but to keep this simple & safe, we just derive a secondary factor from it:
    val dragIntensity = (lastDragMagnitude / 40f).coerceIn(0f, 0.18f)
    val squashScaleY = 1f - (if (isDragging) dragIntensity else 0f)
    val squashScaleX = 1f + (if (isDragging) dragIntensity else 0f)

    val combinedScaleX = stretchScale * squashScaleX
    val combinedScaleY = stretchScale * squashScaleY

    val auraAlphaTarget = if (isDragging) {
        0.8f + stateEnergy * 0.2f
    } else {
        0.3f + stateEnergy * 0.4f
    }

    val auraAlpha by animateFloatAsState(
        targetValue = auraAlphaTarget,
        animationSpec = tween(400),
        label = "aura_alpha"
    )

    // Eye brightness based on emotion
    val eyeAlphaTarget = when (emotion) {
        Emotion.Happy, Emotion.Excited -> 1f
        Emotion.Curious, Emotion.Focused -> 0.9f
        Emotion.Concerned -> 0.75f
        Emotion.Neutral -> 0.85f
    }

    val eyeAlpha by animateFloatAsState(
        targetValue = eyeAlphaTarget,
        animationSpec = tween(250),
        label = "eye_alpha"
    )

    // Additional shape changes based on emotion
    val baseEyeScaleYTarget = when (emotion) {
        Emotion.Focused, Emotion.Concerned -> 0.7f
        Emotion.Excited -> 1.1f
        Emotion.Happy -> 1.0f
        Emotion.Curious -> 0.9f
        Emotion.Neutral -> 0.95f
    }

    val baseEyeScaleY by animateFloatAsState(
        targetValue = baseEyeScaleYTarget,
        animationSpec = tween(220),
        label = "eye_emotion_scaleY"
    )

    val blinkScaleYTarget = if (isBlinking) 0.1f else 1f
    val blinkScaleY by animateFloatAsState(
        targetValue = blinkScaleYTarget,
        animationSpec = tween(durationMillis = if (isBlinking) 70 else 120),
        label = "eye_blink_scaleY"
    )

    val combinedEyeScaleY = baseEyeScaleY * blinkScaleY
    val combinedEyeAlpha = if (isBlinking) eyeAlpha * 0.4f else eyeAlpha

    val errorShakeOffsetX by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "error_shake_x"
    )
    val effectiveErrorOffsetX = if (state is AstraState.Error) errorShakeOffsetX else 0f

    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_rotation"
    )

    Box(
        modifier = modifier
            // Add invisible padding so animated scale never clips against the view bounds.
            .padding(ORB_PADDING_DP.dp)
            .onGloballyPositioned { layoutCoordinates ->
                val size = layoutCoordinates.size
                onLayoutChanged?.invoke(size.width, size.height)
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val (dx, dy) = dragAmount
                        lastDragMagnitude = hypot(dx, dy)

                        // Update gaze based on drag direction
                        val normX = (dx / 40f).coerceIn(-1f, 1f)
                        val normY = (dy / 40f).coerceIn(-1f, 1f)
                        eyeOffsetX = normX
                        eyeOffsetY = normY

                        onDrag(dx, dy)
                    },
                    onDragEnd = {
                        isDragging = false
                        lastDragMagnitude = 0f
                        eyeOffsetX = 0f
                        eyeOffsetY = 0f
                        onDragEnd()
                    },
                    onDragCancel = {
                        isDragging = false
                        lastDragMagnitude = 0f
                        eyeOffsetX = 0f
                        eyeOffsetY = 0f
                        onDragEnd()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress?.invoke() }
                )
            }
            .scale(idlePulseScale)
            .scale(scaleX = combinedScaleX, scaleY = combinedScaleY),
        contentAlignment = Alignment.Center
    ) {
        val maxEyeOffsetDp = 3f
        val externalGazeX = gazeHintX ?: 0f
        val externalGazeY = gazeHintY ?: 0f
        val blendedGazeX = (eyeOffsetX * 0.7f) + (externalGazeX * 0.3f)
        val blendedGazeY = (eyeOffsetY * 0.7f) + (externalGazeY * 0.3f)
        val density = LocalDensity.current
        val gazeTranslateXPx = with(density) { (blendedGazeX * maxEyeOffsetDp).dp.toPx() }
        val gazeTranslateYPx = with(density) { (blendedGazeY * maxEyeOffsetDp).dp.toPx() }
        val auraSizeDp = when (state) {
            is AstraState.Listening -> 60.dp
            is AstraState.Speaking -> 64.dp
            is AstraState.Thinking -> 58.dp
            is AstraState.Error -> 60.dp
            is AstraState.Idle -> 56.dp
        }

        // Aura halo
        Box(
            modifier = Modifier
                .size(auraSizeDp)
                .alpha(auraAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.auraInner,
                            palette.auraOuter,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Orb body surface
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            with(density) { effectiveErrorOffsetX.dp.roundToPx() },
                            0
                        )
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.core,
                                palette.core.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (state is AstraState.Thinking) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                rotationZ = thinkingRotation
                            }
                            .border(
                                width = 1.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        palette.auraInner.copy(alpha = 0.1f),
                                        palette.auraInner.copy(alpha = 0.5f),
                                        palette.auraInner.copy(alpha = 0.1f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }

                // Inner "eyes" – two glowing nodes, abstract like a premium AI presence
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .graphicsLayer {
                            translationX = gazeTranslateXPx
                            translationY = gazeTranslateYPx
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Left eye node
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(10.dp)
                            .graphicsLayer {
                                scaleY = combinedEyeScaleY
                            }
                            .alpha(combinedEyeAlpha)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        palette.eye,
                                        palette.eye.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                    // Right eye node
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(10.dp)
                            .graphicsLayer {
                                scaleY = combinedEyeScaleY
                            }
                            .alpha(combinedEyeAlpha)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        palette.eye,
                                        palette.eye.copy(alpha = 0.3f),
                                        Color.Transparent
                                    )
                                ),
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
