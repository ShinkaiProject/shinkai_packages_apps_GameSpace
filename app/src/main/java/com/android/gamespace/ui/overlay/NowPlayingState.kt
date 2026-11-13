package com.android.gamespace.ui.overlay

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt

/** The icon of the app that plays. A monochrome one is meant to be tinted, a colour one is not. */
@Stable
class SourceIcon(val image: ImageBitmap, val monochrome: Boolean)

/**
 * What the media card shows, and what its buttons do: the session that is playing (or else the
 * first there is), as Compose state. It listens to the sessions between [start] and [stop].
 */
@Stable
class NowPlayingState(private val context: Context) {

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private var activeController: MediaController? = null
    private var started = false

    // What the controller last reported. Asking it for these is a call to the media session every
    // time, and the callbacks below say when they change.
    private var playbackState: PlaybackState? = null

    // True while the seekbar is held, so the update does not fight with the finger.
    private var seeking = false

    /** The title, or null when there is no session. */
    var title by mutableStateOf<String?>(null)
        private set
    var artist by mutableStateOf("")
        private set
    var art by mutableStateOf<ImageBitmap?>(null)
        private set
    var sourceIcon by mutableStateOf<SourceIcon?>(null)
        private set
    var playing by mutableStateOf(false)
        private set

    /** In milliseconds. Zero when it is not known, and then there is nothing to seek in. */
    var duration by mutableStateOf(0L)
        private set
    var position by mutableStateOf(0L)
        private set
    var canLike by mutableStateOf(false)
        private set
    var canRepeat by mutableStateOf(false)
        private set
    var liked by mutableStateOf(false)
        private set
    var repeatOn by mutableStateOf(false)
        private set

    val progress: Float
        get() = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

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
        return if (duration > 0L) position.coerceIn(0L, duration) else 0L
    }

    private fun updateProgress() {
        if (seeking) return
        position = currentPosition()
    }

    private fun findCustomAction(state: PlaybackState?, vararg keywords: String) =
        state?.customActions?.firstOrNull { action ->
            val name = action.action.lowercase()
            keywords.any { it in name }
        }

    fun start() {
        started = true
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null) }
        pickActiveSession()
    }

    fun stop() {
        started = false
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        playbackState = null
        handler.removeCallbacks(progressRunnable)
        // The cover art is a bitmap that would otherwise be kept in memory for as long as this is.
        art = null
        sourceIcon = null
    }

    fun togglePlayPause() {
        val controls = activeController?.transportControls ?: return
        if (playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
    }

    fun next() {
        activeController?.transportControls?.skipToNext()
    }

    fun previous() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun beginSeek() {
        seeking = true
    }

    /** Seeks to [fraction] (0..1) of the duration, and shows it at once. */
    fun seekTo(fraction: Float) {
        if (duration <= 0L) return
        val target = (fraction.coerceIn(0f, 1f) * duration).toLong()
        position = target
        activeController?.transportControls?.seekTo(target)
    }

    fun endSeek() {
        seeking = false
    }

    fun toggleLike() {
        val action = findCustomAction(playbackState, "like", "favorite", "thumbs_up") ?: return
        liked = !liked
        activeController?.transportControls?.sendCustomAction(action.action, null)
    }

    fun toggleRepeat() {
        val action = findCustomAction(playbackState, "repeat", "loop") ?: return
        repeatOn = !repeatOn
        activeController?.transportControls?.sendCustomAction(action.action, null)
    }

    private fun pickActiveSession() {
        activeController?.unregisterCallback(controllerCallback)
        val sessions = runCatching { sessionManager.getActiveSessions(null) }.getOrDefault(emptyList())
        val picked = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()

        activeController = picked
        liked = false
        repeatOn = false
        picked?.registerCallback(controllerCallback, handler)
        refreshMetadata(picked?.metadata)
        refreshPlaybackState(picked?.playbackState)
        refreshSourceIcon(picked)
    }

    private fun refreshMetadata(metadata: MediaMetadata?) {
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        updateProgress()

        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        art = bitmap?.asImageBitmap()
    }

    private fun refreshPlaybackState(state: PlaybackState?) {
        playbackState = state
        playing = state?.state == PlaybackState.STATE_PLAYING

        updateProgress()
        handler.removeCallbacks(progressRunnable)
        if (playing) handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS)

        canLike = findCustomAction(state, "like", "favorite", "thumbs_up") != null
        canRepeat = findCustomAction(state, "repeat", "loop") != null
    }

    private fun refreshSourceIcon(controller: MediaController?) {
        if (controller == null) {
            sourceIcon = null
            return
        }
        sourceIcon = runCatching {
            val size = (SOURCE_ICON_DP * context.resources.displayMetrics.density).roundToInt()
            val appIcon = context.packageManager.getApplicationIcon(controller.packageName)
            val monochrome = (appIcon as? AdaptiveIconDrawable)?.monochrome
            if (monochrome != null) {
                SourceIcon(monochrome.mutate().toBitmap(size, size).asImageBitmap(), monochrome = true)
            } else {
                SourceIcon(appIcon.toBitmap(size, size).asImageBitmap(), monochrome = false)
            }
        }.getOrNull()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val SOURCE_ICON_DP = 30
    }
}

/** A [NowPlayingState] that listens to the sessions for as long as this is on the screen. */
@Composable
fun rememberNowPlaying(): NowPlayingState {
    val context = LocalContext.current
    val state = remember { NowPlayingState(context) }
    DisposableEffect(state) {
        state.start()
        onDispose { state.stop() }
    }
    return state
}
