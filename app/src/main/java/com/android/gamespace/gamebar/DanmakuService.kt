/*
 * Copyright (C) 2020 The exTHmUI Open Source Project
 * Copyright (C) 2021 AOSP-Krypton Project
 * Copyright (C) 2022 Nameless-AOSP Project
 * Copyright (C) 2023 the risingOS android Project
 * Copyright (C) 2022-2024 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gamespace.gamebar

import android.animation.ValueAnimator
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.UserHandle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

import androidx.core.animation.addListener

import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped

import java.util.LinkedList

import kotlin.math.ceil

import javax.inject.Inject

import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.gamebar.DanmakuServiceListener
import com.android.gamespace.ui.overlay.ComposeOverlay
import com.android.gamespace.ui.overlay.DanmakuOverlay
import com.android.gamespace.ui.overlay.DanmakuState

interface DanmakuServiceInterface {
    val danmakuNotificationMode: Boolean
    fun showNotificationAsOverlay(danmakuText: String)
    fun getApplabel(packageName: String): String
}

@ServiceScoped
class DanmakuService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings
) : DanmakuServiceInterface {

    private lateinit var notificationListener: DanmakuServiceListener

    private val state = DanmakuState()

    /**
     * The window that is on the screen while there is something to show; null when there is not.
     * The window is only ever resized or moved when the screen is rotated: the slide in and out is
     * done by moving the text inside of it, which costs a redraw. Moving the window instead is a
     * call to the window manager for every single frame of the animation.
     */
    private var overlay: ComposeOverlay? = null

    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)!!

    private val handler = Handler(Looper.getMainLooper())

    private val notificationStack = LinkedList<String>()

    private var layoutParams: LayoutParams = LayoutParams().apply {
        height = LayoutParams.WRAP_CONTENT
        flags = flags or LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_NOT_TOUCHABLE or
                LayoutParams.FLAG_HARDWARE_ACCELERATED
        type = LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP
    }

    private var isPortrait: Boolean =
            context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private var verticalOffsetLandscape = 0
    private var verticalOffsetPortrait = 0

    private var overlayAlphaAnimator: ValueAnimator? = null
    private var overlayPositionAnimator: ValueAnimator? = null

    private val popRunnable = Runnable { popNotification() }

    private var listenerRegistered = false
    private var destroyed = false

    // The preferences hold their listeners weakly, so this has to stay referenced from here.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_DANMAKU_NOTIFICATION_MODE) syncListener()
    }

    fun init() {
        destroyed = false
        notificationListener = DanmakuServiceListener()
        notificationListener.danmakuServiceInterface = this
        updateParams()
        appSettings.registerOnChange(settingsListener)
        syncListener()
    }

    fun updateConfiguration(newConfig: Configuration) {
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        // end() on an animator that is not running would play it once more, and run its end
        // action (which schedules the next step) a second time.
        overlayAlphaAnimator?.takeIf { it.isRunning }?.end()
        overlayPositionAnimator?.takeIf { it.isRunning }?.end()
        updateParams()
        if (overlayPositionAnimator?.isRunning != true) {
            state.translationY = (getOffsetForPosition() - layoutParams.y).toFloat()
        }
        updateViewLayoutSafely()
    }

    fun destroy() {
        destroyed = true
        appSettings.unregisterOnChange(settingsListener)
        if (listenerRegistered) unregisterListener()
        dismissOverlay()
    }

    /**
     * Listens to the notifications only while the Danmaku setting is on. Without it every
     * notification that is posted during the game would still be sent to this process to be
     * looked at, only to be thrown away.
     */
    private fun syncListener() {
        if (destroyed) return
        if (appSettings.danmakuNotification) {
            if (!listenerRegistered) registerListener()
        } else if (listenerRegistered) {
            unregisterListener()
            dismissOverlay()
        }
    }

    private fun registerListener() {
        val componentName = ComponentName(context, DanmakuService::class.java)
        try {
            notificationListener.registerAsSystemService(
                context,
                componentName,
                UserHandle.USER_CURRENT
            )
            listenerRegistered = true
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while registering danmaku service")
        }
    }

    private fun unregisterListener() {
        listenerRegistered = false
        try {
            notificationListener.unregisterAsSystemService()
        } catch (e: RemoteException) {
            Log.e(TAG, "RemoteException while unregistering danmaku service")
        }
    }

    /** Stops whatever is on screen and what is still waiting to be shown. */
    private fun dismissOverlay() {
        handler.removeCallbacks(popRunnable)
        // The end action of an animator also runs when it is cancelled, and that would carry on
        // with the next notification.
        overlayAlphaAnimator?.apply {
            removeAllListeners()
            cancel()
        }
        overlayPositionAnimator?.apply {
            removeAllListeners()
            cancel()
        }
        notificationStack.clear()
        removeViewSafely()
    }

    private fun updateParams() {
        with(context.resources) {
            verticalOffsetLandscape =
                getDimensionPixelSize(R.dimen.notification_vertical_offset_landscape)
            verticalOffsetPortrait =
                getDimensionPixelSize(R.dimen.notification_vertical_offset_portrait)
        }
        layoutParams.y = windowTop()
        state.slideTravelPx = slideTravel()
        layoutParams.width = (NOTIFICATION_MAX_WIDTH * windowManager.currentWindowMetrics.bounds.width()) / 100
        state.textSizePx =
            (if (isPortrait) NOTIFICATION_SIZE_PORTRAIT else NOTIFICATION_SIZE_LANDSCAPE).toFloat()
    }

    /** Distance from the top of the screen of the notification while it is shown. */
    private fun getOffsetForPosition(): Int {
        return if (isPortrait) verticalOffsetPortrait else verticalOffsetLandscape
    }

    /** Where the window is: at the highest point of the slide. */
    private fun windowTop(): Int =
        (getOffsetForPosition() * (1 - SLIDE_ANIMATION_DISTANCE_FACTOR)).toInt()

    /** How far the text moves from the top of the window to the lowest point of the slide. */
    private fun slideTravel(): Int =
        ceil(getOffsetForPosition() * 2 * SLIDE_ANIMATION_DISTANCE_FACTOR).toInt() + 1

    private fun prepareNotification(text: String) {
        state.alpha = 0f
        state.translationY = 0f
        state.text = text
    }

    override fun showNotificationAsOverlay(notification: String) {
        if (overlay == null) {
            prepareNotification(notification)
            overlay = ComposeOverlay(context) { DanmakuOverlay(state) }.also {
                it.attach(windowManager, layoutParams)
            }
            pushNotification()
        } else {
            notificationStack.add(notification)
        }
    }
    
    override fun getApplabel(packageName: String): String {
        return packageName.let {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(it, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    ""
                }
            }
    }

    override val danmakuNotificationMode: Boolean
        get() = appSettings.danmakuNotification

    private fun pushNotification() {
        val end = getOffsetForPosition().toFloat()
        val start = end * (1 - SLIDE_ANIMATION_DISTANCE_FACTOR)
        overlayPositionAnimator = getPositionAnimator(APPEAR_ANIMATION_DURATION, start, end).also {
            it.addListener(onEnd = {
                handler.postDelayed(popRunnable, DISPLAY_NOTIFICATION_DURATION)
            })
            it.start()
        }
        startAlphaAnimation(APPEAR_ANIMATION_DURATION, 0f, 1f)
    }

    private fun popNotification() {
        val start = getOffsetForPosition().toFloat()
        val end = start * (1 + SLIDE_ANIMATION_DISTANCE_FACTOR)
        overlayPositionAnimator =
            getPositionAnimator(DISAPPEAR_ANIMATION_DURATION, start, end).also {
                it.addListener(onEnd = {
                    if (notificationStack.isEmpty()) {
                        removeViewSafely()
                    } else {
                        prepareNotification(notificationStack.pop())
                        pushNotification()
                    }
                })
                it.start()
            }
        startAlphaAnimation(DISAPPEAR_ANIMATION_DURATION, 1f, 0f)
    }

    /** [values] are distances from the top of the screen. */
    private fun getPositionAnimator(duration: Long, vararg values: Float): ValueAnimator {
        return ValueAnimator.ofFloat(*values).apply {
            this.duration = duration
            addUpdateListener {
                state.translationY = (it.animatedValue as Float) - layoutParams.y
            }
        }
    }

    private fun startAlphaAnimation(duration: Long, vararg values: Float) {
        overlayAlphaAnimator = ValueAnimator.ofFloat(*values).apply {
            this.duration = duration
            addUpdateListener {
                state.alpha = it.animatedValue as Float
            }
        }.also { it.start() }
    }

    private fun updateViewLayoutSafely() {
        overlay?.update(windowManager, layoutParams)
    }

    private fun removeViewSafely() {
        overlay?.detach(windowManager, immediate = true)
        overlay = null
    }

    companion object {
    
        private const val TAG = "DanmakuService"

        private const val SLIDE_ANIMATION_DISTANCE_FACTOR = 0.5f

        private const val APPEAR_ANIMATION_DURATION = 500L
        private const val DISPLAY_NOTIFICATION_DURATION = 2000L
        private const val DISAPPEAR_ANIMATION_DURATION = 300L

        private const val NOTIFICATION_SIZE_LANDSCAPE = 60
        private const val NOTIFICATION_SIZE_PORTRAIT = 60

        private const val NOTIFICATION_MAX_WIDTH = 75
    }
}
