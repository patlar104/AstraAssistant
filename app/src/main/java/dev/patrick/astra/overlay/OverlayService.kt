package dev.patrick.astra.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.MotionEvent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.patrick.astra.BuildConfig
import dev.patrick.astra.domain.AssistantPhase
import dev.patrick.astra.domain.AssistantStateStore
import dev.patrick.astra.domain.DebugFlags
import dev.patrick.astra.domain.Emotion
import dev.patrick.astra.ui.MainActivity
import dev.patrick.astra.ui.theme.AstraAssistantTheme
import kotlin.math.roundToInt

class OverlayService :
    LifecycleService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var params: WindowManager.LayoutParams

    private var lastWindowX: Int = 0
    private var lastWindowY: Int = 0
    private var lastWindowXF: Float = 0f
    private var lastWindowYF: Float = 0f
    private var bubbleWidthPx: Int = 0
    private var bubbleHeightPx: Int = 0
    private var baseBubbleWidthPx: Int = 0
    private var hudOffsetAppliedPx: Int = 0
    private var hudVisible: Boolean = false
    private val inDismissZoneState = mutableStateOf(false)
    private val overlaySideState = mutableStateOf(OverlaySide.Right)
    private var dragEverInDismissZone: Boolean = false
    private var hudDismissSignal: Int = 0

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        if (!canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density
        val bubbleMarginPx = (12f * density).roundToInt()
        val fallbackBubbleSizePx = (ORB_BASE_DP * ORB_VISUAL_CONTAINER_SCALE * density).roundToInt()

        windowManager = getSystemService()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - fallbackBubbleSizePx - bubbleMarginPx
            y = screenHeight / 2 - fallbackBubbleSizePx / 2
        }

        params.x = params.x.coerceIn(
            bubbleMarginPx,
            (screenWidth - fallbackBubbleSizePx - bubbleMarginPx).coerceAtLeast(bubbleMarginPx)
        )
        params.y = params.y.coerceIn(
            bubbleMarginPx,
            (screenHeight - fallbackBubbleSizePx - bubbleMarginPx).coerceAtLeast(bubbleMarginPx)
        )

        lastWindowX = params.x
        lastWindowY = params.y
        lastWindowXF = params.x.toFloat()
        lastWindowYF = params.y.toFloat()
        overlaySideState.value = if (params.x + (fallbackBubbleSizePx / 2) > screenWidth / 2) {
            OverlaySide.Right
        } else {
            OverlaySide.Left
        }

        val wm = windowManager
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                AstraAssistantTheme {
                    val visualState by AssistantStateStore.visualState.collectAsState()
                    val overlayLogsEnabled by DebugFlags.overlayLogsEnabled.collectAsState()

                    fun currentWidth(): Int = if (bubbleWidthPx > 0) bubbleWidthPx else fallbackBubbleSizePx
                    fun currentHeight(): Int = if (bubbleHeightPx > 0) bubbleHeightPx else fallbackBubbleSizePx

                    fun clampAndUpdatePosition(dx: Float, dy: Float) {
                        val wmLocal = wm ?: return
                        val layoutParams = params
                        val effectiveW = currentWidth()
                        val effectiveH = currentHeight()

                        lastWindowXF += dx
                        lastWindowYF += dy
                        val candidateX = lastWindowXF.roundToInt()
                        val candidateY = lastWindowYF.roundToInt()

                        if (
                            kotlin.math.abs(candidateX - layoutParams.x) < MOVE_THRESHOLD_PX &&
                            kotlin.math.abs(candidateY - layoutParams.y) < MOVE_THRESHOLD_PX
                        ) {
                            return
                        }

                        val minX = bubbleMarginPx
                        val maxX = (screenWidth - effectiveW - bubbleMarginPx).coerceAtLeast(minX)
                        val minY = bubbleMarginPx
                        val maxY = (screenHeight - effectiveH - bubbleMarginPx).coerceAtLeast(minY)

                        layoutParams.x = candidateX.coerceIn(minX, maxX)
                        layoutParams.y = candidateY.coerceIn(minY, maxY)

                        lastWindowX = layoutParams.x
                        lastWindowY = layoutParams.y
                        lastWindowXF = layoutParams.x.toFloat()
                        lastWindowYF = layoutParams.y.toFloat()

                        val centerX = layoutParams.x + effectiveW / 2
                        val centerY = layoutParams.y + effectiveH / 2
                        val dismissTop = (screenHeight * 0.75f).roundToInt()
                        val dismissBottom = screenHeight - bubbleMarginPx
                        val dismissCenterX = screenWidth / 2
                        val dismissHalfWidth = (screenWidth * 0.35f).roundToInt()
                        val inHorizontalBand = kotlin.math.abs(centerX - dismissCenterX) <= dismissHalfWidth
                        val inVerticalBand = centerY in dismissTop..dismissBottom
                        val inDismissZone = inHorizontalBand && inVerticalBand

                        if (inDismissZone) {
                            dragEverInDismissZone = true
                        }

                        if (inDismissZoneState.value != inDismissZone) {
                            if (BuildConfig.DEBUG) {
                                Log.d(
                                    DISMISS_TAG,
                                    if (inDismissZone) "Entered dismiss zone" else "Exited dismiss zone"
                                )
                            }
                            inDismissZoneState.value = inDismissZone
                        }

                        overlaySideState.value = if (layoutParams.x + effectiveW / 2 > screenWidth / 2) {
                            OverlaySide.Right
                        } else {
                            OverlaySide.Left
                        }
                        if (overlayLogsEnabled && BuildConfig.DEBUG) {
                            Log.d(
                                OVERLAY_DEBUG_TAG,
                                "drag dx=$dx dy=$dy layout=(${layoutParams.x},${layoutParams.y}) rangeX=[$minX,$maxX] rangeY=[$minY,$maxY] " +
                                    "sizes=${effectiveW}x${effectiveH} screen=${screenWidth}x${screenHeight} hud=$hudVisible dismiss=$inDismissZone"
                            )
                        }
                        wmLocal.updateViewLayout(this@apply, layoutParams)
                    }

                    fun snapToEdge() {
                        val wmLocal = wm ?: return
                        val layoutParams = params
                        val effectiveW = currentWidth()
                        val effectiveH = currentHeight()
                        val minX = bubbleMarginPx
                        val maxX = (screenWidth - effectiveW - bubbleMarginPx).coerceAtLeast(minX)
                        val snappedX = if (lastWindowX > screenWidth / 2) maxX else minX
                        val minY = bubbleMarginPx
                        val maxY = (screenHeight - effectiveH - bubbleMarginPx).coerceAtLeast(minY)

                        layoutParams.x = snappedX
                        layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

                        lastWindowX = layoutParams.x
                        lastWindowY = layoutParams.y
                        lastWindowXF = layoutParams.x.toFloat()
                        lastWindowYF = layoutParams.y.toFloat()

                        overlaySideState.value = if (layoutParams.x + effectiveW / 2 > screenWidth / 2) {
                            OverlaySide.Right
                        } else {
                            OverlaySide.Left
                        }

                        wmLocal.updateViewLayout(this@apply, layoutParams)
                    }

                    fun adjustForHud() {
                        val wmLocal = wm ?: return
                        val currentW = currentWidth()
                        if (baseBubbleWidthPx == 0) {
                            baseBubbleWidthPx = currentW
                        }
                        val widthDiff = (currentW - baseBubbleWidthPx).coerceAtLeast(0)
                        val isRightSide = overlaySideState.value == OverlaySide.Right
                        val targetX = when {
                            isRightSide && hudVisible -> lastWindowX - widthDiff
                            isRightSide && !hudVisible -> lastWindowX + widthDiff
                            else -> lastWindowX
                        }

                        val minX = bubbleMarginPx
                        val maxX = (screenWidth - currentW - bubbleMarginPx).coerceAtLeast(minX)

                        params.x = targetX.coerceIn(minX, maxX)
                        lastWindowX = params.x
                        lastWindowY = params.y
                        lastWindowXF = params.x.toFloat()
                        lastWindowYF = params.y.toFloat()
                        hudOffsetAppliedPx = if (hudVisible && isRightSide) widthDiff else 0
                        overlaySideState.value =
                            if (params.x + currentW / 2 > screenWidth / 2) {
                                OverlaySide.Right
                            } else {
                                OverlaySide.Left
                            }
                        if (overlayLogsEnabled && BuildConfig.DEBUG) {
                            Log.d(
                                OVERLAY_DEBUG_TAG,
                                "adjustForHud currentW=$currentW baseW=$baseBubbleWidthPx widthDiff=$widthDiff hud=$hudVisible side=$isRightSide params=(${params.x},${params.y}) rangeX=[$minX,$maxX]"
                            )
                        }
                        wmLocal.updateViewLayout(this@apply, params)
                    }

                    OverlayBubble(
                        visualState = visualState,
                        onTap = { bringMainActivityToFront() },
                        onLongPress = { },
                        onDrag = { dx, dy -> clampAndUpdatePosition(dx, dy) },
                        onDragEnd = {
                            val isCurrentlyInDismissZone = inDismissZoneState.value
                            dragEverInDismissZone = false
                            inDismissZoneState.value = false
                            if (isCurrentlyInDismissZone) {
                                dismissOverlay()
                                return@OverlayBubble
                            }
                            snapToEdge()
                        },
                        onLayoutChanged = { widthPx, heightPx ->
                            bubbleWidthPx = widthPx
                            bubbleHeightPx = heightPx
                            if (!hudVisible) {
                                baseBubbleWidthPx = widthPx
                            }
                            if (overlayLogsEnabled && BuildConfig.DEBUG) {
                                Log.d(
                                    OVERLAY_DEBUG_TAG,
                                    "onLayoutChanged measured=${widthPx}x${heightPx} fallback=${fallbackBubbleSizePx} base=$baseBubbleWidthPx"
                                )
                            }
                            adjustForHud()
                        },
                        isInDismissZone = inDismissZoneState.value,
                        onDismissTriggered = { dismissOverlay() },
                        onRequestVoice = { requestVoice() },
                        onRequestTranslate = { requestTranslate() },
                        onRequestSettings = { requestSettings() },
                        onRequestHide = { requestHide() },
                        overlaySide = overlaySideState.value,
                        onHudVisibilityChanged = { visible, _ ->
                            hudVisible = visible
                            updateTouchFlags(visible)
                            adjustForHud()
                        },
                        hudDismissSignal = hudDismissSignal
                    )
                }
            }
        }.also { view ->
            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    hudDismissSignal++
                    updateTouchFlags(false)
                    true
                } else {
                    false
                }
            }
        }

        overlayView = composeView
        windowManager?.addView(composeView, params)
    }

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
        windowManager = null
        viewModelStore.clear()
        super.onDestroy()
    }

    private fun bringMainActivityToFront() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun requestVoice() {
        AssistantStateStore.set(
            phase = AssistantPhase.Listening,
            emotion = Emotion.Focused
        )
        bringMainActivityToFront()
    }

    private fun requestTranslate() {
        AssistantStateStore.set(
            phase = AssistantPhase.Thinking,
            emotion = Emotion.Curious
        )
    }

    private fun requestSettings() {
        bringMainActivityToFront()
    }

    private fun requestHide() {
        dismissOverlay()
    }

    private fun dismissOverlay() {
        if (BuildConfig.DEBUG) {
            Log.d(DISMISS_TAG, "Dismissing overlay from dismiss zone")
        }
        stopSelf()
    }

    private fun updateTouchFlags(isMenuOpen: Boolean) {
        val wmLocal = windowManager ?: return
        if (!::params.isInitialized || overlayView == null) return
        if (isMenuOpen) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        wmLocal.updateViewLayout(overlayView, params)
    }

    companion object {
        private const val MOVE_THRESHOLD_PX = 4f
        private const val DISMISS_TAG = "OverlayServiceDismiss"
        private const val OVERLAY_DEBUG_TAG = "OverlayDebug"
        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
}
