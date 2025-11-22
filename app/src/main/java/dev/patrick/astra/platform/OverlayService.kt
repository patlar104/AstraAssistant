package dev.patrick.astra.platform

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

    // Last touch coordinates for dragging
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

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
                    OverlayBubble(
                        onClick = {
                            bringMainActivityToFront()
                        }
                    )
                }
            }
        }

        // Wrap in a touchable container to support dragging
        composeView.setOnTouchListener { v, event ->
            val wm = windowManager ?: return@setOnTouchListener false
            val lp = params

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    lp.x = initialX - dx
                    lp.y = initialY + dy
                    wm.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Optional: snap to edges or add click vs drag threshold
                    false
                }
                else -> false
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
