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
import android.os.SystemClock
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
    private var started = false

    // What the controller last reported. Asking it for these is a call to the media session on
    // every time, and the callbacks below say when they change.
    private var playbackState: PlaybackState? = null

    // True while the thumb of the seekbar is held, so the update does not fight with the finger.
    private var seeking = false
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

    /**
     * Moves the seekbar along while something is playing. It works from the last reported state,
     * with no call to the media session, and it is only scheduled while playing: when paused, or
     * with nothing to play, the position does not change and there is nothing to do.
     */
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            if (started && playbackState?.state == PlaybackState.STATE_PLAYING) {
                handler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (started) refreshMetadata(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (started) refreshPlaybackState(state)
        }

        override fun onSessionDestroyed() {
            if (started) pickActiveSession()
        }
    }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { if (started) pickActiveSession() }

    /**
     * Where the playback is now. The state only has the position as of the moment the player
     * reported it, and players do not report again until something happens, so the time since
     * then is added while it plays (the same as MediaController's own progress does).
     */
    private fun currentPosition(): Long {
        val state = playbackState ?: return 0L
        var position = state.position
        if (position < 0L) return 0L
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        val duration = seekbar.max
        return if (duration > 0) position.coerceIn(0L, duration.toLong()) else 0L
    }

    private fun updateProgress() {
        if (seeking) return
        val position = currentPosition()
        seekbar.progress = position.toInt()
        val duration = seekbar.max
        squigglyDrawable.progress = if (duration > 0) position.toFloat() / duration.toFloat() else 0f
    }

    private fun findCustomAction(state: PlaybackState?, vararg keywords: String) =
        state?.customActions?.firstOrNull { action ->
            val name = action.action.lowercase()
            keywords.any { it in name }
        }

    fun start() {
        started = true
        applyMonetColor()
        
        seekbar.progressDrawable = squigglyDrawable
        
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null) }
        pickActiveSession()

        playPause.setOnClickListener {
            val playing = playbackState?.state == PlaybackState.STATE_PLAYING
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
            override fun onStartTrackingTouch(sb: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                seeking = false
            }
        })
        likeBtn.setOnClickListener {
            val action = findCustomAction(playbackState, "like", "favorite", "thumbs_up")
                ?: return@setOnClickListener
            isLiked = !isLiked
            likeBtn.setImageResource(if (isLiked) R.drawable.ic_media_like_filled else R.drawable.ic_media_like_border)
            activeController?.transportControls?.sendCustomAction(action.action, null)
        }
        repeatBtn.setOnClickListener {
            val action = findCustomAction(playbackState, "repeat", "loop")
                ?: return@setOnClickListener
            isRepeatOn = !isRepeatOn
            refreshRepeatState(isRepeatOn)
            activeController?.transportControls?.sendCustomAction(action.action, null)
        }
    }

    fun stop() {
        started = false
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        playbackState = null
        handler.removeCallbacks(progressRunnable)
        // The panel's views stay around while it is closed, and with them whatever they show:
        // the cover art is a bitmap that would be kept in memory for the whole game.
        artBackground.setImageDrawable(null)
        sourceIcon.setImageDrawable(null)
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
        updateProgress()

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) artBackground.setImageBitmap(art) else artBackground.setImageDrawable(null)
    }

    private fun refreshPlaybackState(state: PlaybackState?) {
        playbackState = state
        val playing = state?.state == PlaybackState.STATE_PLAYING
        playPause.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)

        updateProgress()
        squigglyDrawable.animateWave = playing
        handler.removeCallbacks(progressRunnable)
        if (playing) handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS)

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

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
