package com.android.gamespace.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Calls [onStart], [onPause] and [onStop] as the screen starts, pauses and stops. */
@Composable
fun LifecycleEffect(onStart: () -> Unit = {}, onPause: () -> Unit = {}, onStop: () -> Unit = {}) {
    val owner = LocalLifecycleOwner.current
    val start by rememberUpdatedState(onStart)
    val pause by rememberUpdatedState(onPause)
    val stop by rememberUpdatedState(onStop)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
