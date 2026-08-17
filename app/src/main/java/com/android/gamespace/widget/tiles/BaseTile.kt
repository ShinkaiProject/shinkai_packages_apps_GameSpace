package com.android.gamespace.widget.tiles

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.android.gamespace.R
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf


abstract class BaseTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs), View.OnClickListener, View.OnLongClickListener {
    init {
        isClickable = true
        isFocusable = true
        prepareLayout()
    }

    val appSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    val systemSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().systemSettings() }

    val title: TextView?
        get() = findViewById(R.id.tile_title)

    val summary: TextView?
        get() = findViewById(R.id.tile_summary)

    val icon: ImageView?
        get() = findViewById(R.id.tile_icon)

    private fun prepareLayout() {
        LayoutInflater.from(context)
            .inflate(R.layout.panel_tile, this, true)
        setOnClickListener(this)
        setOnLongClickListener(this)
    }

    override fun onClick(v: View?) {
        isSelected = !isSelected
    }

    override fun onLongClick(v: View?): Boolean = false

    protected fun openSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
