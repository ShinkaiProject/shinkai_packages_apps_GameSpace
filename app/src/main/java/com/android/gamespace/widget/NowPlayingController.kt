package com.android.gamespace.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.android.gamespace.R

class NowPlayingController(private val context: Context, private val root: View) {

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private var activeController: MediaController? = null
    private var isLiked = false
    private var isRepeatOn = false
    private var badgeIconTint: Int = Color.WHITE

    private val squigglyDrawable = SquigglyProgressDrawable()

    private val title: TextView = root.findViewById(R.id.media_title)
    private val artist: TextView = root.findViewById(R.id.media_artist)
    private val playPause: ImageView = root.findViewById(R.id.media_play_pause)
    private val prevBtn: ImageView = root.findViewById(R.id.media_prev)
    private val nextBtn: ImageView = root.findViewById(R.id.media_next)
    private val seekbar: SeekBar = root.findViewById(R.id.media_seekbar)
    private val sourceIcon: ImageView = root.findViewById(R.id.media_source_icon)
    private val artBackground: ImageView = root.findViewById(R.id.media_art_background)
    private val colorWash: View = root.findViewById(R.id.media_color_wash)
    private val likeBtn: ImageView = root.findViewById(R.id.media_like)
    private val repeatBtn: ImageView = root.findViewById(R.id.media_repeat)

    private val progressRunnable = object : Runnable {
        override fun run() {
            activeController?.let { controller ->
                val state = controller.playbackState
                val position = state?.position ?: 0L
                val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 1L
                
                seekbar.progress = position.toInt()
                
                squigglyDrawable.progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
                squigglyDrawable.animateWave = state?.state == PlaybackState.STATE_PLAYING
            }
            handler.postDelayed(this, 500L)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refreshMetadata(metadata)
        override fun onPlaybackStateChanged(state: PlaybackState?) = refreshPlaybackState(state)
        override fun onSessionDestroyed() = pickActiveSession()
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { pickActiveSession() }

    private fun findCustomAction(state: PlaybackState?, vararg keywords: String) =
        state?.customActions?.firstOrNull { action ->
            val name = action.action.lowercase()
            keywords.any { it in name }
        }

    fun start() {
        applyMonetColor()
        
        seekbar.progressDrawable = squigglyDrawable
        
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null) }
        pickActiveSession()
        handler.post(progressRunnable)

        playPause.setOnClickListener {
            val playing = activeController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) activeController?.transportControls?.pause()
            else activeController?.transportControls?.play()
        }
        nextBtn.setOnClickListener { activeController?.transportControls?.skipToNext() }
        prevBtn.setOnClickListener { activeController?.transportControls?.skipToPrevious() }
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    activeController?.transportControls?.seekTo(progress.toLong())
                    val duration = seekbar.max
                    if (duration > 0) {
                        squigglyDrawable.progress = progress.toFloat() / duration.toFloat()
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        likeBtn.setOnClickListener {
            val action = findCustomAction(activeController?.playbackState, "like", "favorite", "thumbs_up")
                ?: return@setOnClickListener
            isLiked = !isLiked
            likeBtn.setImageResource(if (isLiked) R.drawable.ic_media_like_filled else R.drawable.ic_media_like_border)
            activeController?.transportControls?.sendCustomAction(action.action, null)
        }
        repeatBtn.setOnClickListener {
            val action = findCustomAction(activeController?.playbackState, "repeat", "loop")
                ?: return@setOnClickListener
            isRepeatOn = !isRepeatOn
            refreshRepeatState(isRepeatOn)
            activeController?.transportControls?.sendCustomAction(action.action, null)
        }
    }

    fun stop() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        activeController?.unregisterCallback(controllerCallback)
        handler.removeCallbacks(progressRunnable)
    }

    private fun pickActiveSession() {
        activeController?.unregisterCallback(controllerCallback)
        val sessions = runCatching { sessionManager.getActiveSessions(null) }.getOrDefault(emptyList())
        val picked = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()

        activeController = picked
        isLiked = false
        isRepeatOn = false
        picked?.registerCallback(controllerCallback, handler)
        refreshMetadata(picked?.metadata)
        refreshPlaybackState(picked?.playbackState)
        refreshSourceIcon(picked)
        refreshRepeatState(false)
    }

    private fun refreshMetadata(metadata: MediaMetadata?) {
        title.text = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: context.getString(R.string.media_no_session)
        artist.text = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt().coerceAtLeast(0)
        seekbar.max = duration

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) artBackground.setImageBitmap(art) else artBackground.setImageDrawable(null)
    }

    private fun refreshPlaybackState(state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        playPause.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        
        val position = state?.position ?: 0L
        seekbar.progress = position.toInt()
        
        val duration = seekbar.max
        squigglyDrawable.progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
        squigglyDrawable.animateWave = playing

        likeBtn.visibility = if (findCustomAction(state, "like", "favorite", "thumbs_up") != null) View.VISIBLE else View.GONE
        repeatBtn.visibility = if (findCustomAction(state, "repeat", "loop") != null) View.VISIBLE else View.GONE
    }

    private fun refreshSourceIcon(controller: MediaController?) {
        if (controller == null) {
            sourceIcon.setImageDrawable(null)
            return
        }
        runCatching {
            val appIcon = context.packageManager.getApplicationIcon(controller.packageName)
            val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (appIcon as? AdaptiveIconDrawable)?.monochrome
            } else null

            if (monochrome != null) {
                val tinted = monochrome.mutate()
                tinted.setTint(badgeIconTint)
                sourceIcon.setImageDrawable(tinted)
            } else {
                sourceIcon.setImageDrawable(appIcon)
            }
        }
    }

    private fun refreshRepeatState(active: Boolean) {
        repeatBtn.alpha = if (active) 1f else 0.5f
    }

    private fun applyMonetColor() {
        val color = resolveMonetAccentColor()
        colorWash.setBackgroundColor(Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)))
        (playPause.background?.mutate() as? GradientDrawable)?.setColor(color)
        val iconColor = if (isColorLight(color)) Color.BLACK else Color.WHITE
        playPause.imageTintList = ColorStateList.valueOf(iconColor)

        badgeIconTint = resolveBadgeIconTint()
    }

    private fun resolveMonetAccentColor(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = context.resources.getIdentifier("system_accent1_500", "color", "android")
            if (resId != 0) {
                return runCatching { context.getColor(resId) }.getOrDefault(Color.parseColor("#E8710A"))
            }
        }
        return Color.parseColor("#E8710A")
    }

    private fun resolveBadgeIconTint(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = context.resources.getIdentifier("system_accent1_50", "color", "android")
            if (resId != 0) {
                return runCatching { context.getColor(resId) }.getOrDefault(Color.WHITE)
            }
        }
        return Color.WHITE
    }

    private fun isColorLight(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance > 0.5
    }
}
