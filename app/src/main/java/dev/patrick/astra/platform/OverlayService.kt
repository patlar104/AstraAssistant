package dev.patrick.astra.platform

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.patrick.astra.assistant.OverlayUiStateStore
import dev.patrick.astra.ui.BUBBLE_MAX_VISUAL_SCALE_X
import dev.patrick.astra.ui.BUBBLE_MAX_VISUAL_SCALE_Y
import dev.patrick.astra.ui.MainActivity
import dev.patrick.astra.ui.OverlayBubble
import dev.patrick.astra.ui.theme.AstraAssistantTheme

/**
 * Foreground-like overlay service that shows a draggable Astra bubble on top
 * of other apps. Tapping the bubble brings MainActivity to the foreground.
 *
 * This uses a ComposeView attached to a TYPE_APPLICATION_OVERLAY window.
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
            x = screenWidth / 2
            y = screenHeight / 2
        }

        val bubbleMarginPx = (24 * displayMetrics.density).toInt()

        // Final safety clamp before first show
        val baseClampW = (64 * displayMetrics.density).toInt()
        val baseClampH = (64 * displayMetrics.density).toInt()
        val effectiveClampW = (baseClampW * BUBBLE_MAX_VISUAL_SCALE_X).toInt()
        val effectiveClampH = (baseClampH * BUBBLE_MAX_VISUAL_SCALE_Y).toInt()
        params.x = params.x.coerceIn(
            bubbleMarginPx,
            screenWidth - effectiveClampW - bubbleMarginPx
        )
        params.y = params.y.coerceIn(
            bubbleMarginPx,
            screenHeight - effectiveClampH - bubbleMarginPx
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
                        onClick = {
                            bringMainActivityToFront()
                        },
                        onDrag = { dx, dy ->
                            val layoutParams = params

                            val w = if (bubbleWidthPx > 0) bubbleWidthPx else (64 * displayMetrics.density).toInt()
                            val h = if (bubbleHeightPx > 0) bubbleHeightPx else (64 * displayMetrics.density).toInt()
                            val effectiveW = (w * BUBBLE_MAX_VISUAL_SCALE_X).toInt()
                            val effectiveH = (h * BUBBLE_MAX_VISUAL_SCALE_Y).toInt()

                            // Update raw position
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()

                            // Clamp the bubble so it can't fully leave the screen
                            val minX = bubbleMarginPxInner
                            val maxX = screenWidth - effectiveW - bubbleMarginPxInner
                            val minY = bubbleMarginPxInner
                            val maxY = screenHeight - effectiveH - bubbleMarginPxInner

                            layoutParams.x = layoutParams.x.coerceIn(minX, maxX)
                            layoutParams.y = layoutParams.y.coerceIn(minY, maxY)

                            lastWindowX = layoutParams.x
                            lastWindowY = layoutParams.y

                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onDragEnd = {
                            val layoutParams = params

                            val w = if (bubbleWidthPx > 0) bubbleWidthPx else (64 * displayMetrics.density).toInt()
                            val h = if (bubbleHeightPx > 0) bubbleHeightPx else (64 * displayMetrics.density).toInt()
                            val effectiveW = (w * BUBBLE_MAX_VISUAL_SCALE_X).toInt()
                            val effectiveH = (h * BUBBLE_MAX_VISUAL_SCALE_Y).toInt()

                            // Decide which edge to snap to based on current x
                            val midX = screenWidth / 2
                            val snappedX = if (lastWindowX > midX) {
                                screenWidth - effectiveW - bubbleMarginPxInner
                            } else {
                                bubbleMarginPxInner
                            }

                            layoutParams.x = snappedX
                            layoutParams.y = layoutParams.y.coerceIn(
                                bubbleMarginPxInner,
                                screenHeight - effectiveH - bubbleMarginPxInner
                            )

                            lastWindowX = layoutParams.x
                            lastWindowY = layoutParams.y

                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onLongPress = {
                            // Future: radial menu or quick actions
                        },
                        onLayoutChanged = { widthPx, heightPx ->
                            bubbleWidthPx = widthPx
                            bubbleHeightPx = heightPx
                        }
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


    companion object {
        fun canDrawOverlays(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }
    }
}
