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
import android.view.WindowManager
import androidx.compose.ui.unit.IntOffset
import androidx.core.app.NotificationCompat
import com.android.systemui.screenrecord.IRecordingCallback
import dagger.hilt.android.AndroidEntryPoint
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.settings.SettingsActivity
import com.android.gamespace.ui.overlay.ComposeOverlay
import com.android.gamespace.ui.overlay.EXPANDED_MARGIN_PX
import com.android.gamespace.ui.overlay.GameBar
import com.android.gamespace.ui.overlay.GameBarState
import com.android.gamespace.ui.overlay.GamePanel
import com.android.gamespace.ui.overlay.PANEL_FADE_MS
import com.android.gamespace.ui.overlay.PanelState
import com.android.gamespace.utils.*
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class GameBarService : Hilt_GameBarService() {

    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var screenUtils: ScreenUtils
    @Inject lateinit var danmakuService: DanmakuService
    @Inject lateinit var fpsRecorder: FpsRecorder

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

    /** What the bar shows; the windows below only draw it. */
    private val barState = GameBarState()

    // A window of Compose is used once: each time the bar or the panel comes up, a new one is made.
    private var barOverlay: ComposeOverlay? = null
    private var panelOverlay: ComposeOverlay? = null
    private var panelState: PanelState? = null

    /** Panels that are fading out, and are taken off the screen when they are done. */
    private val closingPanels = mutableListOf<ComposeOverlay>()
    private var panelOpen = false

    private val binder = GameBarBinder()
    private val autoCollapseRunnable = Runnable { barExpanded = false }
    private val firstPaint = Runnable { reveal() }
    private var shouldClose = false
    private var isGameStarting = false
    private var recorderWatched = false

    private val recordingListener = object : FpsRecorder.Listener {
        override fun onStateChanged(recording: Boolean) {
            barState.fpsRecording = recording
        }
    }

    private var barExpanded: Boolean
        get() = barState.expanded
        set(value) {
            barState.expanded = value
            barState.showFps = if (value) false else appSettings.showFps
            if (value) scheduleAutoCollapse() else handler.removeCallbacks(autoCollapseRunnable)
        }

    override fun onCreate() {
        super.onCreate()
        barState.opacity = appSettings.menuOpacity / 100f
        updateScreenMetrics()
        danmakuService.init()
        barState.fpsRecording = fpsRecorder.isRecording
        fpsRecorder.addListener(recordingListener)
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
        val channel = NotificationChannel(channelId, "Game Space Service", NotificationManager.IMPORTANCE_HIGH).apply {
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
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateScreenMetrics()
        if (!barState.revealed) {
            handler.removeCallbacks(firstPaint)
            handler.postDelayed({
                firstPaint.run()
                dockCollapsedMenu()
            }, 100)
        } else {
            dockCollapsedMenu()
        }
        danmakuService.updateConfiguration(newConfig)
    }

    fun onGameStart() {
        if (isGameStarting) return
        isGameStarting = true

        handler.post {
            if (barOverlay?.isAttached == true) {
                isGameStarting = false
                return@post
            }

            shouldClose = false
            barState.revealed = false
            barState.dragging = false
            barState.expanded = false
            // The bar is put in its place before it is put on the screen, and not touchable until it
            // is shown: it is there, but not to be seen yet.
            barLayoutParam.x = appSettings.x
            barLayoutParam.y = appSettings.y
            barLayoutParam.flags = barLayoutParam.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            dockCollapsedMenu()

            barOverlay = ComposeOverlay(this) {
                GameBar(
                    state = barState,
                    initialPosition = { IntOffset(barLayoutParam.x, barLayoutParam.y) },
                    onMove = ::onBarMove,
                    onMoveComplete = ::onBarMoveComplete,
                    onToggle = { barExpanded = !barExpanded },
                    onPanel = { if (panelOpen) closePanel() else openPanel() },
                    onPanelLongClick = {
                        startActivity(
                            Intent(this, SettingsActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onSidebar = ::openSidebar,
                    onScreenshot = ::takeShot,
                    onRecord = ::toggleRecording,
                )
            }.also { it.attach(wm, barLayoutParam) }
            handler.postDelayed(firstPaint, 500)
            startForegroundService()
            isGameStarting = false
        }
    }

    fun onGameLeave() {
        // The game is going away: finish and save any running FPS recording right now.
        fpsRecorder.stop(blocking = true)
        shouldClose = true
        handler.removeCallbacksAndMessages(null)

        panelOpen = false
        panelOverlay?.detach(wm, immediate = true)
        panelOverlay = null
        panelState = null
        closingPanels.forEach { it.detach(wm, immediate = true) }
        closingPanels.clear()

        barOverlay?.detach(wm, immediate = true)
        barOverlay = null
        barState.revealed = false
        barState.expanded = false

        stopForeground(true)
        stopSelf()
    }

    private fun updateLayout(update: WindowManager.LayoutParams.() -> Unit = {}) {
        barLayoutParam.update()
        barOverlay?.update(wm, barLayoutParam)
    }

    /** The first time the bar is shown, a moment after the window was put on the screen. */
    private fun reveal() {
        if (shouldClose || barOverlay?.isAttached != true) return
        barState.opacity = appSettings.menuOpacity / 100f
        barLayoutParam.flags = barLayoutParam.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        barExpanded = false
        barLayoutParam.x = appSettings.x
        barLayoutParam.y = appSettings.y
        barState.revealed = true
        dockCollapsedMenu()
        watchScreenRecorder()
    }

    private fun dockCollapsedMenu() {
        // x counts from the middle of the screen: the sign says which side the bar is on.
        barState.dockedEnd = barLayoutParam.x >= 0
        barLayoutParam.x = if (barLayoutParam.x < 0) -halfWidth else halfWidth
        barLayoutParam.y = barLayoutParam.y.coerceIn(safeArea, safeHeight)
        barState.showFps = if (barExpanded) false else appSettings.showFps
        barOverlay?.update(wm, barLayoutParam)
    }

    private fun onBarMove(x: Int, y: Int) {
        barState.dragging = true
        updateLayout {
            this.x = x
            this.y = y
        }
        if (barExpanded) scheduleAutoCollapse()
    }

    private fun onBarMoveComplete() {
        barState.dragging = false
        dockCollapsedMenu()
        appSettings.x = barLayoutParam.x
        appSettings.y = barLayoutParam.y
    }

    private fun scheduleAutoCollapse() {
        handler.removeCallbacks(autoCollapseRunnable)
        if (!appSettings.barAutoHideEnabled || panelOpen) return
        handler.postDelayed(autoCollapseRunnable, appSettings.barAutoHideDelaySec * 1000L)
    }

    private fun openPanel() {
        if (panelOpen) return
        panelOpen = true
        handler.removeCallbacks(autoCollapseRunnable)

        val state = PanelState()
        // What the bar takes of the side: itself and the gap to the edge on its outer side.
        val barWidth = ((barOverlay?.width ?: 0) - EXPANDED_MARGIN_PX).coerceAtLeast(0)
        val dockedEnd = barLayoutParam.x >= 0
        panelState = state
        panelOverlay = ComposeOverlay(this) {
            GamePanel(
                state = state,
                appSettings = appSettings,
                dockedEnd = dockedEnd,
                barWidthPx = barWidth,
                onDismiss = { closePanel() },
            )
        }.also { it.attach(wm, panelLayoutParam) }
    }

    private fun closePanel() {
        if (!panelOpen) return
        panelOpen = false
        val overlay = panelOverlay
        panelState?.closing = true
        panelOverlay = null
        panelState = null
        if (overlay != null) {
            closingPanels += overlay
            // After the fade, and a moment more.
            handler.postDelayed({
                closingPanels.remove(overlay)
                overlay.detach(wm)
            }, PANEL_FADE_MS + 50L)
        }
        if (barExpanded) scheduleAutoCollapse()
    }

    private fun takeShot() {
        val afterShot = {
            barExpanded = false
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

    private fun openSidebar() {
        val intent = Intent("com.android.gamespace.action.SHOW_SIDEBAR").apply {
            setPackage("com.libremobileos.sidebar")
        }
        sendBroadcastAsUser(intent, UserHandle.CURRENT, android.Manifest.permission.MANAGE_GAME_MODE)
        barExpanded = false
    }

    /** Shows the record button only if there is a recorder, and follows it. */
    private fun watchScreenRecorder() {
        val recorder = screenUtils.recorder
        barState.hasRecorder = recorder != null
        if (recorder == null || recorderWatched) return
        recorderWatched = true
        recorder.addRecordingCallback(object : IRecordingCallback.Stub() {
            override fun onRecordingStart() {
                handler.post { barState.screenRecording = true }
            }

            override fun onRecordingEnd() {
                handler.post { barState.screenRecording = false }
            }
        })
    }

    private fun toggleRecording() {
        val recorder = screenUtils.recorder ?: return
        if (recorder.isStarting) return
        if (!recorder.isRecording) recorder.startRecording() else recorder.stopRecording()
        barExpanded = false
    }

    companion object {
        const val TAG = "GameBar"
        const val ACTION_START = "GameBar.ACTION_START"
        const val ACTION_STOP = "GameBar.ACTION_STOP"
    }
}
