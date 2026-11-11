/*
 * Copyright (C) 2021 Chaldeaprjkt
 *               2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.gamespace.gamebar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.settings.SettingsActivity
import com.android.gamespace.ui.components.OverlayLifecycleOwner
import com.android.gamespace.ui.components.createOverlayComposeView
import com.android.gamespace.ui.theme.GameSpaceTheme
import com.android.gamespace.ui.theme.RecordingRed
import com.android.gamespace.utils.FpsRecorder
import com.android.gamespace.utils.ScreenUtils
import com.android.gamespace.utils.TaskFpsMonitor
import com.android.gamespace.utils.dp
import com.android.gamespace.utils.statusbarHeight
import com.android.gamespace.widget.GamePanelComposable
import com.android.systemui.screenrecord.IRecordingCallback
import dagger.hilt.android.AndroidEntryPoint
import java.math.RoundingMode
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint(Service::class)
class GameBarService : Hilt_GameBarService() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var danmakuService: DanmakuService
    @Inject lateinit var fpsRecorder: FpsRecorder
    @Inject lateinit var taskFpsMonitor: TaskFpsMonitor

    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private var halfWidth = 0
    private var safeHeight = 0
    private var safeArea = 0

    private val barLayoutParam = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        preferMinimalPostProcessing = true
        gravity = Gravity.TOP
    }

    private val panelLayoutParam = WindowManager.LayoutParams(
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        gravity = Gravity.CENTER_VERTICAL
    }

    private val overlayLifecycle = OverlayLifecycleOwner()
    private lateinit var rootBarView: ComposeView
    private var rootPanelView: ComposeView? = null

    private var barExpandedState by mutableStateOf(false)
    private var showPanelState by mutableStateOf(false)
    private var isDraggingState by mutableStateOf(false)
    private var barXState by mutableIntStateOf(0)
    private var isFpsRecordingState by mutableStateOf(false)
    private var isScreenRecordingState by mutableStateOf(false)

    private val binder = GameBarBinder()
    private val autoCollapseRunnable = Runnable { setBarExpanded(false) }
    private val firstPaint = Runnable { initActions() }
    private var shouldClose = false
    private var isGameStarting = false

    private val recordingListener = object : FpsRecorder.Listener {
        override fun onStateChanged(recording: Boolean) {
            isFpsRecordingState = recording
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayLifecycle.onCreate()
        overlayLifecycle.onResume()
        updateScreenMetrics()
        danmakuService.init()
        isFpsRecordingState = fpsRecorder.isRecording
        fpsRecorder.addListener(recordingListener)

        rootBarView = createOverlayComposeView(this, overlayLifecycle) {
            GameSpaceTheme {
                GameBarOverlayComposable(
                    appSettings = appSettings,
                    taskFpsMonitor = taskFpsMonitor,
                    expanded = barExpandedState,
                    isDragging = isDraggingState,
                    barX = barXState,
                    isFpsRecording = isFpsRecordingState,
                    isScreenRecording = isScreenRecordingState,
                    hasScreenRecorder = screenUtils.recorder != null,
                    onToggleExpand = { setBarExpanded(!barExpandedState) },
                    onDragDelta = { dx, dy ->
                        if (!isDraggingState) isDraggingState = true
                        updateLayout {
                            x = (x + dx).roundToInt()
                            y = (y + dy).roundToInt().coerceIn(safeArea, safeHeight)
                        }
                        barXState = barLayoutParam.x
                        if (barExpandedState) scheduleAutoCollapse()
                    },
                    onDragEnd = {
                        isDraggingState = false
                        dockCollapsedMenu()
                        appSettings.x = barLayoutParam.x
                        appSettings.y = barLayoutParam.y
                    },
                    onTogglePanel = { setShowPanel(!showPanelState) },
                    onOpenSettings = {
                        startActivity(
                            Intent(this@GameBarService, SettingsActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onOpenSidebar = { openSidebar() },
                    onTakeScreenshot = { takeShot() },
                    onToggleScreenRecord = { toggleScreenRecorder() }
                )
            }
        }
    }

    private fun setBarExpanded(value: Boolean) {
        barExpandedState = value
        if (value) scheduleAutoCollapse() else handler.removeCallbacks(autoCollapseRunnable)
    }

    private fun setShowPanel(value: Boolean) {
        showPanelState = value
        if (value) {
            handler.removeCallbacks(autoCollapseRunnable)
            val panel = rootPanelView ?: createOverlayComposeView(this, overlayLifecycle) {
                GameSpaceTheme {
                    Box(
                        contentAlignment = if (barXState < 0) Alignment.CenterStart else Alignment.CenterEnd,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                setShowPanel(false)
                            }
                            .padding(
                                start = if (barXState < 0) 56.dp else 16.dp,
                                end = if (barXState >= 0) 56.dp else 16.dp,
                                top = 16.dp,
                                bottom = 16.dp
                            )
                    ) {
                        GamePanelComposable(appSettings = appSettings)
                    }
                }
            }.also { rootPanelView = it }

            if (!panel.isAttachedToWindow) {
                wm.addView(panel, panelLayoutParam)
            }
        } else {
            rootPanelView?.let { panel ->
                if (panel.isAttachedToWindow) {
                    runCatching { wm.removeView(panel) }
                }
            }
            if (barExpandedState) scheduleAutoCollapse()
        }
    }

    private fun updateScreenMetrics() {
        val bounds = wm.maximumWindowMetrics.bounds
        halfWidth = bounds.width() / 2
        safeArea = statusbarHeight + 4.dp
        safeHeight = bounds.height() - safeArea
    }

    private fun startForegroundService() {
        val channelId = "gamespace_service_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            channelId,
            "Game Space Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.gamespace_running))
            .setSmallIcon(R.drawable.ic_gear)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setSilent(true)
            .build()

        startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> onGameLeave()
            ACTION_START -> onGameStart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent) = binder

    inner class GameBarBinder : Binder() {
        fun getService() = this@GameBarService
    }

    override fun onDestroy() {
        onGameLeave()
        fpsRecorder.removeListener(recordingListener)
        danmakuService.destroy()
        overlayLifecycle.onDestroy()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        dockCollapsedMenu()
        danmakuService.updateConfiguration(newConfig)
    }

    fun onGameStart() {
        if (isGameStarting) return
        isGameStarting = true

        handler.post {
            if (!::rootBarView.isInitialized) return@post
            if (rootBarView.isAttachedToWindow) {
                isGameStarting = false
                return@post
            }

            shouldClose = false
            rootBarView.visibility = View.INVISIBLE
            wm.addView(rootBarView, barLayoutParam)
            handler.postDelayed(firstPaint, 500)
            startForegroundService()
            isGameStarting = false
        }
    }

    fun onGameLeave() {
        fpsRecorder.stop(blocking = true)
        shouldClose = true
        handler.removeCallbacksAndMessages(null)

        runCatching {
            rootPanelView?.takeIf { it.isAttachedToWindow }?.let { wm.removeViewImmediate(it) }
        }
        runCatching {
            if (::rootBarView.isInitialized && rootBarView.isAttachedToWindow) {
                wm.removeViewImmediate(rootBarView)
            }
        }

        stopForeground(true)
        stopSelf()
    }

    private fun updateLayout(update: WindowManager.LayoutParams.() -> Unit = {}) {
        barLayoutParam.update()
        if (rootBarView.isAttachedToWindow) {
            wm.updateViewLayout(rootBarView, barLayoutParam)
        }
    }

    private fun initActions() {
        if (shouldClose) return
        rootBarView.visibility = View.VISIBLE
        setBarExpanded(false)
        barLayoutParam.x = appSettings.x
        barLayoutParam.y = appSettings.y
        dockCollapsedMenu()
        setupRecorderCallback()
    }

    private fun dockCollapsedMenu() {
        barLayoutParam.x = if (barLayoutParam.x < 0) -halfWidth else halfWidth
        barLayoutParam.y = barLayoutParam.y.coerceIn(safeArea, safeHeight)
        barXState = barLayoutParam.x
        if (::rootBarView.isInitialized && rootBarView.isAttachedToWindow) {
            wm.updateViewLayout(rootBarView, barLayoutParam)
        }
    }

    private fun scheduleAutoCollapse() {
        handler.removeCallbacks(autoCollapseRunnable)
        if (!appSettings.barAutoHideEnabled || showPanelState) return
        handler.postDelayed(autoCollapseRunnable, appSettings.barAutoHideDelaySec * 1000L)
    }

    private fun openSidebar() {
        val intent = Intent("com.android.gamespace.action.SHOW_SIDEBAR").apply {
            setPackage("com.libremobileos.sidebar")
        }
        sendBroadcastAsUser(intent, UserHandle.CURRENT, android.Manifest.permission.MANAGE_GAME_MODE)
        setBarExpanded(false)
    }

    private fun takeShot() {
        val afterShot = {
            setBarExpanded(false)
            handler.postDelayed({ updateLayout { alpha = 1f } }, 100)
        }
        updateLayout { alpha = 0f }
        handler.postDelayed({
            runCatching {
                screenUtils.takeScreenshot { afterShot() }
            }.onFailure {
                it.printStackTrace()
                afterShot()
            }
        }, 250)
    }

    private fun setupRecorderCallback() {
        val recorder = screenUtils.recorder ?: return
        runCatching {
            recorder.addRecordingCallback(object : IRecordingCallback.Stub() {
                override fun onRecordingStart() {
                    handler.post { isScreenRecordingState = true }
                }

                override fun onRecordingEnd() {
                    handler.post { isScreenRecordingState = false }
                }
            })
        }
    }

    private fun toggleScreenRecorder() {
        val recorder = screenUtils.recorder ?: return
        if (recorder.isStarting) return
        if (!recorder.isRecording) recorder.startRecording() else recorder.stopRecording()
        setBarExpanded(false)
    }

    companion object {
        const val TAG = "GameBar"
        const val ACTION_START = "GameBar.ACTION_START"
        const val ACTION_STOP = "GameBar.ACTION_STOP"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameBarOverlayComposable(
    appSettings: AppSettings,
    taskFpsMonitor: TaskFpsMonitor,
    expanded: Boolean,
    isDragging: Boolean,
    barX: Int,
    isFpsRecording: Boolean,
    isScreenRecording: Boolean,
    hasScreenRecorder: Boolean,
    onToggleExpand: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onTogglePanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSidebar: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onToggleScreenRecord: () -> Unit
) {
    val opacity = (appSettings.menuOpacity / 100f).coerceIn(0.15f, 1f)
    val showFps = !expanded && appSettings.showFps
    var fpsText by remember { mutableStateOf("0") }
    val df = remember {
        DecimalFormat("#").apply { roundingMode = RoundingMode.HALF_EVEN }
    }

    DisposableEffect(showFps, taskFpsMonitor) {
        val listener = TaskFpsMonitor.Listener { fps ->
            fpsText = df.format(fps)
        }
        if (showFps) {
            taskFpsMonitor.addListener(listener)
        }
        onDispose { taskFpsMonitor.removeListener(listener) }
    }

    val shape = when {
        expanded || isDragging -> RoundedCornerShape(24.dp)
        barX < 0 -> RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 20.dp, bottomEnd = 20.dp)
        else -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .padding(horizontal = if (expanded) 10.dp else 0.dp, vertical = 6.dp)
            .alpha(opacity)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            // Switcher / FPS Counter Handle
            val switcherIcon = when {
                expanded -> R.drawable.ic_close
                isDragging -> R.drawable.ic_drag
                barX > 0 -> R.drawable.ic_arrow_right
                else -> R.drawable.ic_arrow_left
            }
            val switcherTint = if (isFpsRecording && !expanded) {
                RecordingRed
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpand)
            ) {
                if (showFps) {
                    Text(
                        text = fpsText,
                        color = switcherTint,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        painter = painterResource(switcherIcon),
                        contentDescription = null,
                        tint = switcherTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GameBarActionButton(
                        iconRes = R.drawable.ic_panel,
                        onClick = onTogglePanel,
                        onLongClick = onOpenSettings
                    )
                    GameBarActionButton(
                        iconRes = R.drawable.ic_action_sidebar,
                        onClick = onOpenSidebar
                    )
                    GameBarActionButton(
                        iconRes = R.drawable.ic_action_screenshot,
                        onClick = onTakeScreenshot
                    )
                    if (hasScreenRecorder) {
                        GameBarActionButton(
                            iconRes = R.drawable.ic_action_record,
                            containerColor = if (isScreenRecording) {
                                RecordingRed
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (isScreenRecording) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            },
                            onClick = onToggleScreenRecord
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameBarActionButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
