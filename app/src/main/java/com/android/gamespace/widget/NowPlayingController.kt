package com.android.gamespace.widget

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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

    private val title: TextView = root.findViewById(R.id.media_title)
    private val artist: TextView = root.findViewById(R.id.media_artist)
    private val playPause: ImageView = root.findViewById(R.id.media_play_pause)
    private val prevBtn: ImageView = root.findViewById(R.id.media_prev)
    private val nextBtn: ImageView = root.findViewById(R.id.media_next)
    private val seekbar: SeekBar = root.findViewById(R.id.media_seekbar)
    private val sourceIcon: ImageView = root.findViewById(R.id.media_source_icon)
    private val artBackground: ImageView = root.findViewById(R.id.media_art_background)
    private val likeBtn: ImageView = root.findViewById(R.id.media_like)
    private val repeatBtn: ImageView = root.findViewById(R.id.media_repeat)

    private val progressRunnable = object : Runnable {
        override fun run() {
            activeController?.playbackState?.let { seekbar.progress = it.position.toInt() }
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
                if (fromUser) activeController?.transportControls?.seekTo(progress.toLong())
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
        seekbar.max = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt().coerceAtLeast(0)

        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (art != null) artBackground.setImageBitmap(art) else artBackground.setImageDrawable(null)
    }

    private fun refreshPlaybackState(state: PlaybackState?) {
        val playing = state?.state == PlaybackState.STATE_PLAYING
        playPause.setImageResource(if (playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        seekbar.progress = (state?.position ?: 0L).toInt()

        likeBtn.visibility = if (findCustomAction(state, "like", "favorite", "thumbs_up") != null) View.VISIBLE else View.GONE
        repeatBtn.visibility = if (findCustomAction(state, "repeat", "loop") != null) View.VISIBLE else View.GONE
    }

    private fun refreshSourceIcon(controller: MediaController?) {
        if (controller == null) {
            sourceIcon.setImageDrawable(null)
            return
        }
        runCatching {
            sourceIcon.setImageDrawable(context.packageManager.getApplicationIcon(controller.packageName))
        }
    }

    private fun refreshRepeatState(active: Boolean) {
        repeatBtn.alpha = if (active) 1f else 0.5f
    }
}
