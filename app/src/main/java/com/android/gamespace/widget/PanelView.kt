package com.android.gamespace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.gamespace.R
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.dp
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.tiles.TileType
import com.android.gamespace.widget.tiles.TilePagerAdapter

class PanelView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    private var nowPlaying: NowPlayingController? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.panel_view, this, true)
        isClickable = true
        isFocusable = true
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
        super.onAttachedToWindow()
        updateTranslationY()
        batteryTemperature()
        populateTiles()
        setupControlsFlipper()
        setupSliderIcons()
        setupEditButton()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        nowPlaying?.stop()
        nowPlaying = null
        // The panel is reused, and attaching it builds its tiles again.
        setEditing(false, animate = false, rebuildTiles = false)
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
        val pager = requireViewById<ViewPager2>(R.id.tiles_pager)
        val pages = appSettings.selectedTiles.mapNotNull { TileType.fromId(it) }.chunked(TileLayoutModel.TILES_PER_PAGE)
        pager.adapter = TilePagerAdapter(context, pages)
    }

    private var editing = false
    private var tileEditor: PanelTileEditor? = null

    private fun setupEditButton() {
        requireViewById<ImageButton>(R.id.panel_edit_button).setOnClickListener {
            setEditing(!editing)
        }
    }

    /**
     * Swaps the panel's content for the tile editor, or back. The editor takes the height the
     * content had, so the panel keeps its size. Ending the edit shows the new tiles right away.
     */
    private fun setEditing(enabled: Boolean, animate: Boolean = true, rebuildTiles: Boolean = true) {
        if (editing == enabled) return
        editing = enabled

        val normal = requireViewById<View>(R.id.panel_normal_content)
        val list = requireViewById<RecyclerView>(R.id.panel_edit_list)
        val pager = requireViewById<ViewPager2>(R.id.tiles_pager)
        val button = requireViewById<ImageButton>(R.id.panel_edit_button)

        if (animate) {
            TransitionManager.beginDelayedTransition(
                requireViewById<ViewGroup>(R.id.panel_content_container),
                Fade().apply { duration = 150L }
            )
        }

        if (enabled) {
            list.layoutParams.height = maxOf(normal.height, MIN_EDIT_HEIGHT.dp)
            // Tiles that are not shown should not keep listening for updates.
            pager.adapter = null
            val editor = tileEditor
                ?: PanelTileEditor(list, appSettings).also { tileEditor = it }
            editor.open()
        } else {
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
        val batteryTemp:TextView = requireViewById(R.id.batteryTemp)
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

    private companion object {
        /** The least height of the tile editor, in dp, for when the content is shorter. */
        const val MIN_EDIT_HEIGHT = 240
    }
}
