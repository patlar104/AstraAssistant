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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 40  // offset from the right edge
            y = 0
        }

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

                    OverlayBubble(
                        state = uiState.state,
                        emotion = uiState.emotion,
                        onClick = {
                            bringMainActivityToFront()
                        },
                        onDrag = { dx, dy ->
                            val layoutParams = params
                            layoutParams.x += dx.toInt()
                            layoutParams.y += dy.toInt()
                            lastWindowX = layoutParams.x
                            lastWindowY = layoutParams.y
                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onDragEnd = {
                            val layoutParams = params
                            val displayWidth = resources.displayMetrics.widthPixels
                            val middle = displayWidth / 2

                            layoutParams.x = if (lastWindowX > middle) {
                                displayWidth / 2 - 40
                            } else {
                                -displayWidth / 2 + 40
                            }

                            wm?.updateViewLayout(this@apply, layoutParams)
                        },
                        onLongPress = {
                            // Future: radial menu or quick actions
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
