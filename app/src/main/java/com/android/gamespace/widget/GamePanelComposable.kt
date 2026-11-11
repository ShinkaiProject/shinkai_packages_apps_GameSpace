package com.android.gamespace.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.ui.components.DrawableImage
import com.android.gamespace.ui.components.ExpressiveGapSlider
import com.android.gamespace.utils.MemoryUsageMonitor
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.tiles.GameTileComposable
import com.android.gamespace.widget.tiles.TileCardSurface
import com.android.gamespace.widget.tiles.TileType
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Panel utama GameSpace yang dibangun 100% menggunakan Jetpack Compose Material 3.
 * Menggantikan PanelView, PanelTileEditor, NowPlayingController, BrightnessSliderView,
 * VolumeSliderView, QuickStartAppView, BatteryView, MemoryView, dan TextMemoryView.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamePanelComposable(
    appSettings: AppSettings,
    modifier: Modifier = Modifier
) {
    val opacity = (appSettings.menuOpacity / 100f).coerceIn(0.15f, 1f)
    var isEditing by remember { mutableStateOf(false) }
    var showMediaControls by remember { mutableStateOf(false) }
    var selectedTileIds by remember { mutableStateOf(appSettings.selectedTiles) }

    val arrowRotation by animateFloatAsState(
        targetValue = if (showMediaControls) 180f else 0f,
        animationSpec = tween(200),
        label = "ArrowRotation"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .width(280.dp)
            .alpha(opacity)
            .clickable(enabled = true, onClick = {}) // Mencegah klik tembus ke backdrop
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
        ) {
            // Header: Status Pill + Edit Button
            PanelStatusHeader(
                isEditing = isEditing,
                onToggleEdit = {
                    if (isEditing) {
                        selectedTileIds = appSettings.selectedTiles
                    }
                    isEditing = !isEditing
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = isEditing,
                label = "PanelModeSwitch"
            ) { editing ->
                if (editing) {
                    PanelTileEditorComposable(
                        appSettings = appSettings,
                        onTilesChanged = { selectedTileIds = it }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Controls Flipper (Sliders <-> Media Player) + Switch Arrow
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(
                                    targetState = showMediaControls,
                                    label = "ControlsSwitch"
                                ) { showMedia ->
                                    if (showMedia) {
                                        NowPlayingCardComposable()
                                    } else {
                                        PanelSlidersComposable()
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showMediaControls = !showMediaControls },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_chevron_right),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(arrowRotation)
                                )
                            }
                        }

                        // Quick Start Apps
                        QuickStartAppsBarComposable()

                        // Tiles Pager (4 tiles per page, 2x2 grid)
                        val pages = remember(selectedTileIds) {
                            selectedTileIds.mapNotNull { TileType.fromId(it) }
                                .chunked(TileLayoutModel.TILES_PER_PAGE)
                        }
                        if (pages.isNotEmpty()) {
                            val pagerState = rememberPagerState(pageCount = { pages.size })
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .padding(top = 6.dp)
                            ) { pageIndex ->
                                val pageTiles = pages[pageIndex]
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    pageTiles.chunked(2).forEach { rowTiles ->
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            repeat(2) { colIdx ->
                                                if (colIdx < rowTiles.size) {
                                                    GameTileComposable(
                                                        type = rowTiles[colIdx],
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // RAM Usage Indicator
                        MemoryUsageBarComposable()
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelStatusHeader(
    isEditing: Boolean,
    onToggleEdit: () -> Unit
) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(BatteryManager::class.java) }

    var currentTime by remember { mutableStateOf("") }
    var batteryTempC by remember { mutableIntStateOf(0) }
    var batteryPercent by remember {
        mutableIntStateOf(
            batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        )
    }

    fun updateClock() {
        val pattern = if (DateFormat.is24HourFormat(context)) "kk:mm" else "h:mm"
        currentTime = DateFormat.format(pattern, Date()).toString()
    }

    DisposableEffect(context) {
        updateClock()
        val initialBattery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        initialBattery?.let { intent ->
            batteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (scale > 0) batteryPercent = (level * 100) / scale
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        batteryTempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        if (scale > 0) batteryPercent = (level * 100) / scale
                    }
                    Intent.ACTION_TIME_TICK,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> updateClock()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    StatusPillItem(
                        iconRes = R.drawable.clock_time_two_outline,
                        text = currentTime
                    )
                    StatusPillItem(
                        iconRes = R.drawable.thermometer,
                        text = "$batteryTempC\u2103"
                    )
                    StatusPillItem(
                        iconRes = R.drawable.battery_outline,
                        text = stringResource(R.string.battery_format, batteryPercent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleEdit)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(if (isEditing) R.drawable.ic_check else R.drawable.ic_edit_tiles),
                    contentDescription = stringResource(
                        if (isEditing) R.string.panel_edit_done else R.string.panel_edit_enter
                    ),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusPillItem(
    iconRes: Int,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun PanelSlidersComposable() {
    val context = LocalContext.current
    val cr = context.contentResolver
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }

    // Brightness state (HLG 0..1000)
    fun readBrightnessGamma(): Float {
        val linearInt = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val linearFloat = (linearInt.coerceIn(1, 255) - 1) / 254f
        return convertLinearToGamma(linearFloat) * 1000f
    }

    fun readAutoBrightness(): Boolean {
        return Settings.System.getInt(
            cr,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    var brightnessGamma by remember { mutableFloatStateOf(readBrightnessGamma()) }
    var isAutoBrightness by remember { mutableStateOf(readAutoBrightness()) }

    // Volume state
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var ringerMode by remember { mutableIntStateOf(audioManager.ringerMode) }

    DisposableEffect(context) {
        val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                brightnessGamma = readBrightnessGamma()
                isAutoBrightness = readAutoBrightness()
            }
        }
        cr.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver
        )
        cr.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            brightnessObserver
        )

        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                ringerMode = audioManager.ringerMode
            }
        }
        val filter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        context.registerReceiver(volumeReceiver, filter)

        onDispose {
            runCatching { cr.unregisterContentObserver(brightnessObserver) }
            runCatching { context.unregisterReceiver(volumeReceiver) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Brightness Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        val nextMode = if (isAutoBrightness) {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        } else {
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        }
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, nextMode)
                        isAutoBrightness = !isAutoBrightness
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_brightness),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isAutoBrightness) 1f else 0.55f
                        ),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            ExpressiveGapSlider(
                value = brightnessGamma,
                valueRange = 0f..1000f,
                trackHeight = 14.dp,
                handleHeight = 32.dp,
                showEndStopDot = false,
                onValueChange = { newGamma ->
                    brightnessGamma = newGamma
                    val linearFloat = convertGammaToLinear(newGamma / 1000f)
                    val linearInt = (linearFloat * 254f + 1f).roundToInt().coerceIn(1, 255)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, linearInt)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Volume Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            val volumeIconRes = when (ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_off
                AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibration
                else -> R.drawable.ic_volume
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        val nextRinger = when (audioManager.ringerMode) {
                            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_SILENT
                            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE
                            else -> AudioManager.RINGER_MODE_NORMAL
                        }
                        audioManager.ringerMode = nextRinger
                        ringerMode = nextRinger
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(volumeIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            ExpressiveGapSlider(
                value = currentVolume,
                valueRange = 0f..maxVolume.toFloat(),
                trackHeight = 14.dp,
                handleHeight = 32.dp,
                showEndStopDot = false,
                onValueChange = { newVol ->
                    currentVolume = newVol
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        newVol.roundToInt().coerceIn(0, maxVolume),
                        0
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NowPlayingCardComposable() {
    val context = LocalContext.current
    val sessionManager = remember {
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var activeController by remember { mutableStateOf<MediaController?>(null) }
    var title by remember { mutableStateOf(context.getString(R.string.media_no_session)) }
    var artist by remember { mutableStateOf("") }
    var albumArt by remember { mutableStateOf<Bitmap?>(null) }
    var sourceIcon by remember { mutableStateOf<Drawable?>(null) }
    var playbackState by remember { mutableStateOf<PlaybackState?>(null) }
    var durationMs by remember { mutableIntStateOf(0) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var isLiked by remember { mutableStateOf(false) }
    var isRepeatOn by remember { mutableStateOf(false) }

    fun refreshMetadata(metadata: MediaMetadata?) {
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: context.getString(R.string.media_no_session)
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        durationMs = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).toInt().coerceAtLeast(0)
        albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    fun computeProgress(state: PlaybackState?, maxDuration: Int): Float {
        if (state == null || maxDuration <= 0) return 0f
        var pos = state.position
        if (pos < 0L) return 0f
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            pos += (elapsed * state.playbackSpeed).toLong()
        }
        return (pos.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
    }

    DisposableEffect(sessionManager) {
        var registeredController: MediaController? = null

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                refreshMetadata(metadata)
                progressFraction = computeProgress(playbackState, durationMs)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                playbackState = state
                progressFraction = computeProgress(state, durationMs)
            }
        }

        fun pickSession() {
            registeredController?.unregisterCallback(callback)
            val sessions = runCatching { sessionManager.getActiveSessions(null) }.getOrDefault(emptyList())
            val picked = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
            registeredController = picked
            activeController = picked
            isLiked = false
            isRepeatOn = false
            picked?.registerCallback(callback, mainHandler)
            refreshMetadata(picked?.metadata)
            playbackState = picked?.playbackState
            progressFraction = computeProgress(picked?.playbackState, durationMs)
            sourceIcon = picked?.let {
                runCatching { context.packageManager.getApplicationIcon(it.packageName) }.getOrNull()
            }
        }

        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { pickSession() }
        runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionListener, null) }
        pickSession()

        onDispose {
            runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionListener) }
            registeredController?.unregisterCallback(callback)
            albumArt = null
            sourceIcon = null
        }
    }

    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
    LaunchedEffect(isPlaying, playbackState, durationMs) {
        while (isPlaying && durationMs > 0) {
            progressFraction = computeProgress(playbackState, durationMs)
            delay(500L)
        }
    }

    fun findCustomAction(vararg keywords: String) =
        playbackState?.customActions?.firstOrNull { action ->
            val name = action.action.lowercase()
            keywords.any { it in name }
        }

    val likeAction = findCustomAction("like", "favorite", "thumbs_up")
    val repeatAction = findCustomAction("repeat", "loop")

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1A1A1A),
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp)
            .padding(vertical = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val artBitmap = remember(albumArt) { albumArt?.asImageBitmap() }
            if (artBitmap != null) {
                Image(
                    bitmap = artBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark Scrim + Monet Wash
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            )

            if (sourceIcon != null) {
                DrawableImage(
                    drawable = sourceIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 6.dp)
                        .size(20.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 10.dp, top = 26.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (artist.isNotEmpty()) {
                            Text(
                                text = artist,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                if (isPlaying) activeController?.transportControls?.pause()
                                else activeController?.transportControls?.play()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_media_prev),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { activeController?.transportControls?.skipToPrevious() }
                    )

                    SquigglyMediaSeekBar(
                        progress = progressFraction,
                        animateWave = isPlaying,
                        onSeek = { newFraction ->
                            progressFraction = newFraction
                            if (durationMs > 0) {
                                activeController?.transportControls?.seekTo((newFraction * durationMs).toLong())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(22.dp)
                    )

                    Icon(
                        painter = painterResource(R.drawable.ic_media_next),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { activeController?.transportControls?.skipToNext() }
                    )

                    if (likeAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(
                                if (isLiked) R.drawable.ic_media_like_filled else R.drawable.ic_media_like_border
                            ),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    isLiked = !isLiked
                                    activeController?.transportControls?.sendCustomAction(likeAction.action, null)
                                }
                        )
                    }

                    if (repeatAction != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_media_repeat),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = if (isRepeatOn) 1f else 0.5f),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    isRepeatOn = !isRepeatOn
                                    activeController?.transportControls?.sendCustomAction(repeatAction.action, null)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SquigglyMediaSeekBar(
    progress: Float,
    animateWave: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val infiniteTransition = rememberInfiniteTransition(label = "SquigglyWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val centerY = size.height / 2f
            val activeWidth = (w * progress.coerceIn(0f, 1f))

            if (activeWidth > 0f) {
                val path = Path()
                val waveLength = 64f
                val waveHeight = 4.5f
                val rampDistance = 32f
                path.moveTo(0f, centerY)
                var x = 0f
                while (x <= activeWidth) {
                    val startDamping = (x / rampDistance).coerceIn(0f, 1f)
                    val endDamping = ((activeWidth - x) / rampDistance).coerceIn(0f, 1f)
                    val amplitude = waveHeight * minOf(startDamping, endDamping)
                    val y = if (animateWave) {
                        centerY + sin(((x - phase) / waveLength) * 2.0 * Math.PI).toFloat() * amplitude
                    } else {
                        centerY
                    }
                    path.lineTo(x, y)
                    x += 2f
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )
            }

            if (activeWidth < w) {
                drawLine(
                    color = Color.White.copy(alpha = 0.3f),
                    start = Offset(activeWidth, centerY),
                    end = Offset(w, centerY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(activeWidth.coerceIn(0f, w), centerY)
            )
        }
    }
}

@Composable
private fun QuickStartAppsBarComposable() {
    val context = LocalContext.current
    val pm = context.packageManager

    fun loadPackages(): List<Pair<String, Drawable>> {
        val raw = Settings.System.getString(context.contentResolver, "quick_start_apps").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { pkg ->
                val icon = runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationIcon(info)
                }.getOrNull() ?: return@mapNotNull null
                pkg to icon
            }
    }

    var quickApps by remember { mutableStateOf(loadPackages()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                quickApps = loadPackages()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("quick_start_apps"),
            true,
            observer
        )
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    if (quickApps.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickApps, key = { it.first }) { (pkg, icon) ->
                DrawableImage(
                    drawable = icon,
                    contentDescription = pkg,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { launchAppInFreeformMode(context, pkg) }
                )
            }
        }
    }
}

private fun launchAppInFreeformMode(context: Context, packageName: String) {
    runCatching {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        val activityName = launchIntent.component?.className ?: return
        val freeformIntent = Intent("com.libremobileos.freeform.START_FREEFORM")
            .setPackage("com.libremobileos.freeform")
            .putExtra("packageName", packageName)
            .putExtra("activityName", activityName)
            .putExtra("userId", UserHandle.myUserId())
        context.sendBroadcast(freeformIntent)
    }
}

@Composable
private fun MemoryUsageBarComposable() {
    val context = LocalContext.current
    val monitor = remember { MemoryUsageMonitor.get(context) }
    var ramPercent by remember { mutableIntStateOf(0) }

    DisposableEffect(monitor) {
        val listener = MemoryUsageMonitor.Listener { percent ->
            ramPercent = percent
        }
        monitor.addListener(listener)
        onDispose { monitor.removeListener(listener) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.memory),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${stringResource(R.string.ram_usage)} $ramPercent%",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { (ramPercent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .width(56.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    }
}

@Composable
private fun PanelTileEditorComposable(
    appSettings: AppSettings,
    onTilesChanged: (List<String>) -> Unit
) {
    val model = remember {
        TileLayoutModel(
            allIds = TileType.entries.map { it.id },
            initialActive = appSettings.selectedTiles,
            defaultIds = AppSettings.DEFAULT_TILES.split(",")
        )
    }
    val activeTiles = remember {
        mutableStateListOf<TileType>().apply {
            addAll(model.active.mapNotNull { TileType.fromId(it) })
        }
    }
    val availableTiles = remember {
        mutableStateListOf<TileType>().apply {
            addAll(model.available.mapNotNull { TileType.fromId(it) })
        }
    }

    fun syncAndPersist() {
        appSettings.selectedTiles = model.active
        onTilesChanged(model.active)
        activeTiles.clear()
        activeTiles.addAll(model.active.mapNotNull { TileType.fromId(it) })
        availableTiles.clear()
        availableTiles.addAll(model.available.mapNotNull { TileType.fromId(it) })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        item(key = "reset_btn") {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = {
                        if (model.reset()) syncAndPersist()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.tile_layout_reset),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item(key = "active_label") {
            Text(
                text = stringResource(R.string.panel_edit_active),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }

        if (activeTiles.isEmpty()) {
            item(key = "active_empty") {
                Text(
                    text = stringResource(R.string.tile_layout_empty_active),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        } else {
            val activeRows = activeTiles.chunked(2)
            items(activeRows.size, key = { "active_row_$it" }) { rowIdx ->
                val pair = activeRows[rowIdx]
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(2) { colIdx ->
                        if (colIdx < pair.size) {
                            val tile = pair[colIdx]
                            TileCardSurface(
                                title = stringResource(tile.titleRes),
                                summary = "#${rowIdx * 2 + colIdx + 1}",
                                iconRes = tile.iconRes,
                                selected = true,
                                onClick = {
                                    if (model.remove(tile.id)) syncAndPersist()
                                },
                                onLongClick = {
                                    if (model.moveBy(tile.id, -1)) syncAndPersist()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item(key = "available_label") {
            Text(
                text = stringResource(R.string.panel_edit_available),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)
            )
        }

        if (availableTiles.isEmpty()) {
            item(key = "available_empty") {
                Text(
                    text = stringResource(R.string.tile_layout_empty_available),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                )
            }
        } else {
            val availableRows = availableTiles.chunked(2)
            items(availableRows.size, key = { "avail_row_$it" }) { rowIdx ->
                val pair = availableRows[rowIdx]
                Row(modifier = Modifier.fillMaxWidth()) {
                    repeat(2) { colIdx ->
                        if (colIdx < pair.size) {
                            val tile = pair[colIdx]
                            TileCardSurface(
                                title = stringResource(tile.titleRes),
                                summary = "+",
                                iconRes = tile.iconRes,
                                selected = false,
                                onClick = {
                                    if (model.add(tile.id)) syncAndPersist()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item(key = "edit_hint") {
            Text(
                text = stringResource(R.string.panel_edit_hint),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}

// Hybrid Log Gamma (HLG) constants per AOSP Display BrightnessUtils
private const val HLG_R = 0.5f
private const val HLG_A = 0.17883277f
private const val HLG_B = 0.28466892f
private const val HLG_C = 0.55991073f

private fun convertGammaToLinear(gamma: Float): Float {
    val ret = if (gamma <= HLG_R) {
        (gamma / HLG_R) * (gamma / HLG_R)
    } else {
        exp(((gamma - HLG_C) / HLG_A).toDouble()).toFloat() + HLG_B
    }
    return (ret.coerceIn(0f, 12f)) / 12f
}

private fun convertLinearToGamma(linear: Float): Float {
    val norm = linear * 12f
    return if (norm <= 1f) {
        sqrt(norm.toDouble()).toFloat() * HLG_R
    } else {
        HLG_A * ln((norm - HLG_B).toDouble()).toFloat() + HLG_C
    }.coerceIn(0f, 1f)
}
