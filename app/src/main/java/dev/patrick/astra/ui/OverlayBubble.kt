package dev.patrick.astra.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

// Worst-case visual scale of the orb considering pulse + stretch animations.
// These bounds are intentionally generous to ensure edge clamping is safe.
const val BUBBLE_MAX_VISUAL_SCALE_X: Float = 1.25f
const val BUBBLE_MAX_VISUAL_SCALE_Y: Float = 1.25f
private const val ORB_PADDING_DP: Float = 8f

/**
 * Optimized Canvas-driven overlay orb:
 * - Single drawing surface to reduce overdraw.
 * - Centralized animations to limit recompositions.
 * - Preserves drag/gaze/blink and state-driven visuals.
 *
 * OverlayService remains responsible for translating dx/dy into WindowManager.LayoutParams updates.
 */
@Composable
fun OverlayBubble(
    modifier: Modifier = Modifier,
    state: AstraState = AstraState.Idle,
    emotion: Emotion = Emotion.Neutral,
    gazeHintX: Float? = null,
    gazeHintY: Float? = null,
    onClick: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onLayoutChanged: ((widthPx: Int, heightPx: Int) -> Unit)? = null
) {
    val palette = emotion.toPalette()
    val energy = state.toEnergy()

    var isDragging by remember { mutableStateOf(false) }
    var dragMagnitude by remember { mutableStateOf(0f) }
    var eyeOffsetX by remember { mutableStateOf(0f) }
    var eyeOffsetY by remember { mutableStateOf(0f) }

    val clickCb by rememberUpdatedState(onClick)
    val dragCb by rememberUpdatedState(onDrag)
    val dragEndCb by rememberUpdatedState(onDragEnd)
    val longPressCb by rememberUpdatedState(onLongPress)

    val infinite = rememberInfiniteTransition(label = "orb_global")

    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (2200 / energy.pulseSpeedScale).roundToInt(),
                easing = LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val blinkPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "blink_phase"
    )

    val thinkingPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "thinking_phase"
    )

    val targetStretchScale = when {
        isDragging -> 1.04f + (dragMagnitude / 70f).coerceIn(0f, 0.06f)
        state is AstraState.Listening -> 1.035f
        state is AstraState.Speaking -> 1.045f
        else -> 1f
    }.coerceAtMost(1.1f)

    val stretchScale by animateFloatAsState(
        targetValue = targetStretchScale,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 300f
        ),
        label = "stretch_scale"
    )

    val blinkActive = when (state) {
        is AstraState.Idle, is AstraState.Listening -> blinkPhase > 0.92f
        else -> blinkPhase > 0.96f
    }

    val blinkScaleYTarget = if (blinkActive) 0.1f else 1f
    val blinkScaleY by animateFloatAsState(
        targetValue = blinkScaleYTarget,
        animationSpec = tween(durationMillis = if (blinkActive) 80 else 140),
        label = "blink_scale"
    )

    val auraAlphaTarget = if (isDragging) {
        0.55f + energy.energy * 0.3f
    } else {
        0.22f + energy.energy * 0.4f
    }

    val auraAlpha by animateFloatAsState(
        targetValue = auraAlphaTarget * energy.auraBoost,
        animationSpec = tween(300),
        label = "aura_alpha"
    )

    val eyeBaseAlpha = when (emotion) {
        Emotion.Excited, Emotion.Happy -> 1f
        Emotion.Curious, Emotion.Focused -> 0.94f
        Emotion.Neutral -> 0.88f
        Emotion.Concerned -> 0.8f
    }
    val eyeAlpha by animateFloatAsState(
        targetValue = if (blinkActive) eyeBaseAlpha * 0.35f else eyeBaseAlpha,
        animationSpec = tween(180),
        label = "eye_alpha"
    )

    val baseEyeScaleYTarget = when (emotion) {
        Emotion.Focused, Emotion.Concerned -> 0.68f
        Emotion.Excited -> 1.05f
        Emotion.Happy -> 1.0f
        Emotion.Curious -> 0.9f
        Emotion.Neutral -> 0.94f
    }

    val eyeScaleY by animateFloatAsState(
        targetValue = baseEyeScaleYTarget * blinkScaleY,
        animationSpec = tween(200),
        label = "eye_scale_y"
    )

    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                },
                onDragEnd = {
                    isDragging = false
                    dragMagnitude = 0f
                    eyeOffsetX = 0f
                    eyeOffsetY = 0f
                    dragEndCb()
                },
                onDragCancel = {
                    isDragging = false
                    dragMagnitude = 0f
                    eyeOffsetX = 0f
                    eyeOffsetY = 0f
                    dragEndCb()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val (dx, dy) = dragAmount
                    dragMagnitude = hypot(dx, dy)
                    val normX = (dx / 40f).coerceIn(-1f, 1f)
                    val normY = (dy / 40f).coerceIn(-1f, 1f)
                    eyeOffsetX = normX
                    eyeOffsetY = normY
                    dragCb(dx, dy)
                }
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { clickCb() },
                onLongPress = { longPressCb?.invoke() }
            )
        }

    val maxEyeOffset = 0.14f
    val externalGazeX = gazeHintX ?: 0f
    val externalGazeY = gazeHintY ?: 0f
    val blendedGazeX = (eyeOffsetX * 0.7f) + (externalGazeX * 0.3f)
    val blendedGazeY = (eyeOffsetY * 0.7f) + (externalGazeY * 0.3f)
    val eyeGazeX = (blendedGazeX.coerceIn(-1f, 1f)) * maxEyeOffset
    val eyeGazeY = (blendedGazeY.coerceIn(-1f, 1f)) * maxEyeOffset

    val pulseScale = run {
        val wave = (0.5f - abs(pulsePhase - 0.5f)) * 2f // triangle 0..1
        val baseAmplitude = 0.03f
        val pulseAmplitude = baseAmplitude * (0.3f + energy.energy * 0.7f)
        1f + (pulseAmplitude * (wave - 0.5f) * 2f)
    }

    val squashFactor = if (isDragging) (dragMagnitude / 70f).coerceIn(0f, 0.12f) else 0f
    val combinedScaleX = pulseScale * stretchScale * (1f + squashFactor)
    val combinedScaleY = pulseScale * stretchScale * (1f - squashFactor * 0.8f)

    val errorShake = if (state is AstraState.Error) {
        (pulsePhase - 0.5f) * 5f
    } else 0f

    Box(
        modifier = modifier
            .defaultMinSize(72.dp, 72.dp)
            .padding(ORB_PADDING_DP.dp)
            .onGloballyPositioned { coords ->
                onLayoutChanged?.invoke(coords.size.width, coords.size.height)
            }
            .then(gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOrb(
                palette = palette,
                energy = energy,
                auraAlpha = auraAlpha,
                combinedScaleX = combinedScaleX,
                combinedScaleY = combinedScaleY,
                eyeAlpha = eyeAlpha,
                eyeScaleY = eyeScaleY,
                eyeGazeX = eyeGazeX,
                eyeGazeY = eyeGazeY,
                thinkingPhase = thinkingPhase,
                state = state,
                errorShake = errorShake,
                pulseScale = pulseScale
            )
        }
    }
}

