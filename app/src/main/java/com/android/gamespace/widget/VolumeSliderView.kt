package com.android.gamespace.widget

import android.content.*
import android.media.AudioManager
import android.util.AttributeSet
import android.widget.SeekBar

class VolumeSliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SeekBar(context, attrs) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var dragging = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (!dragging) sync() }
    }

    init {
        max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        progressDrawable = GapSliderTrackDrawable(context)
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) am.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) { dragging = false }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
        context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    override fun onDetachedFromWindow() {
        context.unregisterReceiver(receiver)
        super.onDetachedFromWindow()
    }

    private fun sync() { progress = am.getStreamVolume(AudioManager.STREAM_MUSIC) }
}
