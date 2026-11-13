package com.android.gamespace.widget.tiles

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

/**
 * Immutable UI state snapshot for a single GameSpace quick-settings tile rendered in Compose.
 */
@Immutable
data class TileUiState(
    val title: String = "",
    val summary: String = "",
    @DrawableRes val iconRes: Int = 0,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
)

/**
 * Base controller and reactive state holder for GameSpace panel tiles.
 *
 * Keeps system integration, broadcast receivers, content observers, and service callbacks
 * completely separate from the pure Jetpack Compose UI (`GameSpaceTileCell`), while preserving
 * the lifecycle and property contract used by all tile implementations.
 */
abstract class BaseTile @JvmOverloads constructor(
    val context: Context,
    @Suppress("UNUSED_PARAMETER") attrs: AttributeSet? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _uiState = mutableStateOf(TileUiState())

    /** Reactive Compose state observed by `GameSpaceTileCell`. */
    val uiState: State<TileUiState> get() = _uiState

    private var isAttached = false

    val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    val systemSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().systemSettings() }

    inner class TileTextHolder internal constructor(
        private val selector: (TileUiState) -> String,
        private val updater: (TileUiState, String) -> TileUiState,
    ) {
        var text: CharSequence?
            get() = selector(_uiState.value)
            set(value) {
                val resolved = value?.toString().orEmpty()
                updateUiState { current ->
                    if (selector(current) == resolved) current else updater(current, resolved)
                }
            }
    }

    inner class TileIconHolder internal constructor() {
        @DrawableRes
        var imageResource: Int = 0
            private set

        fun setImageResource(@DrawableRes resId: Int) {
            imageResource = resId
            updateUiState { current ->
                if (current.iconRes == resId) current else current.copy(iconRes = resId)
            }
        }
    }

    val title: TileTextHolder? = TileTextHolder(
        selector = { it.title },
        updater = { state, text -> state.copy(title = text) },
    )

    val summary: TileTextHolder? = TileTextHolder(
        selector = { it.summary },
        updater = { state, text -> state.copy(summary = text) },
    )

    val icon: TileIconHolder? = TileIconHolder()

    var isSelected: Boolean
        get() = _uiState.value.isSelected
        set(value) {
            updateUiState { current ->
                if (current.isSelected == value) current else current.copy(isSelected = value)
            }
        }

    var isEnabled: Boolean
        get() = _uiState.value.isEnabled
        set(value) {
            updateUiState { current ->
                if (current.isEnabled == value) current else current.copy(isEnabled = value)
            }
        }

    internal fun seedDefaults(@StringRes titleRes: Int, @DrawableRes iconRes: Int) {
        val defaultTitle = runCatching { context.getString(titleRes) }.getOrDefault("")
        updateUiState { current ->
            current.copy(
                title = current.title.ifEmpty { defaultTitle },
                iconRes = if (current.iconRes != 0) current.iconRes else iconRes,
            )
        }
    }

    private fun updateUiState(transform: (TileUiState) -> TileUiState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _uiState.value = transform(_uiState.value)
        } else {
            mainHandler.post {
                _uiState.value = transform(_uiState.value)
            }
        }
    }

    fun post(action: Runnable): Boolean = mainHandler.post(action)

    /** Attaches the tile to start observing system state changes. */
    fun attach() {
        if (isAttached) return
        isAttached = true
        onAttachedToWindow()
    }

    /** Detaches the tile and unregisters all system observers/callbacks. */
    fun detach() {
        if (!isAttached) return
        isAttached = false
        onDetachedFromWindow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /** Invokes the tile's primary tap action. */
    fun performClick() {
        if (!isEnabled) return
        onClick(null)
    }

    /** Invokes the tile's long-press action, returning true if consumed. */
    fun performLongClick(): Boolean {
        if (!isEnabled) return false
        return onLongClick(null)
    }

    protected open fun onAttachedToWindow() = Unit

    protected open fun onDetachedFromWindow() = Unit

    open fun onClick(v: View?) {
        isSelected = !isSelected
    }

    open fun onLongClick(v: View?): Boolean = false

    protected fun openSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
