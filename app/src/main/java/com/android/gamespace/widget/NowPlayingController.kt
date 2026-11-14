package com.android.gamespace.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.android.gamespace.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlayingState(
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val artwork: Bitmap? = null,
    val sourceIcon: Drawable? = null,
    val likeVisible: Boolean = false,
    val repeatVisible: Boolean = false,
    val isLiked: Boolean = false,
    val isRepeatOn: Boolean = false,
    val accentColor: Int = 0xFFE8710A.toInt(),
    val onAccentColor: Int = Color.WHITE,
    val colorWash: Int = Color.argb(60, 232, 113, 10),
)

class NowPlayingController(private val context: Context) {

    private val sessionManager by lazy {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }

    private val handler = Handler(Looper.getMainLooper())
    private var activeController: MediaController? = null
    private var started = false

    private val mutableState = MutableStateFlow(
        NowPlayingState(title = context.getString(R.string.media_no_session))
    )
    val state: StateFlow<NowPlayingState> = mutableState.asStateFlow()

    private var playbackState: PlaybackState? = null
    private var seeking = false
    private var badgeIconTint: Int = Color.WHITE

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

    fun start() {
        if (started) return
        started = true
        applyMonetColor()
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, null)
        }
        pickActiveSession()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching {
            sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        }
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        playbackState = null
        handler.removeCallbacks(progressRunnable)
        mutableState.value = mutableState.value.copy(
            hasSession = false,
            artwork = null,
            sourceIcon = null,
            likeVisible = false,
            repeatVisible = false,
        )
    }

    fun togglePlayPause() {
        val playing = playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) activeController?.transportControls?.pause()
        else activeController?.transportControls?.play()
    }

    fun skipToNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }

    fun beginSeek() {
        seeking = true
    }

    fun endSeek() {
        seeking = false
        updateProgress()
    }

    fun seekTo(positionMs: Long) {
        val duration = mutableState.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        activeController?.transportControls?.seekTo(target)
        mutableState.value = mutableState.value.copy(positionMs = target)
    }

    fun toggleLike() {
        val action = findCustomAction(playbackState, "like", "favorite", "thumbs_up") ?: return
        val liked = !mutableState.value.isLiked
        mutableState.value = mutableState.value.copy(isLiked = liked)
        activeController?.transportControls?.sendCustomAction(action.action, null)
    }

    fun toggleRepeat() {
        val action = findCustomAction(playbackState, "repeat", "loop") ?: return
        val repeat = !mutableState.value.isRepeatOn
        mutableState.value = mutableState.value.copy(isRepeatOn = repeat)
        activeController?.transportControls?.sendCustomAction(action.action, null)
    }

    private fun findCustomAction(state: PlaybackState?, vararg keywords: String) =
        state?.customActions?.firstOrNull { action ->
            val name = action.action.lowercase()
            keywords.any { it in name }
        }

    private fun currentPosition(): Long {
        val state = playbackState ?: return 0L
        var position = state.position
        if (position < 0L) return 0L
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            position += (elapsed * state.playbackSpeed).toLong()
        }
        val duration = mutableState.value.durationMs
        return if (duration > 0) position.coerceIn(0L, duration) else 0L
    }

    private fun updateProgress() {
        if (seeking) return
        mutableState.value = mutableState.value.copy(positionMs = currentPosition())
    }

    private fun pickActiveSession() {
        activeController?.unregisterCallback(controllerCallback)
        val sessions = runCatching { sessionManager.getActiveSessions(null) }
            .getOrDefault(emptyList())
        val picked = sessions.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: sessions.firstOrNull()

        activeController = picked
        picked?.registerCallback(controllerCallback, handler)
        refreshMetadata(picked?.metadata)
        refreshPlaybackState(picked?.playbackState)
        refreshSourceIcon(picked)
        mutableState.value = mutableState.value.copy(isLiked = false)
        refreshRepeatState(false)
    }

    private fun refreshMetadata(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: context.getString(R.string.media_no_session)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
            .coerceAtLeast(0L)
        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        mutableState.value = mutableState.value.copy(
            hasSession = activeController != null,
            title = title,
            artist = artist,
            durationMs = duration,
            artwork = art,
        )
        updateProgress()
    }

    private fun refreshPlaybackState(state: PlaybackState?) {
        playbackState = state
        val playing = state?.state == PlaybackState.STATE_PLAYING
        val likeAction = findCustomAction(state, "like", "favorite", "thumbs_up")
        val repeatAction = findCustomAction(state, "repeat", "loop")
        mutableState.value = mutableState.value.copy(
            isPlaying = playing,
            likeVisible = likeAction != null,
            repeatVisible = repeatAction != null,
        )
        updateProgress()
        handler.removeCallbacks(progressRunnable)
        if (playing) handler.postDelayed(progressRunnable, PROGRESS_INTERVAL_MS)
    }

    private fun refreshSourceIcon(controller: MediaController?) {
        if (controller == null) {
            mutableState.value = mutableState.value.copy(sourceIcon = null)
            return
        }
        runCatching {
            val appIcon = context.packageManager.getApplicationIcon(controller.packageName)
            val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (appIcon as? AdaptiveIconDrawable)?.monochrome
            } else {
                null
            }
            val icon = if (monochrome != null) {
                monochrome.mutate().apply { setTint(badgeIconTint) }
            } else {
                appIcon
            }
            mutableState.value = mutableState.value.copy(sourceIcon = icon)
        }
    }

    private fun refreshRepeatState(active: Boolean) {
        mutableState.value = mutableState.value.copy(isRepeatOn = active)
    }

    private fun applyMonetColor() {
        val color = resolveMonetAccentColor()
        val onColor = if (isColorLight(color)) Color.BLACK else Color.WHITE
        badgeIconTint = resolveBadgeIconTint()
        mutableState.value = mutableState.value.copy(
            accentColor = color,
            onAccentColor = onColor,
            colorWash = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color)),
        )
    }

    private fun resolveMonetAccentColor(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = context.resources.getIdentifier(
                "system_accent1_500", "color", "android",
            )
            if (resId != 0) {
                return runCatching { context.getColor(resId) }
                    .getOrDefault(Color.parseColor("#E8710A"))
            }
        }
        return Color.parseColor("#E8710A")
    }

    private fun resolveBadgeIconTint(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val resId = context.resources.getIdentifier(
                "system_accent1_50", "color", "android",
            )
            if (resId != 0) {
                return runCatching { context.getColor(resId) }.getOrDefault(Color.WHITE)
            }
        }
        return Color.WHITE
    }

    private fun isColorLight(color: Int): Boolean {
        val luminance =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return luminance > 0.5
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
