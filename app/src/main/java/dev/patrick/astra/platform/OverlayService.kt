package dev.patrick.astra.platform

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
import dev.patrick.astra.assistant.OverlayUiStateStore
import dev.patrick.astra.ui.AstraState
import dev.patrick.astra.ui.Emotion
import dev.patrick.astra.ui.MainActivity
import dev.patrick.astra.ui.OverlayBubble
import dev.patrick.astra.ui.ORB_BASE_DP
import dev.patrick.astra.ui.ORB_VISUAL_CONTAINER_SCALE
import dev.patrick.astra.ui.theme.AstraAssistantTheme
import kotlin.math.roundToInt

/**
 * Foreground-like overlay service that shows a draggable Astra bubble on top
 * of other apps. Tapping the bubble brings MainActivity to the foreground.
 *
 * This uses a ComposeView attached to a TYPE_APPLICATION_OVERLAY window.
 *
 * Summary:
 * - Bounds use actual measured bubble size (fallback = 96dp * 1.1) with 12dp margins.
 * - Drag clamps use coerceAtLeast to avoid empty ranges; snap uses same dimensions.
 * - OverlayBubble delivers onTap/onDrag/onPressChange without extra hitbox padding.
 */
class OverlayService :
    LifecycleService(),
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    // Drag state for overlay position
    private var lastWindowX: Int = 0
    private var lastWindowY: Int = 0
    // Real measured size of the Compose bubble in px
    private var bubbleWidthPx: Int = 0
    private var bubbleHeightPx: Int = 0
    private val inDismissZoneState = mutableStateOf(false)

    // ViewModelStoreOwner implementation
    override val viewModelStore: ViewModelStore = ViewModelStore()

    // Controller managing SavedStateRegistry for this service
    private val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)

    // SavedStateRegistryOwner implementation
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()

        // Attach and restore saved state for this owner
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        // If we don't have permission, stop immediately.
        if (!canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val density = displayMetrics.density

        windowManager = getSystemService()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        ).apply {
            // Use a simple top-start anchor; x/y are offsets from top-left
            gravity = Gravity.TOP or Gravity.START
            val fallbackSizePx = (ORB_BASE_DP * ORB_VISUAL_CONTAINER_SCALE * density).roundToInt()
            val marginPx = (12f * density).roundToInt()
            x = screenWidth - fallbackSizePx - marginPx
            y = screenHeight / 2 - fallbackSizePx / 2
        }

        // Keep fallback size aligned with the Compose container to avoid bounds mismatch.
        val bubbleMarginPx = (12f * density).roundToInt()
        val fallbackBubbleSizePx = (ORB_BASE_DP * ORB_VISUAL_CONTAINER_SCALE * density).roundToInt()
        val dismissTop = (screenHeight * 0.75f).roundToInt()
        val dismissBottom = screenHeight - bubbleMarginPx
        val dismissCenterX = screenWidth / 2
        val dismissHalfWidth = (screenWidth * 0.35f).roundToInt()

        // Final safety clamp before first show
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

        val wm = windowManager
        val composeView = ComposeView(this).apply {
            // Ensure Compose disposes correctly when the view is removed
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )

            // Attach a LifecycleOwner so WindowRecomposer can be created
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                AstraAssistantTheme {
                    // Observe overlay UI state from the shared store
                    val uiState by OverlayUiStateStore.overlayUiState.collectAsState()

                    val bubbleMarginPxInner = bubbleMarginPx

                    OverlayBubble(
                        state = uiState.state,
                        emotion = uiState.emotion,
                        onTap = {
                            bringMainActivityToFront()
                        },
                        onLongPress = {
                            // Future: radial menu or quick actions
                        },
                        onDrag = { dx, dy ->
                            val layoutParams = params

                            val effectiveW = if (bubbleWidthPx > 0) bubbleWidthPx else fallbackBubbleSizePx
                            val effectiveH = if (bubbleHeightPx > 0) bubbleHeightPx else fallbackBubbleSizePx

                            // Update raw position
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()

                            // Clamp the bubble so it can't fully leave the screen; coerceAtLeast prevents empty ranges.
                            val minX = bubbleMarginPxInner
                            val maxX = (screenWidth - effectiveW - bubbleMarginPxInner)
                                .coerceAtLeast(minX)
                            val minY = bubbleMarginPxInner
                            val maxY = (screenHeight - effectiveH - bubbleMarginPxInner)
                                .coerceAtLeast(minY)

                            layoutParams.x = layoutParams.x.coerceIn(minX, maxX)
                            layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

                            lastWindowX = layoutParams.x
                            lastWindowY = layoutParams.y

                            val centerX = layoutParams.x + effectiveW / 2
                            val centerY = layoutParams.y + effectiveH / 2
                            val inHorizontalBand = kotlin.math.abs(centerX - dismissCenterX) <= dismissHalfWidth
                            val inVerticalBand = centerY in dismissTop..dismissBottom
                            val inDismissZone = inHorizontalBand && inVerticalBand
                            if (inDismissZoneState.value != inDismissZone) {
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        DISMISS_TAG,
                                        if (inDismissZone) "Entered dismiss zone" else "Exited dismiss zone"
                                    )
                                }
                                inDismissZoneState.value = inDismissZone
                            }

                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onDragEnd = {
                            val layoutParams = params

                            val effectiveW = if (bubbleWidthPx > 0) bubbleWidthPx else fallbackBubbleSizePx
                            val effectiveH = if (bubbleHeightPx > 0) bubbleHeightPx else fallbackBubbleSizePx
                            val shouldDismiss = inDismissZoneState.value
                            inDismissZoneState.value = false
                            if (shouldDismiss) {
                                dismissOverlay()
                                return@OverlayBubble
                            }

                            // Decide which edge to snap to based on current x
                            val midX = screenWidth / 2
                            val snappedX = if (lastWindowX > midX) {
                                screenWidth - effectiveW - bubbleMarginPxInner
                            } else {
                                bubbleMarginPxInner
                            }

                            val minY = bubbleMarginPxInner
                            val maxY = (screenHeight - effectiveH - bubbleMarginPxInner)
                                .coerceAtLeast(minY)

                            layoutParams.x = snappedX
                            layoutParams.y = layoutParams.y.coerceIn(
                                minY,
                                maxY
                            )

                            lastWindowX = layoutParams.x
                            lastWindowY = layoutParams.y

                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onLayoutChanged = { widthPx, heightPx ->
                            bubbleWidthPx = widthPx
                            bubbleHeightPx = heightPx
                        },
                        isInDismissZone = inDismissZoneState.value,
                        onDismissTriggered = { dismissOverlay() },
                        onRequestVoice = { requestVoice() },
                        onRequestTranslate = { requestTranslate() },
                        onRequestSettings = { requestSettings() },
                        onRequestHide = { requestHide() }
                    )
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
        OverlayUiStateStore.set(
            state = AstraState.Listening(intensity = 1f),
            emotion = Emotion.Focused
        )
        bringMainActivityToFront()
    }

    private fun requestTranslate() {
        OverlayUiStateStore.update(
            state = AstraState.Thinking(mood = Emotion.Curious),
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


    companion object {
        private const val DISMISS_TAG = "OverlayServiceDismiss"
        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
}
