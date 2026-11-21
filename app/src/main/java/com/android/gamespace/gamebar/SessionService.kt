/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2022-2024 crDroid Android Project
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

import android.app.ActivityTaskManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.PowerManager
import android.os.UserHandle
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.GameSession
import com.android.gamespace.data.SystemSettings
import com.android.gamespace.data.UserGame
import com.android.gamespace.display.DisplayEnhancer
import com.android.gamespace.utils.GameModeUtils
import com.android.gamespace.utils.ScreenUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint(Service::class)
class SessionService : Hilt_SessionService() {
    @Inject
    lateinit var appSettings: AppSettings

    @Inject
    lateinit var settings: SystemSettings

    @Inject
    lateinit var session: GameSession

    @Inject
    lateinit var screenUtils: ScreenUtils

    @Inject
    lateinit var gameModeUtils: GameModeUtils

    @Inject
    lateinit var callListener: CallListener

    @Inject
    lateinit var displayEnhancer: DisplayEnhancer

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private var modeJob: Job? = null
    private var requestedPackage: String? = null
    private var activePackage: String? = null
    private var startId = 0
    private var pendingLeaves = 0
    private var barBound = false
    private var gameBar: GameBarService? = null
    private var screenBound = false

    private val gameBarConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            gameBar = (service as GameBarService.GameBarBinder).getService()
            updateGame()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            gameBar = null
            stopSelf()
        }
    }

    private var dndEnabledByUs = false
    private var previousDndFilter = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startId = startId
        requestedPackage = when (intent?.action) {
            START -> intent.getStringExtra(EXTRA_PACKAGE_NAME)
            STOP -> null
            null -> focusedGame()
            else -> requestedPackage
        }
        updateGame()
        return if (requestedPackage == null) START_NOT_STICKY else START_STICKY
    }

    private fun updateGame() {
        val next = requestedPackage
        if (next == activePackage && next != null) return
        endGame()
        if (next == null) {
            stopIfIdle()
            return
        }

        val bar = gameBar
        if (bar == null) {
            if (!barBound) {
                barBound = bindServiceAsUser(
                    Intent(this, GameBarService::class.java), gameBarConnection,
                    Context.BIND_AUTO_CREATE, UserHandle.CURRENT,
                )
                if (!barBound) {
                    requestedPackage = null
                    stopIfIdle()
                }
            }
            return
        }

        activePackage = next
        try {
            if (!screenBound) {
                screenUtils.bind()
                screenBound = true
            }
            session.unregister()
            session.register(next)
            applyGameModeConfig(next)
            applyAutoDnd()
            displayEnhancer.startSession()
            screenUtils.stayAwake = appSettings.stayAwake
            screenUtils.lockGesture = appSettings.lockGesture
            screenUtils.bypassCharge(true)
            callListener.init()
            bar.onGameStart()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting GameSpace session for $next", e)
            requestedPackage = null
            endGame()
            stopIfIdle()
        }
    }

    private fun endGame() {
        if (activePackage == null) return
        activePackage = null
        modeJob?.cancel()
        gameModeUtils.activeGame = null
        gameBar?.let { bar ->
            pendingLeaves++
            bar.onGameLeave {
                pendingLeaves--
                stopIfIdle()
            }
        }
        restoreAutoDnd()
        displayEnhancer.stopSession()
        session.unregister()
        if (screenBound) screenUtils.resetSession()
        callListener.destroy()
    }

    private fun stopIfIdle() {
        if (requestedPackage == null && pendingLeaves == 0) stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        endGame()
        if (barBound) unbindService(gameBarConnection)
        if (screenBound) screenUtils.unbind()
        scope.cancel()
        super.onDestroy()
    }

    private fun applyAutoDnd() {
        if (!appSettings.autoDnd) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val currentFilter = nm.currentInterruptionFilter
        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            currentFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            previousDndFilter = currentFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            dndEnabledByUs = true
        }
    }

    private fun restoreAutoDnd() {
        if (!dndEnabledByUs) return
        dndEnabledByUs = false
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.setInterruptionFilter(previousDndFilter)
    }

    private fun focusedGame(): String? {
        if (getSystemService(PowerManager::class.java)?.isInteractive != true ||
            getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return null
        val focused = ActivityTaskManager.getService()?.focusedRootTaskInfo?.topActivity?.packageName
        return focused?.takeIf { pkg -> settings.userGames.any { it.packageName == pkg } }
    }

    private fun applyGameModeConfig(app: String) {
        val game = settings.userGames.firstOrNull { it.packageName == app } ?: UserGame(app)
        gameModeUtils.activeGame = game
        modeJob = scope.launch {
            runCatching { gameModeUtils.applyGameModeFor(app, game.mode) }
                .onFailure { Log.e(TAG, "Error applying Game Mode for $app", it) }
        }
    }

    companion object {
        const val TAG = "SessionService"
        const val START = "game_start"
        const val STOP = "game_stop"
        const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, app: String) {
            val intent = Intent(context, SessionService::class.java)
                .setAction(START)
                .putExtra(EXTRA_PACKAGE_NAME, app)
            context.startServiceAsUser(intent, UserHandle.CURRENT)
        }

        fun stop(context: Context) {
            val intent = Intent(context, SessionService::class.java).setAction(STOP)
            context.startServiceAsUser(intent, UserHandle.CURRENT)
        }
    }
}
