package dev.patrick.astra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.collections.buildList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// NOTE: Old container scale (1.4x) made the window ~134dp while the orb visuals stay near 96dp,
// which inflated the touch window and mismatched OverlayService bounds. Keep only a small pad.
const val ORB_BASE_DP: Float = 96f
const val ORB_VISUAL_CONTAINER_SCALE: Float = 1.1f // tiny canvas pad to avoid aura/glow clipping at edges

// Summary:
// - Hitbox == 96dp core (Box.size(baseSize)); canvas has only 10% padding for glow.
// - Drag uses detectDragGestures; clicks go through combinedClickable gated by isDragging.
// - Long press toggles didLongPress so taps never fire afterwards.

private const val CORE_RADIUS_RATIO = 0.38f // core spans ~76% of the 96dp diameter (meets 30-40% radius spec)
private const val AURA_RADIUS_RATIO = 0.52f // aura expands slightly beyond the hitbox but stays inside the 1.1x canvas
private const val OUTER_GLOW_MULTIPLIER = 1.18f // keeps glow visible without clipping

/**
 * Optimized Canvas-driven overlay orb:
 * - Single drawing surface to reduce overdraw.
 * - Centralized animations to limit recompositions.
 * - Preserves drag/gaze/blink and state-driven visuals.
 *
 * OverlayService remains responsible for translating dx/dy into WindowManager.LayoutParams updates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverlayBubble(
    state: AstraState,
    emotion: Emotion,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    onPressChange: (Boolean) -> Unit = {},
    onLayoutChanged: ((widthPx: Int, heightPx: Int) -> Unit)? = null,
    isInDismissZone: Boolean = false,
    onDismissTriggered: () -> Unit = {},
    onRequestVoice: () -> Unit,
    onRequestTranslate: () -> Unit,
    onRequestSettings: () -> Unit,
    onRequestHide: (() -> Unit)? = null
) {
    val activeEmotion = when (state) {
        is AstraState.Thinking -> state.mood
        is AstraState.Speaking -> state.mood
        else -> emotion
    }
    val paletteTarget = activeEmotion.toPalette()
    val energy = state.toEnergy()
    val expressionTarget = remember(state, activeEmotion) { expressionFor(state, activeEmotion) }

    var isDragging by remember { mutableStateOf(false) }
    var dragMagnitude by remember { mutableStateOf(0f) }
    var totalDrag by remember { mutableStateOf(Offset.Zero) }
    var didLongPress by remember { mutableStateOf(false) }
    var lastTapPosition by remember { mutableStateOf<Offset?>(null) }
    var gazeTarget by remember { mutableStateOf(Offset.Zero) }
    var hudVisible by remember { mutableStateOf(false) }

    val tapCb by rememberUpdatedState(onTap)
    val dragCb by rememberUpdatedState(onDrag)
    val dragEndCb by rememberUpdatedState(onDragEnd)
    val pressChangeCb by rememberUpdatedState(onPressChange)
    val longPressCb by rememberUpdatedState(onLongPress)
    val requestVoiceCb by rememberUpdatedState(onRequestVoice)
    val requestTranslateCb by rememberUpdatedState(onRequestTranslate)
    val requestSettingsCb by rememberUpdatedState(onRequestSettings)
    val requestHideCb by rememberUpdatedState(onRequestHide)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedByClick by interactionSource.collectIsPressedAsState()

    var previousEmotionPalette by remember { mutableStateOf(paletteTarget) }
    var targetEmotionPalette by remember { mutableStateOf(paletteTarget) }
    val paletteBlend = remember { Animatable(1f) }
    val expressionAnimSpec = remember { tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing) }
    val squashXAnim = remember { Animatable(expressionTarget.squashX) }
    val squashYAnim = remember { Animatable(expressionTarget.squashY) }
    val tiltAnim = remember { Animatable(expressionTarget.tiltDegrees) }
    val wobbleAnim = remember { Animatable(expressionTarget.wobbleFactor) }
    val eyeSeparationAnim = remember { Animatable(expressionTarget.eyeSeparationFactor) }
    val eyeTiltAnim = remember { Animatable(expressionTarget.eyeTiltDegrees) }
    val mouthCurveAnim = remember { Animatable(expressionTarget.mouthCurveAmount) }

    LaunchedEffect(paletteTarget) {
        val currentPalette = blendPalettes(previousEmotionPalette, targetEmotionPalette, paletteBlend.value)
        previousEmotionPalette = currentPalette
        targetEmotionPalette = paletteTarget
        paletteBlend.snapTo(0f)
        paletteBlend.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    }

    val currentPalette = blendPalettes(previousEmotionPalette, targetEmotionPalette, paletteBlend.value)
    LaunchedEffect(expressionTarget) {
        launch { squashXAnim.animateTo(expressionTarget.squashX, expressionAnimSpec) }
        launch { squashYAnim.animateTo(expressionTarget.squashY, expressionAnimSpec) }
        launch { tiltAnim.animateTo(expressionTarget.tiltDegrees, expressionAnimSpec) }
        launch { wobbleAnim.animateTo(expressionTarget.wobbleFactor, expressionAnimSpec) }
        launch { eyeSeparationAnim.animateTo(expressionTarget.eyeSeparationFactor, expressionAnimSpec) }
        launch { eyeTiltAnim.animateTo(expressionTarget.eyeTiltDegrees, expressionAnimSpec) }
        launch { mouthCurveAnim.animateTo(expressionTarget.mouthCurveAmount, expressionAnimSpec) }
    }

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

    val thinkingPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "thinking_phase"
    )

    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "breath_phase"
    )

    val speakingPulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 625, easing = LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "speaking_phase"
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

    val blinkScale = remember { Animatable(1f) }

    LaunchedEffect(state, activeEmotion) {
        blinkScale.snapTo(1f)
        if (state is AstraState.Speaking) {
            return@LaunchedEffect
        }

        if (state is AstraState.Error || activeEmotion == Emotion.Concerned) {
            blinkScale.animateTo(0.1f, tween(durationMillis = 60))
            blinkScale.animateTo(1f, tween(durationMillis = 60))
        }

        val random = Random(System.currentTimeMillis())
        while (isActive) {
            val waitMillis = when {
                state is AstraState.Listening -> random.nextLong(5000L, 7000L)
                state is AstraState.Thinking -> random.nextLong(7000L, 10000L)
                else -> random.nextLong(3000L, 6000L)
            }
            delay(waitMillis)
            blinkScale.animateTo(0.1f, tween(durationMillis = 60))
            blinkScale.animateTo(1f, tween(durationMillis = 60))
        }
    }

    val isPressing = isDragging || isPressedByClick

    val auraAlphaTarget = if (isPressing) {
        0.38f + energy.energy * 0.32f
    } else {
        0.18f + energy.energy * 0.35f
    }

    val auraAlpha by animateFloatAsState(
        targetValue = auraAlphaTarget * energy.auraBoost,
        animationSpec = tween(300),
        label = "aura_alpha"
    )

    val blinkScaleY = blinkScale.value

    val eyeBaseAlpha = when (activeEmotion) {
        Emotion.Excited, Emotion.Happy -> 1f
        Emotion.Curious, Emotion.Focused -> 0.94f
        Emotion.Neutral -> 0.88f
        Emotion.Concerned -> 0.8f
    }
    val eyeAlpha by animateFloatAsState(
        targetValue = eyeBaseAlpha * blinkScaleY.coerceAtLeast(0.35f),
        animationSpec = tween(180),
        label = "eye_alpha"
    )

    val baseEyeScaleYTarget = when (activeEmotion) {
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
    val dismissHighlight by animateFloatAsState(
        targetValue = if (isInDismissZone) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "dismiss_highlight"
    )

    val expressionSquashX = squashXAnim.value
    val expressionSquashY = squashYAnim.value
    val expressionWobble = wobbleAnim.value
    val expressionTilt = tiltAnim.value
    val expressionEyeSeparation = eyeSeparationAnim.value
    val expressionEyeTilt = eyeTiltAnim.value
    val expressionMouthCurve = mouthCurveAnim.value
    val wobbleWave = sin((pulsePhase * 2f * PI).toDouble()).toFloat()
    val wobbleStretch = wobbleWave * expressionWobble * 0.35f
    val squashXWithWobble = expressionSquashX * (1f + wobbleStretch)
    val squashYWithWobble = expressionSquashY * (1f - wobbleStretch * 0.85f)
    val tiltFromWobble = wobbleWave * expressionWobble * 2.2f
    val tiltThinking = if (state is AstraState.Thinking) {
        sin(Math.toRadians(thinkingPhase.toDouble())).toFloat() * 1.5f
    } else 0f
    val finalTilt = expressionTilt + tiltFromWobble + tiltThinking

    // NOTE: Prior hitbox equaled the 96dp core while glow extended past it, so edges could clip and
    // the invisible window size differed from what OverlayService assumed. Use a larger visual
    // container but keep the pointer hitbox tight to the 96dp core.
    val baseSize = ORB_BASE_DP.dp
    val containerSize = baseSize * ORB_VISUAL_CONTAINER_SCALE
    val baseSizePx = with(LocalDensity.current) { baseSize.toPx() }
    val dragModifier = if (hudVisible) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                    hudVisible = false
                    didLongPress = false
                    dragMagnitude = 0f
                    totalDrag = Offset.Zero
                    gazeTarget = Offset.Zero
                },
                onDrag = { change, dragAmount ->
                    totalDrag += dragAmount
                    val (dx, dy) = dragAmount
                    dragMagnitude = hypot(totalDrag.x, totalDrag.y)
                    val magnitude = hypot(dx, dy)
                    if (magnitude > 0f) {
                        val normalized = Offset(dx / magnitude, dy / magnitude)
                        gazeTarget = normalized * 0.12f
                    }

                    change.consume()
                    dragCb(dx, dy)
                },
                onDragEnd = {
                    isDragging = false
                    hudVisible = false
                    didLongPress = false
                    dragMagnitude = 0f
                    totalDrag = Offset.Zero
                    gazeTarget = Offset.Zero
                    dragEndCb()
                },
                onDragCancel = {
                    isDragging = false
                    hudVisible = false
                    didLongPress = false
                    dragMagnitude = 0f
                    totalDrag = Offset.Zero
                    gazeTarget = Offset.Zero
                    dragEndCb()
                }
            )
        }
    }

    val gazePointerModifier = if (hudVisible) {
        Modifier
    } else {
        Modifier.pointerInput(baseSizePx) {
            awaitEachGesture {
                val down = awaitFirstDown()
                lastTapPosition = down.position
                val center = Offset(baseSizePx / 2f, baseSizePx / 2f)
                val delta = down.position - center
                val distance = delta.getDistance()
                gazeTarget = if (distance > 0f) {
                    Offset(delta.x / distance, delta.y / distance) * 0.12f
                } else {
                    Offset.Zero
                }
            }
        }
    }

    val clickModifier = if (hudVisible) {
        Modifier
    } else {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = !isDragging,
            onClick = {
                if (didLongPress) {
                    didLongPress = false
                    return@combinedClickable
                }
                tapCb()
            },
            onLongClick = {
                if (!isDragging) {
                    didLongPress = true
                    hudVisible = true
                    longPressCb()
                }
            }
        )
    }

    val smoothedGaze by animateOffsetAsState(
        targetValue = gazeTarget,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "gaze_offset"
    )

    LaunchedEffect(lastTapPosition) {
        val tap = lastTapPosition ?: return@LaunchedEffect
        val center = Offset(baseSizePx / 2f, baseSizePx / 2f)
        val delta = tap - center
        val distance = delta.getDistance()
        gazeTarget = if (distance > 0f) {
            Offset(delta.x / distance, delta.y / distance) * 0.12f
        } else {
            Offset.Zero
        }
    }

    LaunchedEffect(isPressing) {
        pressChangeCb(isPressing)
    }

    LaunchedEffect(isInDismissZone) {
        if (isInDismissZone) {
            hudVisible = false
        }
    }

    val maxEyeOffset = 0.12f
    val eyeGazeX = smoothedGaze.x.coerceIn(-maxEyeOffset, maxEyeOffset)
    val eyeGazeY = smoothedGaze.y.coerceIn(-maxEyeOffset, maxEyeOffset)

    val pulseScale = run {
        val wave = (0.5f - abs(pulsePhase - 0.5f)) * 2f // triangle 0..1
        val baseAmplitude = 0.03f
        val pulseAmplitude = baseAmplitude * (0.3f + energy.energy * 0.7f)
        1f + (pulseAmplitude * (wave - 0.5f) * 2f)
    }

    val squashFactor = if (isDragging) (dragMagnitude / 70f).coerceIn(0f, 0.12f) else 0f
    val pressedScaleTarget = if (isPressing) 1.08f else 1f
    val pressedScale by animateFloatAsState(
        targetValue = pressedScaleTarget,
        animationSpec = tween(durationMillis = 160),
        label = "pressed_scale"
    )
    val dismissScale = 1f - 0.07f * dismissHighlight
    val combinedScaleX = pulseScale * stretchScale * pressedScale * (1f + squashFactor) * dismissScale
    val combinedScaleY = pulseScale * stretchScale * pressedScale * (1f - squashFactor * 0.8f) * dismissScale

    val breathingScale = if (state is AstraState.Idle || state is AstraState.Listening) {
        1f + sin(breathPhase.toDouble()).toFloat() * 0.015f
    } else {
        1f
    }

    val speakingPulse = if (state is AstraState.Speaking) {
        ((sin(speakingPulsePhase.toDouble()) + 1f) * 0.5f).toFloat()
    } else {
        0f
    }

    val speakingAuraScale = 1f + 0.08f * speakingPulse
    val breathingGlowBoost = if (state is AstraState.Idle || state is AstraState.Listening) {
        1f + sin(breathPhase.toDouble()).toFloat() * 0.015f
    } else {
        1f
    }
    val speakingGlowBoost = 1f + 0.1f * speakingPulse

    val paletteForState = if (speakingPulse > 0f) {
        currentPalette.copy(
            coreColor = lerp(currentPalette.coreColor, Color.White, 0.1f * speakingPulse)
        )
    } else {
        currentPalette
    }

    val dismissColor = Color(0xFFE15B56)
    val paletteWithDismiss = paletteForState.copy(
        coreColor = lerp(paletteForState.coreColor, dismissColor.copy(alpha = 0.9f), 0.5f * dismissHighlight),
        auraColor = lerp(paletteForState.auraColor, dismissColor.copy(alpha = 0.6f), 0.4f * dismissHighlight),
        glowColor = lerp(paletteForState.glowColor, dismissColor.copy(alpha = 0.35f), 0.35f * dismissHighlight),
        eyeColor = lerp(paletteForState.eyeColor, Color.White, 0.08f * dismissHighlight)
    )

    val auraAlphaScaled = auraAlpha * breathingGlowBoost * speakingGlowBoost * (1f - 0.12f * dismissHighlight)
    val auraScale = breathingScale * speakingAuraScale * (1f - 0.05f * dismissHighlight).coerceAtLeast(0.85f)

    val errorShake = if (state is AstraState.Error) {
        (pulsePhase - 0.5f) * 5f
    } else 0f

    Box(
        modifier = modifier
            .size(containerSize)
            .onGloballyPositioned {
                onLayoutChanged?.invoke(it.size.width, it.size.height)
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(containerSize)
        ) {
            drawOrb(
                palette = paletteWithDismiss,
                energy = energy,
                auraAlpha = auraAlphaScaled,
                combinedScaleX = combinedScaleX,
                combinedScaleY = combinedScaleY,
                eyeAlpha = eyeAlpha,
                eyeScaleY = eyeScaleY,
                eyeGazeX = eyeGazeX,
                eyeGazeY = eyeGazeY,
                thinkingPhase = thinkingPhase,
                state = state,
                errorShake = errorShake,
                baseSizePx = baseSizePx,
                breathingScale = breathingScale,
                auraScale = auraScale,
                squashX = squashXWithWobble,
                squashY = squashYWithWobble,
                tiltDegrees = finalTilt,
                eyeSeparationFactor = expressionEyeSeparation,
                eyeTiltDegrees = expressionEyeTilt,
                mouthCurve = expressionMouthCurve,
                dismissHighlight = dismissHighlight,
                dismissOverlayColor = dismissColor
            )
        }

        val quickActions = remember(onRequestVoice, onRequestTranslate, onRequestSettings, onRequestHide) {
            buildList {
                add(
                    OrbHudAction(
                        label = "Ask",
                        icon = "A",
                        onClick = { requestVoiceCb() }
                    )
                )
                add(
                    OrbHudAction(
                        label = "Translate",
                        icon = "T",
                        onClick = { requestTranslateCb() }
                    )
                )
                add(
                    OrbHudAction(
                        label = "Settings",
                        icon = "S",
                        onClick = { requestSettingsCb() }
                    )
                )
                requestHideCb?.let {
                    add(
                        OrbHudAction(
                            label = "Hide",
                            icon = "X",
                            onClick = it
                        )
                    )
                }
            }
        }

        OrbQuickActionsHud(
            visible = hudVisible,
            actions = quickActions,
            onDismiss = {
                hudVisible = false
                didLongPress = false
            }
        )

        Box(
            modifier = Modifier
                .size(baseSize) // hitbox matches the ~96dp orb instead of the larger glow container
                .then(gazePointerModifier)
                .then(dragModifier)
                .then(clickModifier)
        )
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
    baseSizePx: Float,
    breathingScale: Float,
    auraScale: Float,
    squashX: Float,
    squashY: Float,
    tiltDegrees: Float,
    eyeSeparationFactor: Float,
    eyeTiltDegrees: Float,
    mouthCurve: Float,
    dismissHighlight: Float,
    dismissOverlayColor: Color
) {
    val center = Offset(size.width / 2f + errorShake, size.height / 2f)
    val orbDiameterPx = baseSizePx.coerceAtMost(min(size.width, size.height))
    val baseCoreRadius = orbDiameterPx * CORE_RADIUS_RATIO
    val baseAuraRadius = orbDiameterPx * AURA_RADIUS_RATIO
    val globalScaleX = combinedScaleX * breathingScale
    val globalScaleY = combinedScaleY * breathingScale
    val coreRadiusX = baseCoreRadius * squashX * globalScaleX
    val coreRadiusY = baseCoreRadius * squashY * globalScaleY
    val auraRadiusX = baseAuraRadius * squashX * combinedScaleX * auraScale
    val auraRadiusY = baseAuraRadius * squashY * combinedScaleY * auraScale
    val outerGlowRadiusX = auraRadiusX * OUTER_GLOW_MULTIPLIER
    val outerGlowRadiusY = auraRadiusY * OUTER_GLOW_MULTIPLIER
    val specRadius = min(coreRadiusX, coreRadiusY) * 0.55f
    val glowEnergyFactor = 0.85f + energy.energy * 0.15f
    val eyeRadius = min(coreRadiusX, coreRadiusY) * 0.22f
    val baseEyeOffsetX = coreRadiusX * 0.35f * eyeSeparationFactor
    val baseEyeOffsetY = -coreRadiusY * 0.2f
    val eyeMoodShift = -mouthCurve * eyeRadius * 0.18f
    val eyeTiltedLeft = rotateOffset(Offset(-baseEyeOffsetX, baseEyeOffsetY + eyeMoodShift), eyeTiltDegrees)
    val eyeTiltedRight = rotateOffset(Offset(baseEyeOffsetX, baseEyeOffsetY + eyeMoodShift), eyeTiltDegrees)
    val pupilOffset = Offset(eyeGazeX * eyeRadius, eyeGazeY * eyeRadius)
    val blendedMouthColor = lerp(palette.coreColor, palette.eyeColor, 0.35f)
    val mouthColor = if (mouthCurve < 0f) lerp(blendedMouthColor, Color.Black, 0.25f) else blendedMouthColor
    val mouthArcRadiusX = coreRadiusX * 0.7f
    val mouthArcRadiusY = coreRadiusY * 0.45f
    val mouthYOffset = coreRadiusY * 0.45f
    val mouthAlpha = (0.08f + abs(mouthCurve) * 0.08f).coerceIn(0f, 0.16f)
    val mouthThickness = max(1.dp.toPx(), mouthArcRadiusY * 0.08f)
    val mouthT = ((mouthCurve + 1f) * 0.5f).coerceIn(0f, 1f)
    val mouthStart = lerpFloat(160f, 200f, mouthT)
    val mouthSweep = lerpFloat(180f, 140f, mouthT)
    val focusRadiusX = coreRadiusX * 1.08f
    val focusRadiusY = coreRadiusY * 1.08f
    val focusWidth = max(2.dp.toPx(), min(3.dp.toPx(), min(coreRadiusX, coreRadiusY) * 0.06f))
    val arcColor = palette.glowColor.copy(alpha = 0.65f)
    val specOffset = Offset(-coreRadiusX * 0.35f, -coreRadiusY * 0.35f)
    val dismissCrossAlpha = 0.35f * dismissHighlight
    val dismissCrossColor = lerp(dismissOverlayColor, Color.White, 0.3f).copy(alpha = dismissCrossAlpha)
    val dismissCrossSize = min(coreRadiusX, coreRadiusY) * 0.45f
    val dismissCrossStroke = max(2.dp.toPx(), dismissCrossSize * 0.08f)

    withTransform({
        translate(center.x, center.y)
        rotate(tiltDegrees)
    }) {
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.auraColor.copy(alpha = auraAlpha * 0.75f),
                    palette.auraColor.copy(alpha = auraAlpha * 0.45f),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = max(auraRadiusX, auraRadiusY)
            ),
            topLeft = Offset(-auraRadiusX, -auraRadiusY),
            size = Size(auraRadiusX * 2, auraRadiusY * 2)
        )
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glowColor.copy(alpha = auraAlpha * 0.28f * glowEnergyFactor),
                    Color.Transparent
                ),
                center = Offset.Zero,
                radius = max(outerGlowRadiusX, outerGlowRadiusY)
            ),
            topLeft = Offset(-outerGlowRadiusX, -outerGlowRadiusY),
            size = Size(outerGlowRadiusX * 2, outerGlowRadiusY * 2)
        )

        if (state is AstraState.Thinking) {
            rotate(thinkingPhase, Offset.Zero) {
                drawArc(
                    color = arcColor,
                    startAngle = -80f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(-focusRadiusX, -focusRadiusY),
                    size = Size(focusRadiusX * 2, focusRadiusY * 2),
                    style = Stroke(width = focusWidth)
                )
            }
        }

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.coreColor,
                    palette.coreColor.copy(alpha = 0.82f),
                    Color.Black.copy(alpha = 0.2f)
                ),
                center = Offset.Zero,
                radius = max(coreRadiusX, coreRadiusY)
            ),
            topLeft = Offset(-coreRadiusX, -coreRadiusY),
            size = Size(coreRadiusX * 2, coreRadiusY * 2)
        )

        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.24f),
                    Color.Transparent
                ),
                center = specOffset,
                radius = specRadius
            ),
            topLeft = Offset(specOffset.x - specRadius, specOffset.y - specRadius),
            size = Size(specRadius * 2, specRadius * 2)
        )

        if (state is AstraState.Error) {
            drawOval(
                color = Color(0x66FF7043),
                topLeft = Offset(-coreRadiusX, -coreRadiusY),
                size = Size(coreRadiusX * 2, coreRadiusY * 2)
            )
        }

        drawArc(
            color = mouthColor.copy(alpha = mouthAlpha),
            startAngle = mouthStart,
            sweepAngle = mouthSweep,
            useCenter = false,
            topLeft = Offset(-mouthArcRadiusX, mouthYOffset - mouthArcRadiusY),
            size = Size(mouthArcRadiusX * 2, mouthArcRadiusY * 2),
            style = Stroke(width = mouthThickness)
        )

        if (dismissHighlight > 0.001f) {
            val crossHalf = dismissCrossSize * 0.5f
            drawLine(
                color = dismissCrossColor,
                start = Offset(-crossHalf, -crossHalf),
                end = Offset(crossHalf, crossHalf),
                strokeWidth = dismissCrossStroke
            )
            drawLine(
                color = dismissCrossColor,
                start = Offset(-crossHalf, crossHalf),
                end = Offset(crossHalf, -crossHalf),
                strokeWidth = dismissCrossStroke
            )
        }

        drawEye(
            center = eyeTiltedLeft,
            radius = eyeRadius,
            heightScale = eyeScaleY,
            alpha = eyeAlpha,
            palette = palette,
            pupilOffset = pupilOffset
        )
        drawEye(
            center = eyeTiltedRight,
            radius = eyeRadius,
            heightScale = eyeScaleY,
            alpha = eyeAlpha,
            palette = palette,
            pupilOffset = pupilOffset
        )
    }
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
                palette.eyeColor.copy(alpha = alpha),
                palette.eyeColor.copy(alpha = alpha * 0.3f),
                Color.Transparent
            ),
            center = center,
            radius = radius
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
                palette.eyeColor.copy(alpha = alpha),
                palette.eyeColor.copy(alpha = alpha * 0.4f),
                Color.Transparent
            ),
            center = pupilCenter,
            radius = pupilRadius
        ),
        radius = pupilRadius,
        center = pupilCenter
    )
}

private fun rotateOffset(offset: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    return Offset(
        x = offset.x * cosA - offset.y * sinA,
        y = offset.x * sinA + offset.y * cosA
    )
}

private fun lerpFloat(start: Float, end: Float, t: Float): Float {
    return start + (end - start) * t
}

private data class OrbHudAction(
    val label: String,
    val icon: String,
    val onClick: () -> Unit
)

@Composable
private fun OrbQuickActionsHud(
    visible: Boolean,
    actions: List<OrbHudAction>,
    onDismiss: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "hud_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.9f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "hud_scale"
    )

    if (alpha <= 0.01f || actions.isEmpty()) {
        return
    }

    val dismissInteraction = remember { MutableInteractionSource() }
    val alignments = remember {
        listOf(
            Alignment.TopStart,
            Alignment.TopEnd,
            Alignment.BottomStart,
            Alignment.BottomEnd
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = visible,
                indication = null,
                interactionSource = dismissInteraction
            ) {
                onDismiss()
            }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        actions.take(alignments.size).forEachIndexed { index, action ->
            val alignment = alignments[index]
            OrbHudActionChip(
                action = action,
                alpha = alpha,
                scale = scale,
                onDismiss = onDismiss,
                modifier = Modifier.align(alignment)
            )
        }
    }
}

@Composable
private fun OrbHudActionChip(
    action: OrbHudAction,
    alpha: Float,
    scale: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .width(48.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = actionInteraction,
                    indication = null
                ) {
                    action.onClick()
                    onDismiss()
                },
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 8.dp,
            tonalElevation = 4.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = action.icon,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = action.label,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
