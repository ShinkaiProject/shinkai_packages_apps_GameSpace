package com.android.gamespace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ViewFlipper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.dp
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.tiles.GameSpaceTilePager
import com.android.gamespace.widget.tiles.TileType

class PanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    private var nowPlaying: NowPlayingController? = null

    private var selectedTileTypes by mutableStateOf<List<TileType>>(emptyList())
    private var isTilePagerActive by mutableStateOf(false)
    private var panelLifecycleOwner: PanelLifecycleOwner? = null

    private val settingsChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppSettings.KEY_SELECTED_TILES && !editing && isAttachedToWindow) {
                populateTiles()
            }
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_view, this, true)
        isClickable = true
        isFocusable = true
        setupTilePagerComposeView()
    }

    private fun setupTilePagerComposeView() {
        val pagerComposeView = requireViewById<ComposeView>(R.id.tiles_pager)
        pagerComposeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        pagerComposeView.setContent {
            if (isTilePagerActive) {
                GameSpaceTilePager(tiles = selectedTileTypes)
            }
        }
    }

    private fun ensureViewTreeOwners() {
        if (findViewTreeLifecycleOwner() == null || findViewTreeSavedStateRegistryOwner() == null) {
            val owner = PanelLifecycleOwner().also {
                it.onAttach()
                panelLifecycleOwner = it
            }
            val targetRoot = rootView ?: this
            targetRoot.setViewTreeLifecycleOwner(owner)
            targetRoot.setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
        }
    }

    private fun releaseViewTreeOwners() {
        panelLifecycleOwner?.onDetach()
        panelLifecycleOwner = null
    }

    fun updateTranslationY() {
        val targetMargin = appSettings.y
        val params = layoutParams as ViewGroup.MarginLayoutParams
        val animator = ValueAnimator.ofInt(params.topMargin, targetMargin)
        animator.duration = 300L
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { valueAnimator ->
            params.topMargin = valueAnimator.animatedValue as Int
            layoutParams = params
        }
        animator.start()
    }

    override fun onAttachedToWindow() {
        ensureViewTreeOwners()
        super.onAttachedToWindow()
        appSettings.registerOnChange(settingsChangeListener)
        updateTranslationY()
        batteryTemperature()
        populateTiles()
        setupControlsFlipper()
        setupSliderIcons()
        setupEditButton()
    }

    override fun onDetachedFromWindow() {
        appSettings.unregisterOnChange(settingsChangeListener)
        nowPlaying?.stop()
        nowPlaying = null
        // The panel is reused, and attaching it builds its tiles again.
        setEditing(false, animate = false, rebuildTiles = false)
        isTilePagerActive = false
        selectedTileTypes = emptyList()
        requireViewById<ComposeView>(R.id.tiles_pager).disposeComposition()
        super.onDetachedFromWindow()
        releaseViewTreeOwners()
    }

    private val panelContentTransition = ChangeBounds().apply {
        duration = 200L
        interpolator = AccelerateDecelerateInterpolator()
    }

    private fun setupControlsFlipper() {
        val flipper = requireViewById<ViewFlipper>(R.id.controls_flipper)
        flipper.setMeasureAllChildren(false)
        val contentContainer = requireViewById<ViewGroup>(R.id.panel_content_container)
        val arrow = requireViewById<ImageView>(R.id.controls_nav_arrow)
        arrow.setOnClickListener {
            TransitionManager.beginDelayedTransition(contentContainer, panelContentTransition)
            flipper.showNext()
            arrow.animate().rotationBy(180f).setDuration(200L).start()
        }
        nowPlaying = NowPlayingController(context, flipper).also { it.start() }
    }

    private fun populateTiles() {
        selectedTileTypes = appSettings.selectedTiles.mapNotNull { TileType.fromId(it) }
        isTilePagerActive = !editing
    }

    private var editing = false
    private var tileEditor: PanelTileEditor? = null

    private fun setupEditButton() {
        requireViewById<ImageButton>(R.id.panel_edit_button).setOnClickListener {
            setEditing(!editing)
        }
    }

    /** The container's bottom margin while it is given up for the editor; see [setEditing]. */
    private var containerBottomMargin = 0

    /**
     * Swaps the panel's content for the tile editor, or back. Ending the edit shows the new tiles
     * right away.
     *
     * The header stays where it is and the editor's list scrolls on its own below it, cut off at
     * its edges. The list runs down to the bottom edge of the panel (the container gives up its
     * bottom margin for it) and has the height the content had, so the panel keeps its size.
     * Never taller than what the panel can show, so the panel itself has nothing to scroll and
     * the header cannot be moved out of the way.
     */
    private fun setEditing(enabled: Boolean, animate: Boolean = true, rebuildTiles: Boolean = true) {
        if (editing == enabled) return
        editing = enabled

        val normal = requireViewById<View>(R.id.panel_normal_content)
        val list = requireViewById<RecyclerView>(R.id.panel_edit_list)
        val container = requireViewById<ViewGroup>(R.id.panel_content_container)
        val pager = requireViewById<ComposeView>(R.id.tiles_pager)
        val button = requireViewById<ImageButton>(R.id.panel_edit_button)

        if (animate) {
            TransitionManager.beginDelayedTransition(
                container,
                Fade().apply { duration = 150L }
            )
        }

        val containerParams = container.layoutParams as ViewGroup.MarginLayoutParams
        if (enabled) {
            val scroll = requireViewById<ScrollView>(R.id.panel_scroll)
            val header = requireViewById<View>(R.id.panel_header)
            scroll.scrollTo(0, 0)

            containerBottomMargin = containerParams.bottomMargin
            val listParams = list.layoutParams as ViewGroup.MarginLayoutParams
            // As tall as the content was, counting the margin the list now takes over.
            val sameSize = normal.height + containerBottomMargin - listParams.topMargin
            val listTop = containerParams.topMargin + header.bottom + listParams.topMargin
            val room = scroll.height - listTop
            listParams.height = (if (room > 0) minOf(sameSize, room) else sameSize)
                .coerceAtLeast(MIN_EDIT_HEIGHT.dp)
            containerParams.bottomMargin = 0
            container.layoutParams = containerParams

            // Tiles that are not shown should not keep listening for updates.
            isTilePagerActive = false
            pager.disposeComposition()
            val editor = tileEditor
                ?: PanelTileEditor(list, appSettings).also { tileEditor = it }
            editor.open()
        } else {
            containerParams.bottomMargin = containerBottomMargin
            container.layoutParams = containerParams
            tileEditor?.close()
            if (rebuildTiles) populateTiles()
        }

        normal.visibility = if (enabled) View.GONE else View.VISIBLE
        list.visibility = if (enabled) View.VISIBLE else View.GONE
        button.setImageResource(if (enabled) R.drawable.ic_check else R.drawable.ic_edit_tiles)
        button.contentDescription = context.getString(
            if (enabled) R.string.panel_edit_done else R.string.panel_edit_enter
        )
    }

    private fun batteryTemperature() {
        val intent: Intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).toInt() / 10
        val degree = "\u2103"
        val batteryTemp: TextView = requireViewById(R.id.batteryTemp)
        batteryTemp.text = "$temp$degree"
    }

    private fun setupSliderIcons() {
        val brightnessIcon = requireViewById<ImageView>(R.id.brightness_icon)
        val volumeIcon = requireViewById<ImageView>(R.id.volume_icon)

        refreshBrightnessIcon(brightnessIcon)
        brightnessIcon.setOnClickListener {
            toggleAutoBrightness()
            refreshBrightnessIcon(brightnessIcon)
        }

        refreshVolumeIcon(volumeIcon)
        volumeIcon.setOnClickListener {
            cycleRingerMode()
            refreshVolumeIcon(volumeIcon)
        }
    }

    private fun toggleAutoBrightness() {
        val resolver = context.contentResolver
        val current = Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        val next = if (current == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
    }

    private fun refreshBrightnessIcon(icon: ImageView) {
        val isAuto = Settings.System.getInt(
            context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        icon.alpha = if (isAuto) 1f else 0.55f
    }

    private fun cycleRingerMode() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
    }

    private fun refreshVolumeIcon(icon: ImageView) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        icon.setImageResource(
            when (audioManager.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_off
                AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibration
                else -> R.drawable.ic_volume
            }
        )
    }

    /**
     * Lightweight [LifecycleOwner] and [SavedStateRegistryOwner] for hosting [ComposeView] inside
     * the [GameBarService] overlay window where no Activity host is present.
     */
    private class PanelLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onAttach() {
            savedStateController.performAttach()
            savedStateController.performRestore(Bundle.EMPTY)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDetach() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private companion object {
        /** The least height of the tile editor, in dp, for when the content is shorter. */
        const val MIN_EDIT_HEIGHT = 240
    }
}
