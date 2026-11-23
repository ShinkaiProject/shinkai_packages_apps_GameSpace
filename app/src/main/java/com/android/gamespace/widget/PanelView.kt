package com.android.gamespace.widget

import android.content.Context
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.gamespace.data.AppSettings
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.compose.panel.GameSpacePanel
import com.android.gamespace.widget.compose.theme.GameSpaceTheme

class PanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val appSettings: AppSettings by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().appSettings()
    }

    private val panelLifecycleOwner = PanelLifecycleOwner()
    private val composeView = ComposeView(context)

    init {
        isClickable = true
        isFocusable = true
        composeView.setViewTreeLifecycleOwner(panelLifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(panelLifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(panelLifecycleOwner)
        composeView.setContent {
            GameSpaceTheme {
                GameSpacePanel(appSettings = appSettings)
            }
        }
        addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun updateTranslationY() {
        animate().translationY(appSettings.y.toFloat())
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rootView.setViewTreeLifecycleOwner(panelLifecycleOwner)
        rootView.setViewTreeSavedStateRegistryOwner(panelLifecycleOwner)
        rootView.setViewTreeViewModelStoreOwner(panelLifecycleOwner)
        panelLifecycleOwner.resume()
        updateTranslationY()
    }

    override fun onDetachedFromWindow() {
        animate().cancel()
        panelLifecycleOwner.pause()
        super.onDetachedFromWindow()
    }

    fun destroy() {
        composeView.disposeComposition()
        panelLifecycleOwner.destroy()
    }
}

private class PanelLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    init {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun resume() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    fun pause() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.CREATED
        }
    }

    fun destroy() {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }
}
