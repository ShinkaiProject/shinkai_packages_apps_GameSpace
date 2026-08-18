package com.android.gamespace.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.ViewFlipper
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import androidx.core.view.doOnLayout
import androidx.viewpager2.widget.ViewPager2
import com.android.gamespace.R
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
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        nowPlaying?.stop()
        nowPlaying = null
    }

    private fun setupControlsFlipper() {
        val flipper = requireViewById<ViewFlipper>(R.id.controls_flipper)
        val arrow = requireViewById<ImageView>(R.id.controls_nav_arrow)
        arrow.setOnClickListener {
            flipper.showNext()
            arrow.animate().rotationBy(180f).setDuration(200L).start()
        }
        nowPlaying = NowPlayingController(context, flipper).also { it.start() }
    }

    private fun populateTiles() {
        val pager = requireViewById<ViewPager2>(R.id.tiles_pager)
        val pages = appSettings.selectedTiles.mapNotNull { TileType.fromId(it) }.chunked(4)
        pager.adapter = TilePagerAdapter(context, pages)
    }

    private fun batteryTemperature() {
        val intent: Intent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0).toInt() / 10
        val degree = "\u2103"
        val batteryTemp:TextView = requireViewById(R.id.batteryTemp)
        batteryTemp.text = "$temp$degree"
    }
}