private fun DrawScope.drawOrb(
    palette: EmotionPalette,
    energy: StateEnergy,
    auraAlpha: Float,
    combinedScaleX: Float,
    combinedScaleY: Float,
    eyeAlpha: Float,
    eyeScaleY: Float,
    eyeGazeX: Float,
    eyeGazeY: Float,
    thinkingPhase: Float,
    state: AstraState,
    errorShake: Float,
    pulseScale: Float
) {
    val center = Offset(size.width / 2f + errorShake, size.height / 2f)
    val minDim = min(size.width, size.height)
    val scaleFactor = min(combinedScaleX, combinedScaleY)
    val coreRadius = minDim * 0.18f * scaleFactor * pulseScale
    val auraRadius = minDim * 0.32f * scaleFactor * pulseScale
    val specRadius = coreRadius * 0.65f

    // Aura
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.auraInner.copy(alpha = auraAlpha * 0.9f),
                palette.auraOuter.copy(alpha = auraAlpha * 0.6f),
                Color.Transparent
            )
        ),
        radius = auraRadius,
        center = center
    )

    // Thinking ring
    if (state is AstraState.Thinking) {
        rotate(thinkingPhase, center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(
                        palette.auraInner.copy(alpha = 0.08f),
                        palette.auraInner.copy(alpha = 0.45f),
                        palette.auraInner.copy(alpha = 0.08f)
                    )
                ),
                radius = coreRadius * 1.05f,
                center = center,
                style = Stroke(width = coreRadius * 0.06f)
            )
        }
    }

    // Core orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.core,
                palette.core.copy(alpha = 0.8f),
                Color.Black.copy(alpha = 0.2f)
            )
        ),
        radius = coreRadius,
        center = center
    )

    // Specular highlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                Color.Transparent
            )
        ),
        radius = specRadius,
        center = center + Offset(-coreRadius * 0.4f, -coreRadius * 0.4f)
    )

    // Error overlay
    if (state is AstraState.Error) {
        drawCircle(
            color = Color(0x66FF7043),
            radius = coreRadius,
            center = center
        )
    }

    // Eyes
    val eyeRadius = coreRadius * 0.18f
    val eyeOffsetX = coreRadius * 0.35f
    val eyeOffsetY = -coreRadius * 0.2f
    val pupilOffset = Offset(eyeGazeX * eyeRadius * 0.3f, eyeGazeY * eyeRadius * 0.3f)

    val leftEyeCenter = center + Offset(-eyeOffsetX, eyeOffsetY)
    val rightEyeCenter = center + Offset(eyeOffsetX, eyeOffsetY)

    drawEye(
        center = leftEyeCenter,
        radius = eyeRadius,
        heightScale = eyeScaleY,
        alpha = eyeAlpha,
        palette = palette,
        pupilOffset = pupilOffset
    )
    drawEye(
        center = rightEyeCenter,
        radius = eyeRadius,
        heightScale = eyeScaleY,
        alpha = eyeAlpha,
        palette = palette,
        pupilOffset = pupilOffset
    )
}

private fun DrawScope.drawEye(
    center: Offset,
    radius: Float,
    heightScale: Float,
    alpha: Float,
    palette: EmotionPalette,
    pupilOffset: Offset
) {
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.eye.copy(alpha = alpha),
                palette.eye.copy(alpha = alpha * 0.3f),
                Color.Transparent
            )
        ),
        topLeft = Offset(center.x - radius, center.y - radius * heightScale),
        size = Size(radius * 2, radius * 2 * heightScale)
    )

    // Pupil
    val pupilRadius = radius * 0.35f
    val pupilCenter = center + pupilOffset
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                palette.eye.copy(alpha = alpha),
                palette.eye.copy(alpha = alpha * 0.4f),
                Color.Transparent
            )
        ),
        radius = pupilRadius,
        center = pupilCenter
    )
}
