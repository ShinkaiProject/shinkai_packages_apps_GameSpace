package com.android.gamespace.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.android.gamespace.R
import com.android.gamespace.utils.TaskFpsMonitor
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.dp
import com.android.gamespace.utils.entryPointOf
import java.math.RoundingMode
import java.text.DecimalFormat

class MenuSwitcher @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        LayoutInflater.from(context).inflate(R.layout.bar_menu_switcher, this, true)
    }

    private val defaultTextColors: ColorStateList =
        requireViewById<TextView>(R.id.menu_content).textColors

    private val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }

    // Shared with the FPS recorder: the system reports FPS to one listener per task only.
    private val fpsMonitor by lazy { context.entryPointOf<ServiceViewEntryPoint>().taskFpsMonitor() }

    private val fpsListener = TaskFpsMonitor.Listener { fps ->
        if (isAttachedToWindow) {
            onFrameUpdated(fps)
        }
    }

    private val content: TextView?
        get() = findViewById(R.id.menu_content)

    var showFps = false
        set(value) {
            setMenuIcon(null)
            field = value
        }

    private var currentIcon: Int? = null

    /** Tints the collapsed bar red while an FPS recording is running. */
    var isRecording = false
        set(value) {
            if (field == value) return
            field = value
            setMenuIcon(currentIcon)
        }

    var isDragged = false
        set(value) {
            if (value && !showFps) setMenuIcon(R.drawable.ic_drag)
            field = value
        }

    fun updateIconState(isExpanded: Boolean, location: Int) {
        showFps = if (isExpanded) false else appSettings.showFps
        when {
            isExpanded -> R.drawable.ic_close
            location > 0 -> R.drawable.ic_arrow_right
            else -> R.drawable.ic_arrow_left
        }.let { setMenuIcon(it) }
        updateFrameRateBinding()
    }

    // One formatter for all the updates, which come in at least twice a second.
    private val fpsFormat = DecimalFormat("#").apply { roundingMode = RoundingMode.HALF_EVEN }
    private var shownFps: String? = null

    private fun onFrameUpdated(newValue: Float) {
        val text = fpsFormat.format(newValue)
        // The width of the text is not fixed, so setting even the same text again would measure
        // and lay out the bar again. FPS mostly stays on the same value for a while.
        if (text == shownFps) return
        shownFps = text
        content?.text = text
    }

    private fun updateFrameRateBinding() {
        if (showFps) {
            fpsMonitor.addListener(fpsListener)
        } else {
            fpsMonitor.removeListener(fpsListener)
        }
    }

    private fun setMenuIcon(icon: Int?) {
        currentIcon = icon
        when (icon) {
            R.drawable.ic_close, R.drawable.ic_drag -> layoutParams.width = 36.dp
            else -> layoutParams.width = LayoutParams.WRAP_CONTENT
        }
        requestLayout()
        val ic = icon?.takeIf { !showFps }?.let { resources.getDrawable(it, context.theme) }
        val recordingColor = context.getColor(R.color.recordingIndicator)
        if (isRecording && icon != R.drawable.ic_close) ic?.mutate()?.setTint(recordingColor)
        content?.setTextColor(
            if (isRecording) ColorStateList.valueOf(recordingColor) else defaultTextColors
        )
        content?.textScaleX = if (showFps) 1f else 0f
        content?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, ic, null, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFrameRateBinding()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        fpsMonitor.removeListener(fpsListener)
    }
}
