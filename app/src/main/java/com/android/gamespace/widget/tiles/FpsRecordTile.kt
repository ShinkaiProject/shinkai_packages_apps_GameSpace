package com.android.gamespace.widget.tiles

import android.content.Context
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R
import com.android.gamespace.utils.FpsRecorder
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

/**
 * Starts and stops FPS recording. The recorder itself outlives the panel, so the tile only
 * mirrors its state while attached.
 */
class FpsRecordTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val recorder by lazy { context.entryPointOf<ServiceViewEntryPoint>().fpsRecorder() }

    /** The whole second the summary currently shows, so it is only redrawn when that changes. */
    private var shownSecond = -1L

    private val listener = object : FpsRecorder.Listener {
        override fun onStateChanged(recording: Boolean) = render()

        // Samples arrive twice a second but the timer only counts whole seconds.
        override fun onSample(elapsedMs: Long, fps: Float) {
            if (elapsedMs / 1000 != shownSecond) render()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.fps_record_title)
        icon?.setImageResource(R.drawable.ic_fps_record)
        recorder.addListener(listener, wantsSamples = true)
        render()
    }

    override fun onDetachedFromWindow() {
        recorder.removeListener(listener)
        super.onDetachedFromWindow()
    }

    private fun render() {
        val recording = recorder.isRecording
        isSelected = recording
        val seconds = recorder.elapsedMs / 1000
        shownSecond = if (recording) seconds else -1L
        summary?.text = if (recording) {
            context.getString(
                R.string.fps_record_summary_recording,
                DateUtils.formatElapsedTime(seconds)
            )
        } else {
            context.getString(R.string.fps_record_summary_idle)
        }
    }

    override fun onClick(v: View?) {
        if (recorder.isRecording) recorder.stop() else recorder.start()
        render()
    }
}
