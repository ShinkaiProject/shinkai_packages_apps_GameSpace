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

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.ui.components.OverlayLifecycleOwner
import com.android.gamespace.ui.components.createOverlayComposeView
import com.android.gamespace.ui.theme.GameSpaceTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.roundToInt

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
    private val windowManager: WindowManager = context.getSystemService(WindowManager::class.java)!!
    private val handler = Handler(Looper.getMainLooper())
    private val notificationStack = LinkedList<String>()

    private val overlayLifecycle = OverlayLifecycleOwner()
    private var currentTextState by mutableStateOf("")
    private var messageSequence by mutableIntStateOf(0)
    private var slideDistancePx by mutableIntStateOf(0)

    private val overlayRoot: ComposeView by lazy {
        createOverlayComposeView(context, overlayLifecycle) {
            GameSpaceTheme {
                val text = currentTextState
                val seq = messageSequence
                val travel = slideDistancePx.toFloat()

                val alphaAnim = remember { Animatable(0f) }
                val offsetYAnim = remember { Animatable(0f) }

                LaunchedEffect(seq, text) {
                    if (text.isEmpty()) return@LaunchedEffect
                    alphaAnim.snapTo(0f)
                    offsetYAnim.snapTo(0f)

                    // Appear
                    launch {
                        alphaAnim.animateTo(1f, tween(APPEAR_ANIMATION_DURATION.toInt()))
                    }
                    offsetYAnim.animateTo(travel / 2f, tween(APPEAR_ANIMATION_DURATION.toInt()))

                    delay(DISPLAY_NOTIFICATION_DURATION)

                    // Disappear
                    launch {
                        alphaAnim.animateTo(0f, tween(DISAPPEAR_ANIMATION_DURATION.toInt()))
                    }
                    offsetYAnim.animateTo(travel, tween(DISAPPEAR_ANIMATION_DURATION.toInt()))

                    onCurrentAnimationFinished()
                }

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    if (text.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .offset { IntOffset(0, offsetYAnim.value.roundToInt()) }
                                .alpha(alphaAnim.value)
                        ) {
                            Text(
                                text = text,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

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
    private var listenerRegistered = false
    private var destroyed = false

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_DANMAKU_NOTIFICATION_MODE) syncListener()
    }

    fun init() {
        destroyed = false
        overlayLifecycle.onCreate()
        overlayLifecycle.onResume()
        notificationListener = DanmakuServiceListener()
        notificationListener.danmakuServiceInterface = this
        updateParams()
        appSettings.registerOnChange(settingsListener)
        syncListener()
    }

    fun updateConfiguration(newConfig: Configuration) {
        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        updateParams()
        updateViewLayoutSafely()
    }

    fun destroy() {
        destroyed = true
        appSettings.unregisterOnChange(settingsListener)
        if (listenerRegistered) unregisterListener()
        dismissOverlay()
        overlayLifecycle.onDestroy()
    }

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

    private fun dismissOverlay() {
        notificationStack.clear()
        currentTextState = ""
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
        slideDistancePx = slideTravel()
        layoutParams.width =
            (NOTIFICATION_MAX_WIDTH * windowManager.currentWindowMetrics.bounds.width()) / 100
    }

    private fun getOffsetForPosition(): Int =
        if (isPortrait) verticalOffsetPortrait else verticalOffsetLandscape

    private fun windowTop(): Int =
        (getOffsetForPosition() * (1 - SLIDE_ANIMATION_DISTANCE_FACTOR)).toInt()

    private fun slideTravel(): Int =
        ceil(getOffsetForPosition() * 2 * SLIDE_ANIMATION_DISTANCE_FACTOR).toInt() + 1

    override fun showNotificationAsOverlay(danmakuText: String) {
        handler.post {
            if (overlayRoot.parent == null) {
                currentTextState = danmakuText
                messageSequence++
                windowManager.addView(overlayRoot, layoutParams)
            } else {
                notificationStack.add(danmakuText)
            }
        }
    }

    private fun onCurrentAnimationFinished() {
        if (notificationStack.isEmpty()) {
            currentTextState = ""
            removeViewSafely()
        } else {
            currentTextState = notificationStack.pop()
            messageSequence++
        }
    }

    override fun getApplabel(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            ""
        }
    }

    override val danmakuNotificationMode: Boolean
        get() = appSettings.danmakuNotification

    private fun updateViewLayoutSafely() {
        if (overlayRoot.parent != null) {
            windowManager.updateViewLayout(overlayRoot, layoutParams)
        }
    }

    private fun removeViewSafely() {
        if (overlayRoot.parent != null) {
            windowManager.removeViewImmediate(overlayRoot)
        }
    }

    companion object {
        private const val TAG = "DanmakuService"
        private const val SLIDE_ANIMATION_DISTANCE_FACTOR = 0.5f
        private const val APPEAR_ANIMATION_DURATION = 500L
        private const val DISPLAY_NOTIFICATION_DURATION = 2000L
        private const val DISAPPEAR_ANIMATION_DURATION = 300L
        private const val NOTIFICATION_MAX_WIDTH = 75
    }
}
