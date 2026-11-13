package com.android.gamespace.ui.overlay

import android.content.Context
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 * The lifecycle and the saved state that Compose looks for above its view. An activity has them;
 * a window that a service puts on the screen does not, so it gets this one. It is resumed while
 * the window is on the screen and gone after it.
 */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * A Compose UI in a window of its own, put on the screen with the [WindowManager]: what a service
 * does to draw over a game. Use it once: [attach] it, [update] its layout while it is there, and
 * [detach] it, after which it is finished and a new one is made for the next time.
 *
 * Main thread only.
 */
class ComposeOverlay(context: Context, content: @Composable () -> Unit) {

    private val owner = OverlayLifecycleOwner()

    private val composeView = ComposeView(context).apply {
        ViewTreeLifecycleOwner.set(this, owner)
        ViewTreeSavedStateRegistryOwner.set(this, owner)
        setContent { OverlayTheme(content) }
    }

    var isAttached = false
        private set

    /** The width of the window in pixels, once it has been laid out. */
    val width: Int get() = composeView.width

    private var finished = false

    fun attach(windowManager: WindowManager, params: WindowManager.LayoutParams) {
        if (isAttached || finished) return
        owner.resume()
        windowManager.addView(composeView, params)
        isAttached = true
    }

    fun update(windowManager: WindowManager, params: WindowManager.LayoutParams) {
        if (isAttached) windowManager.updateViewLayout(composeView, params)
    }

    /** Takes the window off the screen. With [immediate] it is gone when this returns. */
    fun detach(windowManager: WindowManager, immediate: Boolean = false) {
        if (finished) return
        finished = true
        if (isAttached) {
            isAttached = false
            runCatching {
                if (immediate) windowManager.removeViewImmediate(composeView)
                else windowManager.removeView(composeView)
            }
        }
        owner.destroy()
    }
}
